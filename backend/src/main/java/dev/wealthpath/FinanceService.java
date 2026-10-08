package dev.wealthpath;

import static dev.wealthpath.ApiDtos.*;

import jakarta.persistence.criteria.Predicate;
import java.math.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
class FinanceService {
  private final UserRepository users;
  private final AccountRepository accounts;
  private final TransactionRepository transactions;
  private final GoalRepository goals;
  private final AuditService audit;
  private final BigDecimal contributionLimit;

  FinanceService(
      UserRepository users,
      AccountRepository accounts,
      TransactionRepository transactions,
      GoalRepository goals,
      AuditService audit,
      @Value("${wealthpath.contribution-limit}") BigDecimal limit) {
    this.users = users;
    this.accounts = accounts;
    this.transactions = transactions;
    this.goals = goals;
    this.audit = audit;
    this.contributionLimit = limit;
    if (limit.signum() <= 0)
      throw new IllegalArgumentException("CONTRIBUTION_LIMIT must be positive");
  }

  @Transactional(readOnly = true)
  List<AccountView> listAccounts(UUID user) {
    Map<UUID, BigDecimal> contributions = accountContributionMap(user);
    return accounts.findByUserIdOrderByCreatedAtAsc(user).stream()
        .map(a -> view(a, contributions.getOrDefault(a.id, BigDecimal.ZERO)))
        .toList();
  }

  AccountView createAccount(UUID user, AccountCreate input) {
    AppUser owner = lockUser(user);
    IraAccount account = new IraAccount();
    account.user = owner;
    account.name = input.name().trim();
    account.type = input.type();
    account.openingBalance = input.openingBalance().setScale(2);
    account.balance = account.openingBalance;
    accounts.save(account);
    audit.record(user, "ACCOUNT_CREATED", account.id, "IRA account created");
    return view(account);
  }

  AccountView updateAccount(UUID user, UUID id, AccountUpdate input) {
    lockUser(user);
    IraAccount account = lockAccount(user, id);
    if (account.type != input.type() && transactions.existsByAccountId(id))
      throw ApiException.invalid("An account with recorded transactions cannot change IRA type.");
    account.name = input.name().trim();
    account.type = input.type();
    audit.record(user, "ACCOUNT_UPDATED", id, "IRA account updated");
    return view(account);
  }

  void deleteAccount(UUID user, UUID id) {
    lockUser(user);
    IraAccount account = lockAccount(user, id);
    if (account.balance.signum() != 0 || transactions.existsByAccountId(id))
      throw ApiException.invalid("Only an empty account without transactions can be deleted.");
    accounts.delete(account);
    audit.record(user, "ACCOUNT_DELETED", id, "Empty IRA account deleted");
  }

  AccountView beneficiaries(UUID user, UUID id, Beneficiaries input) {
    lockUser(user);
    IraAccount account = lockAccount(user, id);
    BigDecimal total =
        input.beneficiaries().stream()
            .map(BeneficiaryInput::percentage)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    if (!input.beneficiaries().isEmpty() && total.compareTo(new BigDecimal("100")) != 0)
      throw ApiException.invalid("Beneficiary allocations must total exactly 100%.");
    account.beneficiaries.clear();
    for (BeneficiaryInput item : input.beneficiaries()) {
      Beneficiary b = new Beneficiary();
      b.account = account;
      b.name = item.name().trim();
      b.relationship = item.relationship().trim();
      b.percentage = item.percentage();
      account.beneficiaries.add(b);
    }
    audit.record(user, "BENEFICIARIES_UPDATED", id, "Beneficiary allocations updated");
    return view(account);
  }

  @Transactional(readOnly = true)
  PageView<TransactionView> listTransactions(
      UUID user,
      String search,
      UUID accountId,
      TransactionType type,
      LocalDate from,
      LocalDate to,
      int page,
      int size,
      String sort) {
    if (from != null && to != null && from.isAfter(to))
      throw ApiException.invalid("The start date must be before the end date.");
    Specification<LedgerTransaction> spec =
        (root, query, cb) -> {
          List<Predicate> conditions = new ArrayList<>();
          conditions.add(cb.equal(root.get("account").get("user").get("id"), user));
          if (accountId != null) conditions.add(cb.equal(root.get("account").get("id"), accountId));
          if (type != null) conditions.add(cb.equal(root.get("type"), type));
          if (from != null) conditions.add(cb.greaterThanOrEqualTo(root.get("date"), from));
          if (to != null) conditions.add(cb.lessThanOrEqualTo(root.get("date"), to));
          if (search != null && !search.isBlank()) {
            String escaped =
                search
                    .trim()
                    .toLowerCase(Locale.ROOT)
                    .replace("\\", "\\\\")
                    .replace("%", "\\%")
                    .replace("_", "\\_");
            String pattern = "%" + escaped + "%";
            conditions.add(
                cb.or(
                    cb.like(cb.lower(root.get("description")), pattern, '\\'),
                    cb.like(cb.lower(root.get("account").get("name")), pattern, '\\')));
          }
          return cb.and(conditions.toArray(Predicate[]::new));
        };
    Page<LedgerTransaction> results =
        transactions.findAll(spec, PageRequest.of(page, size, transactionSort(sort)));
    return new PageView<>(
        results.getContent().stream().map(TransactionView::of).toList(),
        results.getTotalElements(),
        results.getTotalPages(),
        results.getNumber(),
        results.getSize());
  }

  TransactionView createTransaction(UUID user, TransactionInput input) {
    lockUser(user);
    IraAccount account = lockAccount(user, input.accountId());
    checkContribution(user, input, null);
    account.balance = checkedBalance(account.balance.add(effect(input.type(), input.amount())));
    LedgerTransaction t = new LedgerTransaction();
    t.account = account;
    assign(t, input);
    transactions.saveAndFlush(t);
    audit.record(user, "TRANSACTION_CREATED", t.id, "Ledger entry created");
    return TransactionView.of(t);
  }

  TransactionView updateTransaction(UUID user, UUID id, TransactionInput input) {
    lockUser(user);
    LedgerTransaction t = ownedTransaction(user, id);
    if (!t.account.getId().equals(input.accountId()))
      throw ApiException.invalid("A posted transaction cannot move between accounts.");
    IraAccount account = lockAccount(user, t.account.getId());
    checkContribution(user, input, t);
    // Reverse the original effect, then apply the replacement within the same locked database
    // transaction.
    account.balance =
        checkedBalance(
            account
                .balance
                .subtract(effect(t.type, t.amount))
                .add(effect(input.type(), input.amount())));
    assign(t, input);
    transactions.saveAndFlush(t);
    audit.record(user, "TRANSACTION_UPDATED", id, "Ledger entry corrected");
    return TransactionView.of(t);
  }

  void deleteTransaction(UUID user, UUID id) {
    lockUser(user);
    LedgerTransaction t = ownedTransaction(user, id);
    IraAccount account = lockAccount(user, t.account.getId());
    account.balance = checkedBalance(account.balance.subtract(effect(t.type, t.amount)));
    transactions.delete(t);
    audit.record(user, "TRANSACTION_DELETED", id, "Ledger entry removed");
  }

  private void checkContribution(UUID user, TransactionInput input, LedgerTransaction original) {
    if (input.type() != TransactionType.CONTRIBUTION) return;
    int year = input.date().getYear();
    BigDecimal already =
        transactions.contributions(user, LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31));
    if (original != null
        && original.type == TransactionType.CONTRIBUTION
        && original.date.getYear() == year) already = already.subtract(original.amount);
    // The configurable demo policy is shared across all owned IRAs; the user lock prevents
    // concurrent cap bypass.
    if (already.add(input.amount()).compareTo(contributionLimit) > 0)
      throw ApiException.invalid(
          "This entry exceeds the configured annual contribution policy of $"
              + contributionLimit.toPlainString()
              + " across your IRA accounts.");
  }

  @Transactional(readOnly = true)
  List<GoalView> listGoals(UUID user) {
    return goals.findByUserIdOrderByTargetDateAsc(user).stream().map(GoalView::of).toList();
  }

  GoalView createGoal(UUID user, GoalInput input) {
    RetirementGoal g = new RetirementGoal();
    g.user = lockUser(user);
    assign(g, input);
    goals.save(g);
    audit.record(user, "GOAL_CREATED", g.id, "Retirement goal created");
    return GoalView.of(g);
  }

  GoalView updateGoal(UUID user, UUID id, GoalInput input) {
    lockUser(user);
    RetirementGoal g = goals.findByIdAndUserId(id, user).orElseThrow(ApiException::missing);
    assign(g, input);
    audit.record(user, "GOAL_UPDATED", id, "Retirement goal updated");
    return GoalView.of(g);
  }

  void deleteGoal(UUID user, UUID id) {
    lockUser(user);
    RetirementGoal g = goals.findByIdAndUserId(id, user).orElseThrow(ApiException::missing);
    goals.delete(g);
    audit.record(user, "GOAL_DELETED", id, "Retirement goal deleted");
  }

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  Dashboard dashboard(UUID user) {
    LocalDate today = LocalDate.now(ZoneOffset.UTC);
    List<IraAccount> owned = accounts.findByUserIdOrderByCreatedAtAsc(user);
    Map<UUID, BigDecimal> perAccount = accountContributionMap(user);
    List<AccountView> accountViews =
        owned.stream().map(a -> view(a, perAccount.getOrDefault(a.id, BigDecimal.ZERO))).toList();
    BigDecimal total = owned.stream().map(a -> a.balance).reduce(BigDecimal.ZERO, BigDecimal::add);
    BigDecimal contributions =
        perAccount.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    List<RetirementGoal> savedGoals = goals.findByUserIdOrderByTargetDateAsc(user);
    BigDecimal target =
        savedGoals.stream().map(g -> g.targetAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    BigDecimal current =
        savedGoals.stream().map(g -> g.currentAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    BigDecimal progress =
        target.signum() == 0
            ? BigDecimal.ZERO
            : current
                .multiply(new BigDecimal("100"))
                .divide(target, 2, RoundingMode.HALF_UP)
                .min(new BigDecimal("100"));
    List<BalancePoint> history = new ArrayList<>();
    for (int offset = 5; offset >= 0; offset--) {
      YearMonth month = YearMonth.from(today).minusMonths(offset);
      LocalDate end = offset == 0 ? today : month.atEndOfMonth();
      BigDecimal balance = offset == 0 ? total : historicalBalance(user, owned, end);
      history.add(
          new BalancePoint(month.format(DateTimeFormatter.ofPattern("MMM", Locale.US)), balance));
    }
    BigDecimal prior = history.get(history.size() - 2).balance();
    BigDecimal change =
        prior.signum() == 0
            ? BigDecimal.ZERO
            : total
                .subtract(prior)
                .multiply(new BigDecimal("100"))
                .divide(prior, 2, RoundingMode.HALF_UP);
    List<TransactionView> recent =
        transactions
            .findByAccountUserId(
                user, PageRequest.of(0, 5, Sort.by(Sort.Direction.DESC, "date", "createdAt", "id")))
            .stream()
            .map(TransactionView::of)
            .toList();
    return new Dashboard(
        total,
        contributions,
        contributionLimit,
        progress,
        change,
        accountViews,
        recent,
        history,
        owned.stream().map(a -> new Allocation(a.name, a.balance)).toList());
  }

  private BigDecimal historicalBalance(UUID user, List<IraAccount> owned, LocalDate end) {
    Instant cutoff = end.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
    BigDecimal current =
        owned.stream()
            .filter(a -> a.createdAt.isBefore(cutoff))
            .map(a -> a.balance)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    if (owned.isEmpty()) return BigDecimal.ZERO;
    // Aggregate in SQL so dashboard memory usage stays bounded as a portfolio's ledger grows.
    return current
        .subtract(transactions.effectAfter(user, end, cutoff))
        .setScale(2, RoundingMode.HALF_UP);
  }

  private AccountView view(IraAccount a) {
    int year = LocalDate.now(ZoneOffset.UTC).getYear();
    BigDecimal contribution =
        transactions.accountContributions(
            a.id, LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31));
    return view(a, contribution);
  }

  private Map<UUID, BigDecimal> accountContributionMap(UUID user) {
    int year = LocalDate.now(ZoneOffset.UTC).getYear();
    Map<UUID, BigDecimal> totals = new HashMap<>();
    for (AccountContribution item :
        transactions.contributionsByAccount(
            user, LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31)))
      totals.put(item.getAccountId(), item.getTotal());
    return totals;
  }

  private AccountView view(IraAccount a, BigDecimal contribution) {
    return new AccountView(
        a.id,
        a.name,
        a.type,
        a.balance,
        contribution,
        a.beneficiaries.stream()
            .map(b -> new BeneficiaryView(b.id, b.name, b.relationship, b.percentage))
            .toList());
  }

  private AppUser lockUser(UUID id) {
    return users.lockById(id).orElseThrow(ApiException::missing);
  }

  private IraAccount lockAccount(UUID user, UUID id) {
    // A transaction's lazy account may already be represented by a proxy. Mutate its managed
    // target,
    // so field-access entity updates are tracked on the persisted object rather than the proxy
    // shell.
    return org.hibernate.Hibernate.unproxy(
        accounts.lockOwned(id, user).orElseThrow(ApiException::missing), IraAccount.class);
  }

  private LedgerTransaction ownedTransaction(UUID user, UUID id) {
    return transactions.findByIdAndAccountUserId(id, user).orElseThrow(ApiException::missing);
  }

  private BigDecimal checkedBalance(BigDecimal balance) {
    if (balance.signum() < 0)
      throw ApiException.invalid("This entry would make the account balance negative.");
    if (balance.compareTo(new BigDecimal("99999999999999999.99")) > 0)
      throw ApiException.invalid("The account balance exceeds the supported amount.");
    return balance.setScale(2, RoundingMode.UNNECESSARY);
  }

  static BigDecimal effect(TransactionType type, BigDecimal amount) {
    return type == TransactionType.WITHDRAWAL ? amount.negate() : amount;
  }

  private void assign(LedgerTransaction t, TransactionInput input) {
    t.type = input.type();
    t.amount = input.amount().setScale(2);
    t.description = input.description().trim();
    t.date = input.date();
  }

  private void assign(RetirementGoal g, GoalInput input) {
    g.name = input.name().trim();
    g.targetAmount = input.targetAmount();
    g.currentAmount = input.currentAmount();
    g.targetDate = input.targetDate();
    g.monthlyContribution = input.monthlyContribution();
    g.expectedReturn = input.expectedReturn();
  }

  private Sort transactionSort(String input) {
    String[] parts = input.split(",", -1);
    Map<String, String> columns =
        Map.of(
            "date",
            "date",
            "amount",
            "amount",
            "type",
            "type",
            "description",
            "description",
            "accountName",
            "account.name");
    if (parts.length != 2
        || !columns.containsKey(parts[0])
        || (!parts[1].equalsIgnoreCase("asc") && !parts[1].equalsIgnoreCase("desc")))
      throw ApiException.invalid(
          "Sort must use date, amount, type, description, or accountName and asc/desc.");
    return Sort.by(Sort.Direction.fromString(parts[1]), columns.get(parts[0]))
        .and(Sort.by(Sort.Direction.DESC, "createdAt", "id"));
  }
}
