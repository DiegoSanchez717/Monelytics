package dev.monelytics;

import static dev.monelytics.ApiDtos.*;
import static dev.monelytics.ApiTestSupport.realCsrf;
import static dev.monelytics.FinanceDtos.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FinanceIntegrationTest {
  @Autowired AuthService auth;
  @Autowired FinanceService finance;
  @Autowired PlanningService planning;
  @Autowired AnalyticsService analytics;
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  private final LocalDate today = LocalDate.now(ZoneOffset.UTC);

  @Test
  void registrationStartsWithUsableCategoriesAndNoInventedFinanceData() {
    UUID owner = owner();
    assertThat(planning.listCategories(owner))
        .hasSize(10)
        .anyMatch(value -> value.name().equals("Salary"));
    assertThat(finance.listAccounts(owner)).isEmpty();
    assertThat(analytics.budgets(owner, null)).isEmpty();
  }

  @Test
  void iraConversionRequiresExplicitlyClearingBeneficiaries() throws Exception {
    UUID owner = owner();
    AccountView ira = account(owner, AccountType.ROTH_IRA, "100.00");
    finance.beneficiaries(
        owner,
        ira.id(),
        new Beneficiaries(
            List.of(new BeneficiaryInput("Alex Tester", "Spouse", new BigDecimal("100")))));
    mvc.perform(
            put("/api/accounts/" + ira.id())
                .with(user(owner.toString()).roles("USER"))
                .with(realCsrf(mvc, json))
                .contentType("application/json")
                .content("{\"name\":\"Converted checking\",\"type\":\"CHECKING\"}"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value("BUSINESS_RULE"));
    AccountView unchanged = finance.listAccounts(owner).getFirst();
    assertThat(unchanged.type()).isEqualTo(AccountType.ROTH_IRA);
    assertThat(unchanged.name()).isEqualTo(ira.name());
    assertThat(unchanged.beneficiaries()).hasSize(1);

    AccountView traditional =
        finance.updateAccount(
            owner, ira.id(), new AccountUpdate("Traditional IRA", AccountType.TRADITIONAL_IRA));
    assertThat(traditional.beneficiaries()).hasSize(1);
    finance.beneficiaries(owner, ira.id(), new Beneficiaries(List.of()));
    AccountView savings =
        finance.updateAccount(
            owner, ira.id(), new AccountUpdate("Converted savings", AccountType.SAVINGS));
    assertThat(savings.type()).isEqualTo(AccountType.SAVINGS);
    assertThat(savings.beneficiaries()).isEmpty();
    assertThat(savings.balance()).isEqualByComparingTo("100.00");
  }

  @Test
  void transferCorrectionsUpdateBothSidesAndCannotReverseConsumedFunds() {
    UUID owner = owner();
    AccountView checking = account(owner, AccountType.CHECKING, "1000.00"),
        savings = account(owner, AccountType.SAVINGS, "0.00");
    TransactionView transfer =
        finance.createTransaction(
            owner, input(checking, TransactionType.TRANSFER, "250.00", null, savings.id()));
    assertBalance(owner, checking.id(), "750.00");
    assertBalance(owner, savings.id(), "250.00");
    assertThat(
            finance
                .listTransactions(owner, null, savings.id(), null, today, today, 0, 10, "date,desc")
                .totalElements())
        .isEqualTo(1);
    finance.updateTransaction(
        owner,
        transfer.id(),
        input(checking, TransactionType.TRANSFER, "300.00", null, savings.id()));
    assertBalance(owner, checking.id(), "700.00");
    assertBalance(owner, savings.id(), "300.00");
    finance.createTransaction(owner, input(savings, TransactionType.EXPENSE, "200.00", null, null));
    assertThatThrownBy(() -> finance.deleteTransaction(owner, transfer.id()))
        .isInstanceOf(ApiException.class);
    assertBalance(owner, checking.id(), "700.00");
    assertBalance(owner, savings.id(), "100.00");
    assertThat(analytics.analytics(owner, null).income()).isEqualByComparingTo("0.00");
    assertThat(analytics.analytics(owner, null).expenses()).isEqualByComparingTo("200.00");
  }

  @Test
  void transferOwnershipAndCategoryTypeAreValidatedBeforeAnyBalanceChange() {
    UUID owner = owner(), other = owner();
    AccountView checking = account(owner, AccountType.CHECKING, "500.00"),
        foreign = account(other, AccountType.SAVINGS, "0.00");
    CategoryView foreignCategory = category(other, CategoryType.EXPENSE);
    assertThatThrownBy(
            () ->
                finance.createTransaction(
                    owner, input(checking, TransactionType.TRANSFER, "100.00", null, foreign.id())))
        .isInstanceOfSatisfying(
            ApiException.class, error -> assertThat(error.status.value()).isEqualTo(404));
    assertThatThrownBy(
            () ->
                finance.createTransaction(
                    owner,
                    input(checking, TransactionType.EXPENSE, "50.00", foreignCategory.id(), null)))
        .isInstanceOf(ApiException.class);
    CategoryView income = category(owner, CategoryType.INCOME);
    assertThatThrownBy(
            () ->
                finance.createTransaction(
                    owner, input(checking, TransactionType.EXPENSE, "50.00", income.id(), null)))
        .isInstanceOf(ApiException.class);
    assertThatThrownBy(
            () ->
                finance.createTransaction(
                    owner, input(checking, TransactionType.TRANSFER, "10.00", null, checking.id())))
        .isInstanceOf(ApiException.class);
    assertBalance(owner, checking.id(), "500.00");
  }

  @Test
  void creditDebtBalancesAndRepaymentsHaveConsistentNetWorth() {
    UUID owner = owner();
    AccountView checking = account(owner, AccountType.CHECKING, "1000.00"),
        card = account(owner, AccountType.CREDIT_CARD, "-250.00");
    finance.createTransaction(owner, input(card, TransactionType.EXPENSE, "75.00", null, null));
    finance.createTransaction(
        owner, input(checking, TransactionType.TRANSFER, "100.00", null, card.id()));
    assertBalance(owner, checking.id(), "900.00");
    assertBalance(owner, card.id(), "-225.00");
    assertThat(finance.dashboard(owner).totalBalance()).isEqualByComparingTo("675.00");
    assertThatThrownBy(() -> account(owner, AccountType.SAVINGS, "-1.00"))
        .isInstanceOf(ApiException.class);
  }

  @Test
  void monthlyBudgetsUseActualExpensesAndTighterOverlappingRoom() {
    UUID owner = owner();
    AccountView checking = account(owner, AccountType.CHECKING, "1000.00"),
        savings = account(owner, AccountType.SAVINGS, "0.00");
    CategoryView expenses = category(owner, CategoryType.EXPENSE);
    finance.createTransaction(owner, input(checking, TransactionType.INCOME, "500.00", null, null));
    finance.createTransaction(
        owner, input(checking, TransactionType.EXPENSE, "120.00", expenses.id(), null));
    finance.createTransaction(owner, input(checking, TransactionType.EXPENSE, "50.00", null, null));
    finance.createTransaction(
        owner, input(checking, TransactionType.TRANSFER, "80.00", null, savings.id()));
    String month = YearMonth.from(today).toString();
    BudgetView overall =
        planning.createBudget(owner, new BudgetInput(null, month, new BigDecimal("200.00")));
    BudgetView scoped =
        planning.createBudget(
            owner, new BudgetInput(expenses.id(), month, new BigDecimal("150.00")));
    assertThat(overall.spentAmount()).isEqualByComparingTo("170.00");
    assertThat(scoped.spentAmount()).isEqualByComparingTo("120.00");
    assertThat(overall.status()).isEqualTo("NEAR_LIMIT");
    assertThat(scoped.percentage()).isEqualByComparingTo("80.00");
    assertThat(analytics.budgetRoom(owner, month, expenses.id())).isEqualByComparingTo("30.00");
    assertThatThrownBy(
            () ->
                planning.createBudget(
                    owner, new BudgetInput(null, month, new BigDecimal("300.00"))))
        .isInstanceOfSatisfying(
            ApiException.class, error -> assertThat(error.status.value()).isEqualTo(409));
    AnalyticsView summary = analytics.analytics(owner, month);
    assertThat(summary.income()).isEqualByComparingTo("500.00");
    assertThat(summary.cashFlow()).isEqualByComparingTo("330.00");
    assertThat(summary.spendingTrends()).hasSize(6);
    assertThat(summary.categoryBreakdown()).hasSize(2);
    assertThat(analytics.budgets(owner, YearMonth.from(today).minusMonths(1).toString())).isEmpty();
  }

  @Test
  void recurringPaymentIsIdempotentAndDeletionRestoresTheDueOccurrence() {
    UUID owner = owner();
    AccountView checking = account(owner, AccountType.CHECKING, "500.00");
    CategoryView expenses = category(owner, CategoryType.EXPENSE);
    RecurringView bill =
        planning.createRecurring(
            owner,
            new RecurringInput(
                "Internet",
                RecurringKind.BILL,
                checking.id(),
                expenses.id(),
                new BigDecimal("100.00"),
                RecurringFrequency.MONTHLY,
                today,
                true));
    RecurringPayment request = new RecurringPayment(today, today);
    TransactionView payment = planning.payRecurring(owner, bill.id(), request),
        retry = planning.payRecurring(owner, bill.id(), request);
    assertThat(retry.id()).isEqualTo(payment.id());
    assertBalance(owner, checking.id(), "400.00");
    assertThat(planning.listRecurring(owner, null).getFirst().nextDueDate())
        .isEqualTo(today.plusMonths(1));
    finance.deleteTransaction(owner, payment.id());
    assertBalance(owner, checking.id(), "500.00");
    assertThat(planning.listRecurring(owner, null).getFirst().nextDueDate()).isEqualTo(today);
    TransactionView restored = planning.payRecurring(owner, bill.id(), request);
    assertThat(restored.id()).isNotEqualTo(payment.id());
    assertBalance(owner, checking.id(), "400.00");
    planning.deleteRecurring(owner, bill.id());
    assertBalance(owner, checking.id(), "400.00");
    assertThat(
            finance
                .listTransactions(owner, null, null, null, null, null, 0, 10, "date,desc")
                .totalElements())
        .isEqualTo(1);
  }

  @Test
  void monthEndRecurringPaymentsPreserveTheOriginalDayAnchor() {
    UUID owner = owner();
    AccountView checking = account(owner, AccountType.CHECKING, "500.00");
    CategoryView expenses = category(owner, CategoryType.EXPENSE);
    LocalDate january = LocalDate.of(today.getYear() - 1, 1, 31), february = january.plusMonths(1);
    RecurringView bill =
        planning.createRecurring(
            owner,
            new RecurringInput(
                "Month end",
                RecurringKind.SUBSCRIPTION,
                checking.id(),
                expenses.id(),
                new BigDecimal("10.00"),
                RecurringFrequency.MONTHLY,
                january,
                true));
    planning.payRecurring(owner, bill.id(), new RecurringPayment(january, january));
    assertThat(planning.listRecurring(owner, null).getFirst().nextDueDate()).isEqualTo(february);
    planning.payRecurring(owner, bill.id(), new RecurringPayment(february, february));
    assertThat(planning.listRecurring(owner, null).getFirst().nextDueDate())
        .isEqualTo(january.plusMonths(2));
  }

  @Test
  void goalAllocationLedgerUpdatesProgressWithoutInventingBankMovement() throws Exception {
    UUID owner = owner();
    AccountView checking = account(owner, AccountType.CHECKING, "500.00");
    GoalView goal =
        finance.createGoal(
            owner,
            new GoalInput(
                "Emergency",
                new BigDecimal("1000.00"),
                new BigDecimal("100.00"),
                today.plusYears(1),
                new BigDecimal("50.00"),
                BigDecimal.ZERO));
    mvc.perform(
            post("/api/goals/" + goal.id() + "/contributions")
                .with(user(owner.toString()).roles("USER"))
                .with(realCsrf(mvc, json))
                .contentType("application/json")
                .content("{\"amount\":200,\"date\":\"" + today + "\",\"note\":\"\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.currentAmount").value(300));
    GoalView changed = finance.listGoals(owner).getFirst();
    assertThat(changed.currentAmount()).isEqualByComparingTo("300.00");
    assertBalance(owner, checking.id(), "500.00");
    assertThat(analytics.notifications(owner, null))
        .anyMatch(item -> item.type().equals("GOAL_MILESTONE"));
    assertThatThrownBy(
            () ->
                finance.updateGoal(
                    owner,
                    goal.id(),
                    new GoalInput(
                        "Emergency",
                        new BigDecimal("1000.00"),
                        new BigDecimal("50.00"),
                        today.plusYears(1),
                        BigDecimal.ZERO,
                        BigDecimal.ZERO)))
        .isInstanceOf(ApiException.class);
    UUID contribution = planning.listGoalContributions(owner, goal.id()).getFirst().id();
    assertThat(planning.deleteGoalContribution(owner, goal.id(), contribution).currentAmount())
        .isEqualByComparingTo("100.00");
    assertThat(planning.listGoalContributions(owner, goal.id())).isEmpty();
  }

  @Test
  void csvExportEscapesFormulasQuotesAndEnforcesOwnerFilters() throws Exception {
    UUID owner = owner(), other = owner();
    AccountView checking = account(owner, AccountType.CHECKING, "500.00");
    finance.createTransaction(
        owner,
        new TransactionInput(
            checking.id(),
            TransactionType.EXPENSE,
            new BigDecimal("25.00"),
            " =SUM(1,2) \"quote\"",
            today));
    AccountView foreign = account(other, AccountType.CHECKING, "500.00");
    finance.createTransaction(other, input(foreign, TransactionType.EXPENSE, "80.00", null, null));
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    finance.exportCsv(
        owner, null, null, TransactionType.EXPENSE, null, null, null, "date,desc", output);
    String csv = output.toString(StandardCharsets.UTF_8);
    assertThat(csv).contains("\"'=SUM(1,2) \"\"quote\"\"\"").doesNotContain("80.00");
    assertThat(csv.lines().count()).isEqualTo(2);
    assertThat(FinanceService.csvCell("\t=HYPERLINK(\"bad\")")).startsWith("\"'");
    assertThat(FinanceService.csvCell(" =SUM(1,2)")).isEqualTo("\"' =SUM(1,2)\"");
  }

  @Test
  void newBudgetEndpointsValidateInputsAndRejectForeignResourceUpdates() throws Exception {
    UUID owner = owner(), other = owner();
    String month = YearMonth.from(today).toString();
    mvc.perform(
            post("/api/budgets")
                .with(user(owner.toString()).roles("USER"))
                .with(realCsrf(mvc, json))
                .contentType("application/json")
                .content("{\"categoryId\":null,\"month\":\"" + month + "\",\"limitAmount\":300}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.categoryId").isEmpty())
        .andExpect(jsonPath("$.spentAmount").value(0));
    UUID budget = analytics.budgets(owner, month).getFirst().id();
    mvc.perform(
            delete("/api/budgets/" + budget)
                .with(user(other.toString()).roles("USER"))
                .with(realCsrf(mvc, json)))
        .andExpect(status().isNotFound());
    mvc.perform(
            post("/api/budgets")
                .with(user(owner.toString()).roles("USER"))
                .with(realCsrf(mvc, json))
                .contentType("application/json")
                .content("{\"month\":\"2026-99\",\"limitAmount\":0}"))
        .andExpect(status().isBadRequest());
    mvc.perform(get("/api/analytics").with(user(owner.toString()).roles("USER")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.spendingTrends.length()").value(6));
  }

  @Test
  void unusualExpenseThresholdUsesExactCentsAndRequiresPriorHistory() {
    UUID owner = owner();
    AccountView checking = account(owner, AccountType.CHECKING, "1000.00");
    CategoryView expenses = category(owner, CategoryType.EXPENSE);
    LocalDate prior = YearMonth.from(today).minusMonths(1).atDay(1);
    for (int i = 0; i < 3; i++)
      finance.createTransaction(
          owner,
          new TransactionInput(
              checking.id(),
              TransactionType.EXPENSE,
              new BigDecimal("50.01"),
              "Prior",
              prior,
              expenses.id(),
              null));
    finance.createTransaction(
        owner, input(checking, TransactionType.EXPENSE, "100.02", expenses.id(), null));
    assertThat(analytics.notifications(owner, null))
        .noneMatch(item -> item.type().equals("UNUSUAL_SPENDING"));
    finance.createTransaction(
        owner, input(checking, TransactionType.EXPENSE, "100.03", expenses.id(), null));
    assertThat(analytics.notifications(owner, null))
        .filteredOn(item -> item.type().equals("UNUSUAL_SPENDING"))
        .hasSize(1);
  }

  private UUID owner() {
    return auth.register(
            new Register("Finance", "Tester", UUID.randomUUID() + "@test.dev", "StrongPath!2026"))
        .id;
  }

  private AccountView account(UUID owner, AccountType type, String opening) {
    return finance.createAccount(
        owner, new AccountCreate("Test " + type, type, new BigDecimal(opening)));
  }

  private CategoryView category(UUID owner, CategoryType type) {
    return planning.createCategory(owner, new CategoryInput("Test " + type, type, "#48D1CC"));
  }

  private TransactionInput input(
      AccountView account, TransactionType type, String amount, UUID category, UUID destination) {
    return new TransactionInput(
        account.id(), type, new BigDecimal(amount), "Test entry", today, category, destination);
  }

  private void assertBalance(UUID owner, UUID account, String expected) {
    assertThat(
            finance.listAccounts(owner).stream()
                .filter(value -> value.id().equals(account))
                .findFirst()
                .orElseThrow()
                .balance())
        .isEqualByComparingTo(expected);
  }
}
