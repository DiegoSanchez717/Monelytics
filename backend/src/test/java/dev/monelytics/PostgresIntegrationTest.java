package dev.monelytics;

import static dev.monelytics.ApiDtos.*;
import static dev.monelytics.FinanceDtos.*;
import static org.assertj.core.api.Assertions.*;

import java.math.BigDecimal;
import java.sql.*;
import java.time.*;
import java.util.UUID;
import java.util.concurrent.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;

@Tag("postgres")
@Testcontainers
@SpringBootTest(properties = "spring.session.jdbc.platform=postgresql")
@ActiveProfiles("test")
class PostgresIntegrationTest {
  @Container @ServiceConnection
  static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17.11-alpine");

  @Autowired AuthService auth;
  @Autowired FinanceService finance;
  @Autowired PlanningService planning;

  @Test
  void concurrentTransfersCannotOverdrawOrCreateMoney() throws Exception {
    AppUser user =
        auth.register(new Register("Transfer", "Tester", "transfer@test.dev", "TransferTest!2026"));
    AccountView source =
        finance.createAccount(
            user.id, new AccountCreate("Checking", AccountType.CHECKING, new BigDecimal("100.00")));
    AccountView first =
        finance.createAccount(
            user.id, new AccountCreate("Savings one", AccountType.SAVINGS, BigDecimal.ZERO));
    AccountView second =
        finance.createAccount(
            user.id, new AccountCreate("Savings two", AccountType.SAVINGS, BigDecimal.ZERO));
    CountDownLatch start = new CountDownLatch(1);
    try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
      Future<Boolean> a =
          executor.submit(() -> attemptTransfer(user.id, source.id(), first.id(), start));
      Future<Boolean> b =
          executor.submit(() -> attemptTransfer(user.id, source.id(), second.id(), start));
      start.countDown();
      assertThat(a.get(15, TimeUnit.SECONDS) ^ b.get(15, TimeUnit.SECONDS)).isTrue();
    }
    assertThat(
            finance.listAccounts(user.id).stream()
                .map(AccountView::balance)
                .reduce(BigDecimal.ZERO, BigDecimal::add))
        .isEqualByComparingTo("100.00");
    assertThat(
            finance.listAccounts(user.id).stream()
                .filter(a -> a.id().equals(source.id()))
                .findFirst()
                .orElseThrow()
                .balance())
        .isEqualByComparingTo("20.00");
  }

  private boolean attemptTransfer(UUID user, UUID source, UUID destination, CountDownLatch start)
      throws Exception {
    start.await();
    try {
      finance.createTransaction(
          user,
          new TransactionInput(
              source,
              TransactionType.TRANSFER,
              new BigDecimal("80.00"),
              "Concurrent transfer",
              LocalDate.now(ZoneOffset.UTC),
              null,
              destination));
      return true;
    } catch (ApiException expected) {
      return false;
    }
  }

  @Test
  void concurrentRecurringRetriesReturnTheSamePaymentAndDebitOnce() throws Exception {
    AppUser user =
        auth.register(
            new Register("Recurring", "Tester", "recurring@test.dev", "RecurringTest!2026"));
    AccountView account =
        finance.createAccount(
            user.id, new AccountCreate("Checking", AccountType.CHECKING, new BigDecimal("500.00")));
    CategoryView category =
        planning.createCategory(
            user.id, new CategoryInput("Internet", CategoryType.EXPENSE, "#48D1CC"));
    LocalDate today = LocalDate.now(ZoneOffset.UTC);
    RecurringView bill =
        planning.createRecurring(
            user.id,
            new RecurringInput(
                "Internet",
                RecurringKind.BILL,
                account.id(),
                category.id(),
                new BigDecimal("100.00"),
                RecurringFrequency.MONTHLY,
                today,
                true));
    CountDownLatch start = new CountDownLatch(1);
    try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
      Callable<TransactionView> pay =
          () -> {
            start.await();
            return planning.payRecurring(user.id, bill.id(), new RecurringPayment(today, today));
          };
      Future<TransactionView> a = executor.submit(pay), b = executor.submit(pay);
      start.countDown();
      assertThat(a.get(15, TimeUnit.SECONDS).id()).isEqualTo(b.get(15, TimeUnit.SECONDS).id());
    }
    assertThat(finance.listAccounts(user.id).getFirst().balance()).isEqualByComparingTo("400.00");
    assertThat(
            finance
                .listTransactions(user.id, null, null, null, null, null, 0, 10, "date,desc")
                .totalElements())
        .isEqualTo(1);
  }

  @Test
  void migrationPreservesAnExistingRetirementPortfolio() throws Exception {
    String schema = "legacy_portfolio";
    Flyway.configure()
        .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
        .schemas(schema)
        .defaultSchema(schema)
        .target("3")
        .load()
        .migrate();
    UUID user = UUID.randomUUID(),
        account = UUID.randomUUID(),
        transaction = UUID.randomUUID(),
        goal = UUID.randomUUID();
    try (Connection connection =
        DriverManager.getConnection(
            postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())) {
      connection.setSchema(schema);
      try (PreparedStatement insert =
          connection.prepareStatement(
              "INSERT INTO app_users(id,first_name,last_name,email,password_hash,role,created_at) VALUES(?,'Legacy','Owner','legacy@test.dev','unused','USER',CURRENT_TIMESTAMP)")) {
        insert.setObject(1, user);
        insert.executeUpdate();
      }
      try (PreparedStatement insert =
          connection.prepareStatement(
              "INSERT INTO ira_accounts(id,user_id,name,type,balance,opening_balance,created_at) VALUES(?,?,'Legacy IRA','ROTH_IRA',125.55,100,CURRENT_TIMESTAMP)")) {
        insert.setObject(1, account);
        insert.setObject(2, user);
        insert.executeUpdate();
      }
      try (PreparedStatement insert =
          connection.prepareStatement(
              "INSERT INTO ledger_transactions(id,account_id,type,amount,description,transaction_date,created_at) VALUES(?,?,'CONTRIBUTION',25.55,'Preserved',CURRENT_DATE,CURRENT_TIMESTAMP)")) {
        insert.setObject(1, transaction);
        insert.setObject(2, account);
        insert.executeUpdate();
      }
      try (PreparedStatement insert =
          connection.prepareStatement(
              "INSERT INTO retirement_goals(id,user_id,name,target_amount,current_amount,target_date,monthly_contribution,expected_return) VALUES(?,?,'Legacy goal',1000,100,CURRENT_DATE,0,0)")) {
        insert.setObject(1, goal);
        insert.setObject(2, user);
        insert.executeUpdate();
      }
    }
    Flyway.configure()
        .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
        .schemas(schema)
        .defaultSchema(schema)
        .load()
        .migrate();
    try (Connection connection =
        DriverManager.getConnection(
            postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())) {
      connection.setSchema(schema);
      try (Statement query = connection.createStatement();
          ResultSet value = query.executeQuery("SELECT balance FROM financial_accounts")) {
        assertThat(value.next()).isTrue();
        assertThat(value.getBigDecimal(1)).isEqualByComparingTo("125.55");
      }
      try (Statement query = connection.createStatement();
          ResultSet value =
              query.executeQuery("SELECT current_amount,opening_amount FROM savings_goals")) {
        assertThat(value.next()).isTrue();
        assertThat(value.getBigDecimal(1)).isEqualByComparingTo("100.00");
        assertThat(value.getBigDecimal(2)).isEqualByComparingTo("100.00");
      }
      try (Statement query = connection.createStatement();
          ResultSet value = query.executeQuery("SELECT count(*) FROM ledger_transactions")) {
        assertThat(value.next()).isTrue();
        assertThat(value.getInt(1)).isEqualTo(1);
      }
    }
  }

  @Test
  void migrationsAndLedgerExecuteAgainstRealPostgres() {
    AppUser user =
        auth.register(new Register("Postgres", "Tester", "postgres@test.dev", "PostgresTest!2026"));
    AccountView account =
        finance.createAccount(
            user.id,
            new AccountCreate("Postgres IRA", AccountType.ROTH_IRA, new BigDecimal("100.00")));
    finance.createTransaction(
        user.id,
        new TransactionInput(
            account.id(),
            TransactionType.CONTRIBUTION,
            new BigDecimal("25.55"),
            "Exact currency test",
            LocalDate.now(ZoneOffset.UTC)));
    assertThat(finance.listAccounts(user.id).getFirst().balance()).isEqualByComparingTo("125.55");
    assertThat(finance.dashboard(user.id).annualContributions()).isEqualByComparingTo("25.55");
  }

  @Test
  void concurrentContributionsCannotBypassSharedPolicy() throws Exception {
    AppUser user =
        auth.register(
            new Register("Concurrent", "Tester", "concurrent@test.dev", "ConcurrentTest!2026"));
    AccountView first =
        finance.createAccount(
            user.id, new AccountCreate("First IRA", AccountType.ROTH_IRA, BigDecimal.ZERO));
    AccountView second =
        finance.createAccount(
            user.id, new AccountCreate("Second IRA", AccountType.TRADITIONAL_IRA, BigDecimal.ZERO));
    CountDownLatch start = new CountDownLatch(1);
    try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
      Callable<Boolean> firstContribution = () -> attemptContribution(user, first, start);
      Callable<Boolean> secondContribution = () -> attemptContribution(user, second, start);
      Future<Boolean> firstResult = executor.submit(firstContribution);
      Future<Boolean> secondResult = executor.submit(secondContribution);
      start.countDown();
      assertThat(firstResult.get(20, TimeUnit.SECONDS))
          .isNotEqualTo(secondResult.get(20, TimeUnit.SECONDS));
    }
    assertThat(finance.dashboard(user.id).annualContributions()).isEqualByComparingTo("4500.00");
    assertThat(finance.dashboard(user.id).totalBalance()).isEqualByComparingTo("4500.00");
  }

  private boolean attemptContribution(AppUser user, AccountView account, CountDownLatch start)
      throws Exception {
    start.await();
    try {
      finance.createTransaction(
          user.id,
          new TransactionInput(
              account.id(),
              TransactionType.CONTRIBUTION,
              new BigDecimal("4500.00"),
              "Concurrent deposit",
              LocalDate.now(ZoneOffset.UTC)));
      return true;
    } catch (ApiException expected) {
      assertThat(expected.code).isEqualTo("BUSINESS_RULE");
      return false;
    }
  }
}
