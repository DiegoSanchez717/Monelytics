package dev.monelytics;

import static org.assertj.core.api.Assertions.*;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(
    properties = {
      "monelytics.demo-enabled=true",
      "monelytics.demo-password=Monelytics!2026",
      "monelytics.admin-password=AdminDemo!2026",
      "spring.datasource.url=jdbc:h2:mem:seeded;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE"
    })
@ActiveProfiles("test")
class DemoDataTest {
  @Autowired UserRepository users;
  @Autowired FinanceService finance;
  @Autowired PlanningService planning;
  @Autowired AnalyticsService analytics;

  @Test
  void optInSeedProvidesSixMonthsOfConsistentPersonalFinanceData() {
    AppUser user = users.findByEmail("demo@monelytics.dev").orElseThrow();
    var dashboard = finance.dashboard(user.id);
    assertThat(dashboard.accounts()).hasSize(3);
    assertThat(dashboard.goals()).hasSize(3);
    assertThat(planning.listCategories(user.id)).hasSize(10);
    assertThat(planning.listRecurring(user.id, null)).hasSize(4);
    assertThat(dashboard.spendingTrends())
        .hasSize(6)
        .allMatch(value -> value.income().compareTo(new BigDecimal("5400.00")) == 0);
    assertThat(
            dashboard.accounts().stream()
                .map(ApiDtos.AccountView::balance)
                .reduce(BigDecimal.ZERO, BigDecimal::add))
        .isEqualByComparingTo(dashboard.totalBalance());
    assertThat(dashboard.notifications())
        .anyMatch(value -> value.type().equals("UNUSUAL_SPENDING"));
    assertThat(dashboard.budgets()).hasSize(4);
    assertThat(
            finance
                .listTransactions(user.id, null, null, null, null, null, 0, 100, "date,desc")
                .totalElements())
        .isEqualTo(82);
    assertThat(users.findByEmail("admin@monelytics.dev").orElseThrow().role).isEqualTo(Role.ADMIN);
  }
}
