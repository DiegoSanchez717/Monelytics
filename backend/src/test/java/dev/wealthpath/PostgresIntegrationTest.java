package dev.wealthpath;

import static dev.wealthpath.ApiDtos.*;
import static org.assertj.core.api.Assertions.*;

import java.math.BigDecimal;
import java.time.*;
import java.util.concurrent.*;
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
