package dev.monelytics;

import java.math.BigDecimal;
import java.time.*;
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
  private final GoalRepository goals;
  private final PasswordEncoder passwords;
  private final AuditService audit;
  private final String demoPassword, adminPassword;

  DemoData(
      UserRepository users,
      AccountRepository accounts,
      TransactionRepository transactions,
      GoalRepository goals,
      PasswordEncoder passwords,
      AuditService audit,
      @Value("${monelytics.demo-password}") String demoPassword,
      @Value("${monelytics.admin-password}") String adminPassword) {
    this.users = users;
    this.accounts = accounts;
    this.transactions = transactions;
    this.goals = goals;
    this.passwords = passwords;
    this.audit = audit;
    this.demoPassword = demoPassword;
    this.adminPassword = adminPassword;
  }

  @Override
  @Transactional
  public void run(String... args) {
    // Explicit opt-in makes repeatable demo data safe for local development; production disables
    // this runner.
    if (!users.existsByEmail("admin@monelytics.dev"))
      createUser("Morgan", "Reed", "admin@monelytics.dev", adminPassword, Role.ADMIN);
    if (users.existsByEmail("demo@monelytics.dev")) return;
    AppUser user = createUser("Alex", "Morgan", "demo@monelytics.dev", demoPassword, Role.USER);
    IraAccount roth = account(user, "Everyday Roth IRA", AccountType.ROTH_IRA, "8500.00");
    IraAccount traditional =
        account(user, "Future Traditional IRA", AccountType.TRADITIONAL_IRA, "5200.00");
    beneficiary(roth, "Jordan Morgan", "Spouse", "80");
    beneficiary(roth, "Taylor Morgan", "Child", "20");
    beneficiary(traditional, "Jordan Morgan", "Spouse", "100");
    LocalDate today = LocalDate.now(ZoneOffset.UTC);
    for (int i = 6; i >= 0; i--) {
      LocalDate date = YearMonth.from(today).minusMonths(i).atDay(1);
      post(roth, TransactionType.CONTRIBUTION, "350.00", "Monthly retirement contribution", date);
      post(traditional, TransactionType.CONTRIBUTION, "200.00", "Automatic IRA contribution", date);
      post(roth, TransactionType.RETURN, "60.04", "Monthly portfolio return", date);
      post(traditional, TransactionType.RETURN, "30.02", "Monthly portfolio return", date);
    }
    post(
        traditional,
        TransactionType.ROLLOVER,
        "6500.00",
        "Prior employer plan rollover",
        today.minusMonths(4).withDayOfMonth(1));
    goal(
        user,
        "A retirement on your terms",
        "750000.00",
        "24680.42",
        today.plusYears(30),
        "550.00",
        "6.00");
    goal(
        user, "See more of the world", "50000.00", "8000.00", today.plusYears(5), "200.00", "4.00");
    audit.record(user.id, "DEMO_SEEDED", user.id, "Demonstration portfolio created");
  }

  private AppUser createUser(String first, String last, String email, String password, Role role) {
    if (password.length() < 12)
      throw new IllegalStateException("Demo passwords must have at least 12 characters");
    AuthService.checkPasswordBytes(password);
    AppUser u = new AppUser();
    u.firstName = first;
    u.lastName = last;
    u.email = email;
    u.passwordHash = passwords.encode(password);
    u.role = role;
    return users.save(u);
  }

  private IraAccount account(AppUser user, String name, AccountType type, String balance) {
    IraAccount a = new IraAccount();
    a.user = user;
    a.name = name;
    a.type = type;
    a.openingBalance = new BigDecimal(balance);
    a.balance = a.openingBalance;
    a.createdAt = Instant.now().minus(Duration.ofDays(365));
    return accounts.save(a);
  }

  private void beneficiary(IraAccount a, String name, String relationship, String percentage) {
    Beneficiary b = new Beneficiary();
    b.account = a;
    b.name = name;
    b.relationship = relationship;
    b.percentage = new BigDecimal(percentage);
    a.beneficiaries.add(b);
  }

  private void post(
      IraAccount a, TransactionType type, String amount, String description, LocalDate date) {
    LedgerTransaction t = new LedgerTransaction();
    t.account = a;
    t.type = type;
    t.amount = new BigDecimal(amount);
    t.description = description;
    t.date = date;
    transactions.save(t);
    a.balance = a.balance.add(FinanceService.effect(type, t.amount));
  }

  private void goal(
      AppUser user,
      String name,
      String target,
      String current,
      LocalDate date,
      String monthly,
      String expectedReturn) {
    RetirementGoal g = new RetirementGoal();
    g.user = user;
    g.name = name;
    g.targetAmount = new BigDecimal(target);
    g.currentAmount = new BigDecimal(current);
    g.targetDate = date;
    g.monthlyContribution = new BigDecimal(monthly);
    g.expectedReturn = new BigDecimal(expectedReturn);
    goals.save(g);
  }
}
