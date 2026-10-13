package dev.monelytics;

import static dev.monelytics.ApiDtos.*;
import static dev.monelytics.FinanceDtos.*;

import dev.monelytics.FinancialAssistantService.Factor;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.*;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.ObjectMapper;

/** Providers receive a general question only; ledger access and affordability stay server-owned. */
interface FinancialEducationProvider {
  record Education(String answer, String provider, String fallbackReason) {
    Education(String answer, String provider) {
      this(answer, provider, provider.equals("MOCK") ? "NOT_CONFIGURED" : null);
    }
  }

  record ProviderStatus(
      String provider,
      boolean configured,
      String model,
      int dailyRequestLimit,
      int remainingRequests,
      String disclosure) {}

  Education answer(String question);

  default ProviderStatus status() {
    return new ProviderStatus(
        "MOCK",
        false,
        null,
        0,
        0,
        "Educational fallback is active. Hosted answers require a Groq Free account key and explicit free-plan confirmation. Recorded account data stays on this server.");
  }
}

class MockFinancialEducationProvider implements FinancialEducationProvider {
  public Education answer(String question) {
    String topic = question.toLowerCase(Locale.ROOT);
    String text;
    if (topic.contains("50/30/20") || topic.contains("50-30-20"))
      text =
          "The 50/30/20 guideline divides take-home income into needs, wants, and savings or debt reduction. About 50% goes to essentials, 30% to optional spending, and 20% to future goals. These are starting points, not requirements: essential costs, income stability, and debt can justify different proportions. Compare the guideline with recorded spending and adjust the plan to your circumstances.";
    else if (topic.contains("debt") || topic.contains("credit"))
      text =
          "A useful debt plan starts with required payments, interest rates, and a manageable payment schedule. Compare the highest-interest-first and smallest-balance-first approaches, while keeping essential bills covered. Recorded balances may differ from lender statements.";
    else if (topic.contains("emergency"))
      text =
          "An emergency fund can help cover unexpected essential costs without borrowing. Set a target based on your essential expenses and how predictable your income is, then build it gradually in accessible savings.";
    else if (topic.contains("invest") || topic.contains("retirement") || topic.contains("interest"))
      text =
          "Compounding means returns can themselves earn returns over time. Future returns are uncertain. Consider your time horizon, diversification, fees, and emergency savings before making investment decisions; a projection is an illustration, not a guarantee.";
    else if (topic.contains("subscription") || topic.contains("bill"))
      text =
          "Review recurring charges for services you use, renewal dates, and cancellation terms. Comparing those charges with essential bills and savings plans can identify opportunities to free up monthly cash flow.";
    else if (topic.contains("tax") || topic.contains("stock") || topic.contains("legal"))
      text =
          "Tax, legal, and investment decisions depend on individual circumstances and current rules. Use official sources and an appropriately qualified professional for those decisions. This assistant can help organize your recorded spending and goals.";
    else
      text =
          "A practical spending plan starts with recorded income, essential expenses, upcoming bills, and savings commitments. Give each spending category a realistic limit, review it regularly, and adjust your plan when circumstances change.";
    return new Education(text, "MOCK");
  }
}

@Configuration
class FinancialAssistantConfiguration {
  @Bean
  FinancialEducationProvider financialEducationProvider(
      ObjectMapper json,
      @Value("${monelytics.ai.provider:groq}") String mode,
      @Value("${monelytics.ai.api-key:}") String key,
      @Value("${monelytics.ai.model:openai/gpt-oss-20b}") String model,
      @Value("${monelytics.ai.free-plan-confirmed:false}") boolean freePlanConfirmed,
      @Value("${monelytics.ai.daily-request-limit:40}") int dailyRequestLimit) {
    if (!Set.of("groq", "mock").contains(mode))
      throw new IllegalArgumentException(
          "AI_PROVIDER must be groq or mock. Local downloads and other hosted providers are disabled.");
    if (key.isBlank() || mode.equals("mock") || !freePlanConfirmed)
      return new MockFinancialEducationProvider();
    return new GroqFinancialEducationProvider(json, key, model, dailyRequestLimit);
  }
}

@Service
class FinancialAssistantService {
  static final String DISCLAIMER =
      "Educational information only, not professional financial advice. Assessments use your recorded information, which may be incomplete. Monelytics never moves money or performs transactions.";

  record Question(
      @NotBlank @Size(max = 1200) String question,
      @DecimalMin(value = "0", inclusive = false)
          @DecimalMax("999999999999.99")
          @Digits(integer = 12, fraction = 2)
          BigDecimal purchaseAmount,
      UUID categoryId,
      @Pattern(regexp = "[0-9]{4}-(0[1-9]|1[0-2])") String month) {
    @Override
    public String toString() {
      return "AssistantQuestion[private content redacted]";
    }
  }

  record Factor(String label, BigDecimal value, String explanation) {}

  record Reply(
      String answer,
      String provider,
      String decision,
      List<Factor> factors,
      List<String> recommendations,
      String disclaimer,
      boolean readOnly,
      Instant asOf,
      String fallbackReason) {}

  private final FinancialAssessmentService assessmentService;
  private final FinancialEducationProvider provider;

  FinancialAssistantService(
      FinancialAssessmentService assessmentService, FinancialEducationProvider provider) {
    this.assessmentService = assessmentService;
    this.provider = provider;
  }

  Reply chat(UUID user, Question question) {
    Assessment assessment = assessmentService.assess(user, question);
    var education = provider.answer(question.question());
    String conclusion =
        question.purchaseAmount() == null
            ? ""
            : switch (assessment.decision()) {
              case "LIKELY_AFFORDABLE" ->
                  "Based on the recorded information, this purchase appears to fit the available cash flow, cash balances, and applicable budgets after reserving upcoming bills and planned savings. ";
              case "NOT_AFFORDABLE" ->
                  "This purchase exceeds at least one recorded cash-flow, balance, or budget constraint. Delaying or reducing it would protect the plan shown below. ";
              case "INSUFFICIENT_DATA" ->
                  "There is not enough recorded income to make a reliable affordability assessment. Add current income and expenses before relying on a purchase estimate. ";
              default ->
                  "This purchase leaves a limited cushion, lacks a selected budget category, or uses a historical month. Review the factors before deciding. ";
            };
    return new Reply(
        conclusion + education.answer(),
        education.provider(),
        assessment.decision(),
        assessment.factors(),
        assessment.recommendations(),
        DISCLAIMER,
        true,
        Instant.now(),
        education.fallbackReason());
  }

  FinancialEducationProvider.ProviderStatus status() {
    return provider.status();
  }

  record Assessment(String decision, List<Factor> factors, List<String> recommendations) {}
}

@Service
class FinancialAssessmentService {
  private final FinanceService finance;
  private final AnalyticsService analytics;
  private final PlanningService planning;

  FinancialAssessmentService(
      FinanceService finance, AnalyticsService analytics, PlanningService planning) {
    this.finance = finance;
    this.analytics = analytics;
    this.planning = planning;
  }

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public FinancialAssistantService.Assessment assess(
      UUID user, FinancialAssistantService.Question question) {
    String month = AnalyticsService.month(question.month()).toString();
    if (question.categoryId() != null
        && planning.listCategories(user).stream()
            .noneMatch(
                category ->
                    category.id().equals(question.categoryId())
                        && category.type() == CategoryType.EXPENSE)) throw ApiException.missing();
    AnalyticsView totals = analytics.analytics(user, month);
    var accounts = finance.listAccounts(user);
    BigDecimal liquid =
        accounts.stream()
            .filter(
                account ->
                    account.type() == AccountType.CHECKING || account.type() == AccountType.SAVINGS)
            .map(AccountView::balance)
            .map(balance -> balance.max(BigDecimal.ZERO))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    BigDecimal debt =
        accounts.stream()
            .filter(
                account ->
                    account.type() == AccountType.CREDIT_CARD && account.balance().signum() < 0)
            .map(account -> account.balance().negate())
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    LocalDate today = LocalDate.now(ZoneOffset.UTC);
    BigDecimal bills =
        planning.listRecurring(user, null).stream()
            .filter(item -> item.active() && !item.nextDueDate().isAfter(today.plusDays(30)))
            .map(RecurringView::amount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    BigDecimal savings =
        finance.listGoals(user).stream()
            .filter(goal -> goal.currentAmount().compareTo(goal.targetAmount()) < 0)
            .map(GoalView::monthlyContribution)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    BigDecimal budgetRoom = analytics.budgetRoom(user, month, question.categoryId());
    BigDecimal available =
        totals
            .cashFlow()
            .subtract(bills)
            .subtract(savings)
            .min(liquid.subtract(debt).subtract(bills).subtract(savings));
    if (budgetRoom != null) available = available.min(budgetRoom);
    List<FinancialAssistantService.Factor> factors =
        new ArrayList<>(
            List.of(
                new Factor(
                    "Recorded monthly income",
                    money(totals.income()),
                    "INCOME entries in "
                        + month
                        + "; transfers and opening balances are excluded."),
                new Factor(
                    "Recorded monthly expenses",
                    money(totals.expenses()),
                    "EXPENSE entries in "
                        + month
                        + "; pending recurring bills are reserved separately."),
                new Factor(
                    "Monthly cash flow",
                    money(totals.cashFlow()),
                    "Recorded income minus recorded expenses, before future commitments."),
                new Factor(
                    "Liquid account balances",
                    money(liquid),
                    "Positive checking and savings balances; IRA balances are not spending cash."),
                new Factor(
                    "Recorded credit-card debt",
                    money(debt),
                    "Negative credit-card balances are reserved against liquid funds."),
                new Factor(
                    "Upcoming unpaid bills",
                    money(bills),
                    "One next occurrence of each active bill or subscription due within thirty days, including overdue items."),
                new Factor(
                    "Planned monthly savings",
                    money(savings),
                    "Monthly contributions planned for incomplete savings goals; contribution records do not automatically transfer money.")));
    if (budgetRoom != null)
      factors.add(
          new Factor(
              "Applicable budget room",
              money(budgetRoom),
              "The tighter remaining amount of the overall monthly budget and selected category budget."));
    factors.add(
        new Factor(
            "Available purchase room",
            money(available),
            "The smallest recorded constraint after reserving bills, debt, and planned savings; this is a conservative estimate."));
    String decision = "NOT_REQUESTED";
    List<String> recommendations = new ArrayList<>();
    if (totals.income().signum() == 0)
      recommendations.add(
          "Record current income before treating any purchase estimate as reliable.");
    if (totals.cashFlow().signum() < 0)
      recommendations.add(
          "Recorded expenses exceed recorded income this month. Review the largest spending categories and recurring charges.");
    var categories = totals.categoryBreakdown();
    categories.stream()
        .max(Comparator.comparing(CategorySpending::value))
        .ifPresent(
            category ->
                recommendations.add(
                    "Review "
                        + category.name()
                        + ", your largest recorded expense category this month, for flexible spending."));
    if (budgetRoom != null && budgetRoom.signum() <= 0)
      recommendations.add(
          "An applicable budget has no remaining room. Revisit the spending plan before adding an expense.");
    if (savings.signum() > 0)
      recommendations.add(
          "Keep the planned savings contributions reserved while evaluating new purchases.");
    if (question.purchaseAmount() != null) {
      BigDecimal cushion = available.subtract(question.purchaseAmount());
      factors.add(
          new Factor(
              "Purchase amount",
              money(question.purchaseAmount()),
              "The amount you entered; it has not been added to the ledger."));
      factors.add(
          new Factor(
              "Cushion after purchase",
              money(cushion),
              "Available purchase room minus the proposed amount."));
      boolean unknownCategoryBudget =
          question.categoryId() == null
              && analytics.budgets(user, month).stream()
                  .anyMatch(item -> item.categoryId() != null);
      boolean historicalMonth = !month.equals(YearMonth.now(ZoneOffset.UTC).toString());
      if (totals.income().signum() == 0) decision = "INSUFFICIENT_DATA";
      else if (cushion.signum() < 0) decision = "NOT_AFFORDABLE";
      else if (unknownCategoryBudget
          || historicalMonth
          || cushion.compareTo(totals.income().multiply(new BigDecimal("0.10"))) < 0)
        decision = "CAUTION";
      else decision = "LIKELY_AFFORDABLE";
      if (unknownCategoryBudget)
        recommendations.add("Select the purchase category to check its specific budget limit.");
      if (historicalMonth)
        recommendations.add(
            "The selected month is historical. Compare with current income and commitments before deciding.");
      if (cushion.signum() < 0)
        recommendations.add(
            "Delay, reduce, or save separately for this purchase rather than using funds reserved for existing commitments.");
    }
    if (recommendations.isEmpty())
      recommendations.add(
          "Add a realistic monthly budget and a savings goal, then keep recorded transactions current.");
    return new FinancialAssistantService.Assessment(
        decision, List.copyOf(factors), List.copyOf(recommendations));
  }

  private static BigDecimal money(BigDecimal value) {
    return value.setScale(2, RoundingMode.HALF_UP);
  }
}

@RestController
@RequestMapping("/api/assistant")
class FinancialAssistantController {
  private final FinancialAssistantService assistant;

  FinancialAssistantController(FinancialAssistantService assistant) {
    this.assistant = assistant;
  }

  @PostMapping("/chat")
  FinancialAssistantService.Reply chat(
      Authentication principal, @Valid @RequestBody FinancialAssistantService.Question input) {
    return assistant.chat(AuthController.id(principal), input);
  }

  @GetMapping("/status")
  FinancialEducationProvider.ProviderStatus status() {
    return assistant.status();
  }
}
