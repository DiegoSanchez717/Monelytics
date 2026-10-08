package dev.wealthpath;

import static dev.wealthpath.ApiDtos.*;
import static dev.wealthpath.AuthController.id;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@Validated
class FinanceController {
  private final FinanceService finance;
  private final ProjectionService projections;
  private final AuditRepository audit;

  FinanceController(FinanceService finance, ProjectionService projections, AuditRepository audit) {
    this.finance = finance;
    this.projections = projections;
    this.audit = audit;
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
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
      @RequestParam(defaultValue = "0") @Min(0) @Max(100000) int page,
      @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size,
      @RequestParam(defaultValue = "date,desc") String sort) {
    return finance.listTransactions(id(auth), search, accountId, type, from, to, page, size, sort);
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
  Dashboard dashboard(Authentication auth) {
    return finance.dashboard(id(auth));
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
