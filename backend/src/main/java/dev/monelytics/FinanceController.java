package dev.monelytics;

import static dev.monelytics.ApiDtos.*;
import static dev.monelytics.AuthController.id;
import static dev.monelytics.FinanceDtos.*;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@RestController
@RequestMapping("/api")
@Validated
class FinanceController {
  private final FinanceService finance;
  private final ProjectionService projections;
  private final AuditRepository audit;
  private final PlanningService planning;
  private final AnalyticsService analytics;

  FinanceController(
      FinanceService finance,
      ProjectionService projections,
      AuditRepository audit,
      PlanningService planning,
      AnalyticsService analytics) {
    this.finance = finance;
    this.projections = projections;
    this.audit = audit;
    this.planning = planning;
    this.analytics = analytics;
  }

  @GetMapping("/accounts")
  List<AccountView> accounts(Authentication auth) {
    return finance.listAccounts(id(auth));
  }

  @PostMapping("/accounts")
  @ResponseStatus(HttpStatus.CREATED)
  AccountView createAccount(Authentication auth, @Valid @RequestBody AccountCreate input) {
    return finance.createAccount(id(auth), input);
  }

  @PutMapping("/accounts/{accountId}")
  AccountView updateAccount(
      Authentication auth, @PathVariable UUID accountId, @Valid @RequestBody AccountUpdate input) {
    return finance.updateAccount(id(auth), accountId, input);
  }

  @DeleteMapping("/accounts/{accountId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void deleteAccount(Authentication auth, @PathVariable UUID accountId) {
    finance.deleteAccount(id(auth), accountId);
  }

  @PutMapping("/accounts/{accountId}/beneficiaries")
  AccountView beneficiaries(
      Authentication auth, @PathVariable UUID accountId, @Valid @RequestBody Beneficiaries input) {
    return finance.beneficiaries(id(auth), accountId, input);
  }

  @GetMapping("/transactions")
  PageView<TransactionView> transactions(
      Authentication auth,
      @RequestParam(required = false) @Size(max = 240) String search,
      @RequestParam(required = false) UUID accountId,
      @RequestParam(required = false) TransactionType type,
      @RequestParam(required = false) UUID categoryId,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
      @RequestParam(defaultValue = "0") @Min(0) @Max(100000) int page,
      @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size,
      @RequestParam(defaultValue = "date,desc") String sort) {
    return finance.listTransactions(
        id(auth), search, accountId, type, categoryId, from, to, page, size, sort);
  }

  @GetMapping("/transactions/export.csv")
  ResponseEntity<StreamingResponseBody> export(
      Authentication auth,
      @RequestParam(required = false) @Size(max = 240) String search,
      @RequestParam(required = false) UUID accountId,
      @RequestParam(required = false) TransactionType type,
      @RequestParam(required = false) UUID categoryId,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
      @RequestParam(defaultValue = "date,desc") String sort) {
    UUID user = id(auth);
    finance.validateExport(user, search, accountId, type, categoryId, from, to, sort);
    return ResponseEntity.ok()
        .header(
            HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"monelytics-transactions.csv\"")
        .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
        .body(
            output ->
                finance.exportCsv(
                    user, search, accountId, type, categoryId, from, to, sort, output));
  }

  @PostMapping("/transactions")
  @ResponseStatus(HttpStatus.CREATED)
  TransactionView createTransaction(
      Authentication auth, @Valid @RequestBody TransactionInput input) {
    return finance.createTransaction(id(auth), input);
  }

  @PutMapping("/transactions/{transactionId}")
  TransactionView updateTransaction(
      Authentication auth,
      @PathVariable UUID transactionId,
      @Valid @RequestBody TransactionInput input) {
    return finance.updateTransaction(id(auth), transactionId, input);
  }

  @DeleteMapping("/transactions/{transactionId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void deleteTransaction(Authentication auth, @PathVariable UUID transactionId) {
    finance.deleteTransaction(id(auth), transactionId);
  }

  @GetMapping("/goals")
  List<GoalView> goals(Authentication auth) {
    return finance.listGoals(id(auth));
  }

  @PostMapping("/goals")
  @ResponseStatus(HttpStatus.CREATED)
  GoalView createGoal(Authentication auth, @Valid @RequestBody GoalInput input) {
    return finance.createGoal(id(auth), input);
  }

  @PutMapping("/goals/{goalId}")
  GoalView updateGoal(
      Authentication auth, @PathVariable UUID goalId, @Valid @RequestBody GoalInput input) {
    return finance.updateGoal(id(auth), goalId, input);
  }

  @DeleteMapping("/goals/{goalId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void deleteGoal(Authentication auth, @PathVariable UUID goalId) {
    finance.deleteGoal(id(auth), goalId);
  }

  @PostMapping("/goals/calculate")
  Calculation calculate(@Valid @RequestBody Calculate input) {
    return projections.calculate(input);
  }

  @GetMapping("/dashboard")
  Dashboard dashboard(
      Authentication auth, @RequestParam(required = false) @Size(max = 7) String month) {
    return finance.dashboard(id(auth), month);
  }

  @GetMapping("/categories")
  List<CategoryView> categories(Authentication auth) {
    return planning.listCategories(id(auth));
  }

  @PostMapping("/categories")
  @ResponseStatus(HttpStatus.CREATED)
  CategoryView category(Authentication auth, @Valid @RequestBody CategoryInput input) {
    return planning.createCategory(id(auth), input);
  }

  @PutMapping("/categories/{categoryId}")
  CategoryView category(
      Authentication auth, @PathVariable UUID categoryId, @Valid @RequestBody CategoryInput input) {
    return planning.updateCategory(id(auth), categoryId, input);
  }

  @DeleteMapping("/categories/{categoryId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void deleteCategory(Authentication auth, @PathVariable UUID categoryId) {
    planning.deleteCategory(id(auth), categoryId);
  }

  @GetMapping("/budgets")
  List<BudgetView> budgets(
      Authentication auth, @RequestParam(required = false) @Size(max = 7) String month) {
    return analytics.budgets(id(auth), month);
  }

  @PostMapping("/budgets")
  @ResponseStatus(HttpStatus.CREATED)
  BudgetView budget(Authentication auth, @Valid @RequestBody BudgetInput input) {
    return planning.createBudget(id(auth), input);
  }

  @PutMapping("/budgets/{budgetId}")
  BudgetView budget(
      Authentication auth, @PathVariable UUID budgetId, @Valid @RequestBody BudgetInput input) {
    return planning.updateBudget(id(auth), budgetId, input);
  }

  @DeleteMapping("/budgets/{budgetId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void deleteBudget(Authentication auth, @PathVariable UUID budgetId) {
    planning.deleteBudget(id(auth), budgetId);
  }

  @GetMapping("/recurring")
  List<RecurringView> recurring(
      Authentication auth, @RequestParam(required = false) RecurringKind kind) {
    return planning.listRecurring(id(auth), kind);
  }

  @PostMapping("/recurring")
  @ResponseStatus(HttpStatus.CREATED)
  RecurringView recurring(Authentication auth, @Valid @RequestBody RecurringInput input) {
    return planning.createRecurring(id(auth), input);
  }

  @PutMapping("/recurring/{recurringId}")
  RecurringView recurring(
      Authentication auth,
      @PathVariable UUID recurringId,
      @Valid @RequestBody RecurringInput input) {
    return planning.updateRecurring(id(auth), recurringId, input);
  }

  @DeleteMapping("/recurring/{recurringId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void deleteRecurring(Authentication auth, @PathVariable UUID recurringId) {
    planning.deleteRecurring(id(auth), recurringId);
  }

  @PostMapping("/recurring/{recurringId}/pay")
  TransactionView pay(
      Authentication auth,
      @PathVariable UUID recurringId,
      @Valid @RequestBody RecurringPayment input) {
    return planning.payRecurring(id(auth), recurringId, input);
  }

  @GetMapping("/goals/{goalId}/contributions")
  List<GoalContributionView> contributions(Authentication auth, @PathVariable UUID goalId) {
    return planning.listGoalContributions(id(auth), goalId);
  }

  @PostMapping("/goals/{goalId}/contributions")
  GoalView contribution(
      Authentication auth,
      @PathVariable UUID goalId,
      @Valid @RequestBody GoalContributionInput input) {
    return planning.contributeToGoal(id(auth), goalId, input);
  }

  @DeleteMapping("/goals/{goalId}/contributions/{contributionId}")
  GoalView deleteContribution(
      Authentication auth, @PathVariable UUID goalId, @PathVariable UUID contributionId) {
    return planning.deleteGoalContribution(id(auth), goalId, contributionId);
  }

  @GetMapping("/analytics")
  AnalyticsView analytics(
      Authentication auth, @RequestParam(required = false) @Size(max = 7) String month) {
    return analytics.analytics(id(auth), month);
  }

  @GetMapping("/notifications")
  List<NotificationView> notifications(
      Authentication auth, @RequestParam(required = false) @Size(max = 7) String month) {
    return analytics.notifications(id(auth), month);
  }

  @GetMapping("/audit")
  PageView<AuditView> audit(
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    Page<AuditEvent> events =
        audit.findAll(PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "occurredAt")));
    return new PageView<>(
        events.getContent().stream()
            .map(
                e -> new AuditView(e.id, e.actorId, e.action, e.resourceId, e.detail, e.occurredAt))
            .toList(),
        events.getTotalElements(),
        events.getTotalPages(),
        page,
        size);
  }
}
