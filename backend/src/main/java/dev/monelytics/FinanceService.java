package dev.monelytics;

import static dev.monelytics.ApiDtos.*;

import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import java.io.*;
import java.math.*;
import java.nio.charset.StandardCharsets;
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
  private final CategoryRepository categories;
  private final RecurringRepository recurring;
  private final GoalContributionRepository goalContributions;
  private final AnalyticsService analytics;
  private final EntityManager entityManager;
  private final AuditService audit;
  private final BigDecimal contributionLimit;

  FinanceService(
      UserRepository users,
      AccountRepository accounts,
      TransactionRepository transactions,
      GoalRepository goals,
      CategoryRepository categories,
      RecurringRepository recurring,
      GoalContributionRepository goalContributions,
      AnalyticsService analytics,
      EntityManager entityManager,
      AuditService audit,
      @Value("${monelytics.contribution-limit}") BigDecimal limit) {
    this.users = users;
    this.accounts = accounts;
    this.transactions = transactions;
    this.goals = goals;
    this.categories = categories;
    this.recurring = recurring;
    this.goalContributions = goalContributions;
    this.analytics = analytics;
    this.entityManager = entityManager;
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
    FinancialAccount account = new FinancialAccount();
    account.user = owner;
    account.name = input.name().trim();
    account.type = input.type();
    account.openingBalance = input.openingBalance().setScale(2);
    account.balance = checkedBalance(account, account.openingBalance);
    accounts.save(account);
    audit.record(user, "ACCOUNT_CREATED", account.id, "Financial account created");
    return view(account);
  }

  AccountView updateAccount(UUID user, UUID id, AccountUpdate input) {
    lockUser(user);
    FinancialAccount account = lockAccount(user, id);
    if (account.type != input.type()
        && transactions.existsByAccountIdOrDestinationAccountId(id, id))
      throw ApiException.invalid("An account with recorded transactions cannot change type.");
    if (!isIra(input.type()) && !account.beneficiaries.isEmpty())
      throw ApiException.invalid(
          "Clear IRA beneficiary allocations before changing to a non-IRA account type.");
    account.name = input.name().trim();
    account.type = input.type();
    checkedBalance(account, account.balance);
    audit.record(user, "ACCOUNT_UPDATED", id, "Financial account updated");
    return view(account);
  }

  void deleteAccount(UUID user, UUID id) {
    lockUser(user);
    FinancialAccount account = lockAccount(user, id);
    if (account.balance.signum() != 0
        || transactions.existsByAccountIdOrDestinationAccountId(id, id)
        || recurring.existsByAccountId(id))
      throw ApiException.invalid("Only an empty account without transactions can be deleted.");
    accounts.delete(account);
    audit.record(user, "ACCOUNT_DELETED", id, "Empty financial account deleted");
  }

  AccountView beneficiaries(UUID user, UUID id, Beneficiaries input) {
    lockUser(user);
    FinancialAccount account = lockAccount(user, id);
    if (!input.beneficiaries().isEmpty() && !isIra(account.type))
      throw ApiException.invalid("Beneficiary allocations are available for IRA accounts.");
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
    return listTransactions(user, search, accountId, type, null, from, to, page, size, sort);
  }

  @Transactional(readOnly = true)
  PageView<TransactionView> listTransactions(
      UUID user,
      String search,
      UUID accountId,
      TransactionType type,
      UUID categoryId,
      LocalDate from,
      LocalDate to,
      int page,
      int size,
      String sort) {
    Page<LedgerTransaction> results =
        transactions.findAll(
            transactionFilter(user, search, accountId, type, categoryId, from, to),
            PageRequest.of(page, size, transactionSort(sort)));
    return new PageView<>(
        results.getContent().stream().map(TransactionView::of).toList(),
        results.getTotalElements(),
        results.getTotalPages(),
        results.getNumber(),
        results.getSize());
  }

  private Specification<LedgerTransaction> transactionFilter(
      UUID user,
      String search,
      UUID accountId,
      TransactionType type,
      UUID categoryId,
      LocalDate from,
      LocalDate to) {
    if (from != null && to != null && from.isAfter(to))
      throw ApiException.invalid("The start date must be before the end date.");
    Specification<LedgerTransaction> spec =
        (root, query, cb) -> {
          List<Predicate> conditions = new ArrayList<>();
          conditions.add(cb.equal(root.get("account").get("user").get("id"), user));
          if (accountId != null)
            conditions.add(
                cb.or(
                    cb.equal(root.get("account").get("id"), accountId),
                    cb.equal(root.get("destinationAccount").get("id"), accountId)));
          if (type != null) conditions.add(cb.equal(root.get("type"), type));
          if (categoryId != null)
            conditions.add(cb.equal(root.get("category").get("id"), categoryId));
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
                    cb.like(cb.lower(root.get("account").get("name")), pattern, '\\'),
                    cb.like(
                        cb.lower(root.join("category", JoinType.LEFT).get("name")), pattern, '\\'),
                    cb.like(
                        cb.lower(root.join("destinationAccount", JoinType.LEFT).get("name")),
                        pattern,
                        '\\')));
          }
          return cb.and(conditions.toArray(Predicate[]::new));
        };
    return spec;
  }

  TransactionView createTransaction(UUID user, TransactionInput input) {
    lockUser(user);
    FinancialAccount account = lockAccount(user, input.accountId());
    validateTransaction(account, input);
    checkContribution(user, input, null);
    LedgerTransaction t = new LedgerTransaction();
    t.account = account;
    assign(user, t, input);
    applyLedgerChange(user, null, t);
    transactions.saveAndFlush(t);
    audit.record(user, "TRANSACTION_CREATED", t.id, "Ledger entry created");
    return TransactionView.of(t);
  }

  TransactionView updateTransaction(UUID user, UUID id, TransactionInput input) {
    lockUser(user);
    LedgerTransaction t = ownedTransaction(user, id);
    if (!t.account.getId().equals(input.accountId()))
      throw ApiException.invalid("A posted transaction cannot move between accounts.");
    FinancialAccount account = lockAccount(user, t.account.getId());
    validateTransaction(account, input);
    if (t.recurring != null && input.type() != TransactionType.EXPENSE)
      throw ApiException.invalid("A recurring payment must remain an expense.");
    checkContribution(user, input, t);
    // Reverse the original effect, then apply the replacement within the same locked database
    // transaction.
    LedgerTransaction replacement = new LedgerTransaction();
    replacement.account = account;
    assign(user, replacement, input);
    applyLedgerChange(user, t, replacement);
    assign(user, t, input);
    transactions.saveAndFlush(t);
    audit.record(user, "TRANSACTION_UPDATED", id, "Ledger entry corrected");
    return TransactionView.of(t);
  }

  void deleteTransaction(UUID user, UUID id) {
    lockUser(user);
    LedgerTransaction t = ownedTransaction(user, id);
    applyLedgerChange(user, t, null);
    if (t.recurring != null) {
      RecurringItem item = org.hibernate.Hibernate.unproxy(t.recurring, RecurringItem.class);
      if (t.recurringDueDate.isBefore(item.nextDueDate)) item.nextDueDate = t.recurringDueDate;
    }
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
    SavingsGoal g = new SavingsGoal();
    g.user = lockUser(user);
    assign(g, input);
    g.openingAmount = g.currentAmount;
    goals.save(g);
    audit.record(user, "GOAL_CREATED", g.id, "Savings goal created");
    return GoalView.of(g);
  }

  GoalView updateGoal(UUID user, UUID id, GoalInput input) {
    lockUser(user);
    SavingsGoal g = goals.findByIdAndUserId(id, user).orElseThrow(ApiException::missing);
    BigDecimal opening = input.currentAmount().subtract(goalContributions.contributed(id));
    if (opening.signum() < 0)
      throw ApiException.invalid(
          "Current savings cannot be less than recorded goal contributions. Remove a contribution first.");
    assign(g, input);
    g.openingAmount = opening;
    audit.record(user, "GOAL_UPDATED", id, "Savings goal updated");
    return GoalView.of(g);
  }

  void deleteGoal(UUID user, UUID id) {
    lockUser(user);
    SavingsGoal g = goals.findByIdAndUserId(id, user).orElseThrow(ApiException::missing);
    goals.delete(g);
    audit.record(user, "GOAL_DELETED", id, "Savings goal deleted");
  }

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  Dashboard dashboard(UUID user) {
    return dashboard(user, null);
  }

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  Dashboard dashboard(UUID user, String requestedMonth) {
    LocalDate today = LocalDate.now(ZoneOffset.UTC);
    YearMonth selected = AnalyticsService.month(requestedMonth);
    FinanceDtos.AnalyticsView summary = analytics.analytics(user, selected.toString());
    List<FinancialAccount> owned = accounts.findByUserIdOrderByCreatedAtAsc(user);
    Map<UUID, BigDecimal> perAccount = accountContributionMap(user);
    List<AccountView> accountViews =
        owned.stream().map(a -> view(a, perAccount.getOrDefault(a.id, BigDecimal.ZERO))).toList();
    BigDecimal total = owned.stream().map(a -> a.balance).reduce(BigDecimal.ZERO, BigDecimal::add);
    BigDecimal contributions =
        perAccount.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    List<SavingsGoal> savedGoals = goals.findByUserIdOrderByTargetDateAsc(user);
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
      YearMonth month = selected.minusMonths(offset);
      LocalDate end = month.equals(YearMonth.from(today)) ? today : month.atEndOfMonth();
      BigDecimal balance =
          month.equals(YearMonth.from(today)) ? total : historicalBalance(user, owned, end);
      history.add(
          new BalancePoint(month.format(DateTimeFormatter.ofPattern("MMM", Locale.US)), balance));
    }
    BigDecimal prior = history.get(history.size() - 2).balance();
    BigDecimal change =
        prior.signum() == 0
            ? BigDecimal.ZERO
            : history
                .getLast()
                .balance()
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
        owned.stream()
            .filter(a -> a.balance.signum() > 0)
            .map(a -> new Allocation(a.name, a.balance))
            .toList(),
        selected.toString(),
        summary.income(),
        summary.expenses(),
        summary.cashFlow(),
        summary.savingsRate(),
        analytics.budgets(user, selected.toString()),
        savedGoals.stream().map(GoalView::of).toList(),
        summary.spendingTrends(),
        summary.categoryBreakdown(),
        analytics.notifications(user, selected.toString()));
  }

  private BigDecimal historicalBalance(UUID user, List<FinancialAccount> owned, LocalDate end) {
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
        .subtract(transactions.destinationEffectAfter(user, end, cutoff))
        .setScale(2, RoundingMode.HALF_UP);
  }

  private AccountView view(FinancialAccount a) {
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

  private AccountView view(FinancialAccount a, BigDecimal contribution) {
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

  private FinancialAccount lockAccount(UUID user, UUID id) {
    // A transaction's lazy account may already be represented by a proxy. Mutate its managed
    // target,
    // so field-access entity updates are tracked on the persisted object rather than the proxy
    // shell.
    return org.hibernate.Hibernate.unproxy(
        accounts.lockOwned(id, user).orElseThrow(ApiException::missing), FinancialAccount.class);
  }

  private LedgerTransaction ownedTransaction(UUID user, UUID id) {
    return transactions.findByIdAndAccountUserId(id, user).orElseThrow(ApiException::missing);
  }

  private BigDecimal checkedBalance(FinancialAccount account, BigDecimal balance) {
    if (balance.signum() < 0 && account.type != AccountType.CREDIT_CARD)
      throw ApiException.invalid("This entry would make the account balance negative.");
    if (balance.abs().compareTo(new BigDecimal("99999999999999999.99")) > 0)
      throw ApiException.invalid("The account balance exceeds the supported amount.");
    return balance.setScale(2, RoundingMode.UNNECESSARY);
  }

  static BigDecimal effect(TransactionType type, BigDecimal amount) {
    return type == TransactionType.WITHDRAWAL
            || type == TransactionType.EXPENSE
            || type == TransactionType.TRANSFER
        ? amount.negate()
        : amount;
  }

  private void assign(UUID user, LedgerTransaction t, TransactionInput input) {
    t.type = input.type();
    t.amount = input.amount().setScale(2);
    t.description = input.description().trim();
    t.date = input.date();
    t.category = input.categoryId() == null ? null : ownedCategory(user, input.categoryId());
    if (t.category != null
        && (input.type() != TransactionType.INCOME && input.type() != TransactionType.EXPENSE))
      throw ApiException.invalid("Categories apply to income and expense entries.");
    if (t.category != null && !t.category.type.name().equals(input.type().name()))
      throw ApiException.invalid("Choose a category matching the transaction type.");
    t.destinationAccount =
        input.destinationAccountId() == null
            ? null
            : lockAccount(user, input.destinationAccountId());
    if ((input.type() == TransactionType.TRANSFER) != (t.destinationAccount != null))
      throw ApiException.invalid(
          "A transfer requires a destination account; other entries cannot have one.");
    if (t.destinationAccount != null && t.destinationAccount.id.equals(t.account.getId()))
      throw ApiException.invalid("Transfer accounts must be different.");
  }

  private FinanceCategory ownedCategory(UUID user, UUID id) {
    return org.hibernate.Hibernate.unproxy(
        categories.findByIdAndUserId(id, user).orElseThrow(ApiException::missing),
        FinanceCategory.class);
  }

  private void validateTransaction(FinancialAccount account, TransactionInput input) {
    if (input.type() == TransactionType.CONTRIBUTION && !isIra(account.type))
      throw ApiException.invalid(
          "Use income or transfers for a checking, savings, or credit card account.");
  }

  private static boolean isIra(AccountType type) {
    return type == AccountType.ROTH_IRA || type == AccountType.TRADITIONAL_IRA;
  }

  private void applyLedgerChange(
      UUID user, LedgerTransaction original, LedgerTransaction replacement) {
    Map<UUID, BigDecimal> deltas = new HashMap<>();
    addEffects(deltas, original, BigDecimal.ONE.negate());
    addEffects(deltas, replacement, BigDecimal.ONE);
    // User-level locking serializes cross-account operations. Check final balances before changing
    // either side, so transfers and corrections are atomic and do not create or lose money.
    Map<FinancialAccount, BigDecimal> finalBalances = new LinkedHashMap<>();
    deltas.keySet().stream()
        .sorted()
        .forEach(
            id -> {
              FinancialAccount account = lockAccount(user, id);
              finalBalances.put(
                  account, checkedBalance(account, account.balance.add(deltas.get(id))));
            });
    finalBalances.forEach((account, balance) -> account.balance = balance);
  }

  private void addEffects(Map<UUID, BigDecimal> deltas, LedgerTransaction item, BigDecimal factor) {
    if (item == null) return;
    deltas.merge(
        item.account.getId(), effect(item.type, item.amount).multiply(factor), BigDecimal::add);
    if (item.destinationAccount != null)
      deltas.merge(item.destinationAccount.getId(), item.amount.multiply(factor), BigDecimal::add);
  }

  @Transactional(isolation = Isolation.REPEATABLE_READ)
  void exportCsv(
      UUID user,
      String search,
      UUID accountId,
      TransactionType type,
      UUID categoryId,
      LocalDate from,
      LocalDate to,
      String sort,
      OutputStream output)
      throws IOException {
    Specification<LedgerTransaction> filter =
        transactionFilter(user, search, accountId, type, categoryId, from, to);
    Sort ordering = transactionSort(sort);
    Writer writer = new BufferedWriter(new OutputStreamWriter(output, StandardCharsets.UTF_8));
    writer.write('\uFEFF');
    writer.write("Date,Account,Type,Category,Destination,Amount,Description\r\n");
    int page = 0;
    Page<LedgerTransaction> batch;
    do {
      batch = transactions.findAll(filter, PageRequest.of(page++, 500, ordering));
      for (LedgerTransaction item : batch) {
        TransactionView value = TransactionView.of(item);
        String[] cells = {
          value.date().toString(),
          value.accountName(),
          value.type().name(),
          value.categoryName(),
          value.destinationAccountName(),
          value.amount().toPlainString(),
          value.description()
        };
        for (int i = 0; i < cells.length; i++) {
          if (i > 0) writer.write(',');
          writer.write(csvCell(cells[i]));
        }
        writer.write("\r\n");
      }
      writer.flush();
      // Release managed rows between pages as well as Java DTOs, keeping large exports bounded.
      entityManager.clear();
    } while (batch.hasNext());
    audit.record(user, "TRANSACTIONS_EXPORTED", user, "Filtered transaction CSV exported");
  }

  void validateExport(
      UUID user,
      String search,
      UUID accountId,
      TransactionType type,
      UUID categoryId,
      LocalDate from,
      LocalDate to,
      String sort) {
    transactionFilter(user, search, accountId, type, categoryId, from, to);
    transactionSort(sort);
  }

  static String csvCell(String value) {
    String text = Objects.toString(value, "");
    String leading = text.stripLeading();
    // Quote delimiters and neutralize spreadsheet formulas, including whitespace-prefixed input.
    if (!leading.isEmpty() && "=+-@".indexOf(leading.charAt(0)) >= 0
        || text.startsWith("\t")
        || text.startsWith("\r")) text = "'" + text;
    return "\"" + text.replace("\"", "\"\"") + "\"";
  }

  private void assign(SavingsGoal g, GoalInput input) {
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
