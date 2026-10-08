package dev.monelytics;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(name = "monelytics.demo-enabled", havingValue = "true")
class DemoData implements CommandLineRunner {
  private final UserRepository users;
  private final AccountRepository accounts;
  private final TransactionRepository transactions;
  private final CategoryRepository categories;
  private final BudgetRepository budgets;
  private final RecurringRepository recurring;
  private final GoalRepository goals;
  private final GoalContributionRepository contributions;
  private final CategoryDefaults defaults;
  private final PasswordEncoder passwords;
  private final AuditService audit;
  private final String demoPassword, adminPassword;

  DemoData(
      UserRepository users,
      AccountRepository accounts,
      TransactionRepository transactions,
      CategoryRepository categories,
      BudgetRepository budgets,
      RecurringRepository recurring,
      GoalRepository goals,
      GoalContributionRepository contributions,
      CategoryDefaults defaults,
      PasswordEncoder passwords,
      AuditService audit,
      @Value("${monelytics.demo-password:Monelytics!2026}") String demoPassword,
      @Value("${monelytics.admin-password:AdminDemo!2026}") String adminPassword) {
    this.users = users;
    this.accounts = accounts;
    this.transactions = transactions;
    this.categories = categories;
    this.budgets = budgets;
    this.recurring = recurring;
    this.goals = goals;
    this.contributions = contributions;
    this.defaults = defaults;
    this.passwords = passwords;
    this.audit = audit;
    this.demoPassword = demoPassword;
    this.adminPassword = adminPassword;
  }

  @Override
  @Transactional
  public void run(String... args) {
    // Explicit opt-in; never replace existing users or their financial records.
    if (!users.existsByEmail("admin@monelytics.dev"))
      createUser("Morgan", "Reed", "admin@monelytics.dev", adminPassword, Role.ADMIN);
    if (users.existsByEmail("demo@monelytics.dev")) return;
    AppUser user = createUser("Alex", "Morgan", "demo@monelytics.dev", demoPassword, Role.USER);
    Map<String, FinanceCategory> category = new HashMap<>();
    categories
        .findByUserIdOrderByNameAsc(user.id)
        .forEach(value -> category.put(value.name, value));
    FinancialAccount checking = account(user, "Everyday checking", AccountType.CHECKING, "1750.00");
    FinancialAccount savings = account(user, "High-yield savings", AccountType.SAVINGS, "9800.00");
    FinancialAccount card =
        account(user, "Everyday credit card", AccountType.CREDIT_CARD, "-780.00");
    LocalDate today = LocalDate.now(ZoneOffset.UTC);
    for (int offset = 5; offset >= 0; offset--) {
      YearMonth month = YearMonth.from(today).minusMonths(offset);
      post(
          checking,
          TransactionType.INCOME,
          "5400.00",
          "Monthly salary",
          day(month, 1, today),
          category.get("Salary"),
          null);
      post(
          checking,
          TransactionType.EXPENSE,
          "1850.00",
          "Apartment rent",
          day(month, 1, today),
          category.get("Housing"),
          null);
      for (int week = 0; week < 4; week++)
        post(
            checking,
            TransactionType.EXPENSE,
            new BigDecimal("91.35").add(new BigDecimal(week * 3 + offset)).toPlainString(),
            "Weekly groceries",
            day(month, 2 + week * 7, today),
            category.get("Groceries"),
            null);
      post(
          checking,
          TransactionType.EXPENSE,
          new BigDecimal("160.50").add(new BigDecimal(offset * 7)).toPlainString(),
          "Restaurants and coffee",
          day(month, 6, today),
          category.get("Dining"),
          null);
      post(
          checking,
          TransactionType.EXPENSE,
          "82.75",
          "Transit pass",
          day(month, 3, today),
          category.get("Transport"),
          null);
      post(
          checking,
          TransactionType.EXPENSE,
          "15.99",
          "Music streaming",
          day(month, 5, today),
          category.get("Subscriptions"),
          null);
      if (offset > 0) {
        post(
            checking,
            TransactionType.EXPENSE,
            "126.50",
            "Electricity and internet",
            day(month, 11, today),
            category.get("Utilities"),
            null);
        post(
            checking,
            TransactionType.EXPENSE,
            "12.99",
            "Cloud storage",
            day(month, 13, today),
            category.get("Subscriptions"),
            null);
      }
      post(
          card,
          TransactionType.EXPENSE,
          offset == 0 ? "880.00" : Integer.toString(110 + offset * 12),
          offset == 0 ? "Laptop upgrade" : "Home and clothing",
          day(month, 7, today),
          category.get("Shopping"),
          null);
      post(
          checking,
          TransactionType.TRANSFER,
          "600.00",
          "Monthly savings transfer",
          day(month, 4, today),
          null,
          savings);
      post(
          checking,
          TransactionType.TRANSFER,
          "150.00",
          "Credit card payment",
          day(month, 6, today),
          null,
          card);
      budget(user, null, month, "3600.00");
      budget(user, category.get("Groceries"), month, "400.00");
      budget(user, category.get("Dining"), month, "220.00");
      budget(user, category.get("Shopping"), month, "400.00");
    }
    planned(
        user,
        checking,
        category.get("Utilities"),
        "Electricity and internet",
        RecurringKind.BILL,
        "126.50",
        today.plusDays(3));
    planned(
        user,
        checking,
        category.get("Subscriptions"),
        "Cloud storage",
        RecurringKind.SUBSCRIPTION,
        "12.99",
        today.plusDays(5));
    planned(
        user,
        checking,
        category.get("Subscriptions"),
        "Music streaming",
        RecurringKind.SUBSCRIPTION,
        "15.99",
        YearMonth.from(today).plusMonths(1).atDay(5));
    planned(
        user,
        checking,
        category.get("Housing"),
        "Apartment rent",
        RecurringKind.BILL,
        "1850.00",
        YearMonth.from(today).plusMonths(1).atDay(1));
    SavingsGoal emergency =
        goal(user, "Emergency fund", "12000.00", "7500.00", today.plusYears(1), "500.00");
    contribute(emergency, "500.00", today.minusMonths(1), "Monthly emergency allocation");
    contribute(emergency, "500.00", today, "This month's emergency allocation");
    goal(user, "Japan next spring", "5000.00", "3200.00", today.plusMonths(9), "250.00");
    goal(user, "First home deposit", "25000.00", "1500.00", today.plusYears(3), "400.00");
    audit.record(
        user.id, "DEMO_SEEDED", user.id, "Six months of categorized personal finance data created");
  }

  private LocalDate day(YearMonth month, int day, LocalDate today) {
    LocalDate value = month.atDay(Math.min(day, month.lengthOfMonth()));
    return value.isAfter(today) ? today : value;
  }

  private AppUser createUser(String first, String last, String email, String password, Role role) {
    if (password.length() < 12)
      throw new IllegalStateException("Demo passwords must have at least 12 characters");
    AuthService.checkPasswordBytes(password);
    AppUser user = new AppUser();
    user.firstName = first;
    user.lastName = last;
    user.email = email;
    user.role = role;
    user.passwordHash = passwords.encode(password);
    users.save(user);
    defaults.seed(user);
    return user;
  }

  private FinancialAccount account(AppUser user, String name, AccountType type, String opening) {
    FinancialAccount account = new FinancialAccount();
    account.user = user;
    account.name = name;
    account.type = type;
    account.openingBalance = new BigDecimal(opening);
    account.balance = account.openingBalance;
    account.createdAt = Instant.now().minus(Duration.ofDays(365));
    return accounts.save(account);
  }

  private void post(
      FinancialAccount account,
      TransactionType type,
      String amount,
      String description,
      LocalDate date,
      FinanceCategory category,
      FinancialAccount destination) {
    LedgerTransaction transaction = new LedgerTransaction();
    transaction.account = account;
    transaction.type = type;
    transaction.amount = new BigDecimal(amount).setScale(2);
    transaction.description = description;
    transaction.date = date;
    transaction.category = category;
    transaction.destinationAccount = destination;
    transactions.save(transaction);
    account.balance = account.balance.add(FinanceService.effect(type, transaction.amount));
    if (destination != null) destination.balance = destination.balance.add(transaction.amount);
  }

  private void budget(AppUser user, FinanceCategory category, YearMonth month, String limit) {
    MonthlyBudget budget = new MonthlyBudget();
    budget.user = user;
    budget.category = category;
    budget.month = month.toString();
    budget.scopeKey = category == null ? "ALL" : category.id.toString();
    budget.limitAmount = new BigDecimal(limit);
    budgets.save(budget);
  }

  private void planned(
      AppUser user,
      FinancialAccount account,
      FinanceCategory category,
      String name,
      RecurringKind kind,
      String amount,
      LocalDate due) {
    RecurringItem item = new RecurringItem();
    item.user = user;
    item.account = account;
    item.category = category;
    item.name = name;
    item.kind = kind;
    item.amount = new BigDecimal(amount);
    item.frequency = RecurringFrequency.MONTHLY;
    item.nextDueDate = due;
    item.anchorDay = due.getDayOfMonth();
    item.active = true;
    recurring.save(item);
  }

  private SavingsGoal goal(
      AppUser user, String name, String target, String current, LocalDate date, String monthly) {
    SavingsGoal goal = new SavingsGoal();
    goal.user = user;
    goal.name = name;
    goal.targetAmount = new BigDecimal(target);
    goal.currentAmount = new BigDecimal(current);
    goal.openingAmount = goal.currentAmount;
    goal.targetDate = date;
    goal.monthlyContribution = new BigDecimal(monthly);
    goal.expectedReturn = BigDecimal.ZERO;
    return goals.save(goal);
  }

  private void contribute(SavingsGoal goal, String amount, LocalDate date, String note) {
    GoalContribution contribution = new GoalContribution();
    contribution.goal = goal;
    contribution.amount = new BigDecimal(amount);
    contribution.date = date;
    contribution.note = note;
    contributions.save(contribution);
    goal.currentAmount = goal.currentAmount.add(contribution.amount);
  }
}
