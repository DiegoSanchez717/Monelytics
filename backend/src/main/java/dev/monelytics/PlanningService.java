package dev.monelytics;

import static dev.monelytics.ApiDtos.*;
import static dev.monelytics.FinanceDtos.*;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@Transactional
class PlanningService {
  private final UserRepository users;
  private final AccountRepository accounts;
  private final CategoryRepository categories;
  private final BudgetRepository budgets;
  private final RecurringRepository recurring;
  private final TransactionRepository transactions;
  private final GoalRepository goals;
  private final GoalContributionRepository contributions;
  private final FinanceService finance;
  private final AnalyticsService analytics;
  private final AuditService audit;

  PlanningService(
      UserRepository users,
      AccountRepository accounts,
      CategoryRepository categories,
      BudgetRepository budgets,
      RecurringRepository recurring,
      TransactionRepository transactions,
      GoalRepository goals,
      GoalContributionRepository contributions,
      FinanceService finance,
      AnalyticsService analytics,
      AuditService audit) {
    this.users = users;
    this.accounts = accounts;
    this.categories = categories;
    this.budgets = budgets;
    this.recurring = recurring;
    this.transactions = transactions;
    this.goals = goals;
    this.contributions = contributions;
    this.finance = finance;
    this.analytics = analytics;
    this.audit = audit;
  }

  @Transactional(readOnly = true)
  List<CategoryView> listCategories(UUID user) {
    return categories.findByUserIdOrderByNameAsc(user).stream().map(CategoryView::of).toList();
  }

  CategoryView createCategory(UUID user, CategoryInput input) {
    AppUser owner = lockUser(user);
    FinanceCategory category = new FinanceCategory();
    category.user = owner;
    assign(category, input);
    categories.saveAndFlush(category);
    audit.record(user, "CATEGORY_CREATED", category.id, "Transaction category created");
    return CategoryView.of(category);
  }

  CategoryView updateCategory(UUID user, UUID id, CategoryInput input) {
    lockUser(user);
    FinanceCategory category = ownedCategory(user, id);
    if (category.type != input.type() && usedCategory(id))
      throw ApiException.invalid("A category in use cannot change income/expense type.");
    assign(category, input);
    categories.saveAndFlush(category);
    audit.record(user, "CATEGORY_UPDATED", id, "Transaction category updated");
    return CategoryView.of(category);
  }

  void deleteCategory(UUID user, UUID id) {
    lockUser(user);
    FinanceCategory category = ownedCategory(user, id);
    if (usedCategory(id))
      throw ApiException.invalid(
          "Move transactions, budgets, and recurring items to another category before deleting this category.");
    categories.delete(category);
    audit.record(user, "CATEGORY_DELETED", id, "Unused category deleted");
  }

  private boolean usedCategory(UUID id) {
    return transactions.existsByCategoryId(id)
        || budgets.existsByCategoryId(id)
        || recurring.existsByCategoryId(id);
  }

  private void assign(FinanceCategory category, CategoryInput input) {
    category.name = input.name().trim();
    category.nameKey = category.name.toLowerCase(Locale.ROOT);
    category.type = input.type();
    category.color = input.color().toUpperCase(Locale.ROOT);
  }

  BudgetView createBudget(UUID user, BudgetInput input) {
    AppUser owner = lockUser(user);
    MonthlyBudget budget = new MonthlyBudget();
    budget.user = owner;
    assign(user, budget, input);
    checkDuplicateBudget(user, budget, null);
    budgets.saveAndFlush(budget);
    audit.record(user, "BUDGET_CREATED", budget.id, "Monthly budget created");
    return budgetView(user, budget);
  }

  BudgetView updateBudget(UUID user, UUID id, BudgetInput input) {
    lockUser(user);
    MonthlyBudget budget = budgets.findByIdAndUserId(id, user).orElseThrow(ApiException::missing);
    String oldMonth = budget.month, oldScope = budget.scopeKey;
    assign(user, budget, input);
    if (!oldMonth.equals(budget.month) || !oldScope.equals(budget.scopeKey))
      checkDuplicateBudget(user, budget, id);
    budgets.saveAndFlush(budget);
    audit.record(user, "BUDGET_UPDATED", id, "Monthly budget updated");
    return budgetView(user, budget);
  }

  void deleteBudget(UUID user, UUID id) {
    lockUser(user);
    budgets.delete(budgets.findByIdAndUserId(id, user).orElseThrow(ApiException::missing));
    audit.record(user, "BUDGET_DELETED", id, "Monthly budget deleted");
  }

  private void assign(UUID user, MonthlyBudget budget, BudgetInput input) {
    budget.month = AnalyticsService.month(input.month()).toString();
    budget.limitAmount = input.limitAmount().setScale(2);
    budget.category = input.categoryId() == null ? null : expenseCategory(user, input.categoryId());
    budget.scopeKey = budget.category == null ? "ALL" : budget.category.id.toString();
  }

  private void checkDuplicateBudget(UUID user, MonthlyBudget budget, UUID ignored) {
    if (budgets.existsByUserIdAndMonthAndScopeKey(user, budget.month, budget.scopeKey))
      throw new ApiException(
          HttpStatus.CONFLICT,
          "BUDGET_EXISTS",
          "A budget for this month and scope already exists.");
  }

  private BudgetView budgetView(UUID user, MonthlyBudget budget) {
    return analytics.budgets(user, budget.month).stream()
        .filter(value -> value.id().equals(budget.id))
        .findFirst()
        .orElseThrow();
  }

  @Transactional(readOnly = true)
  List<RecurringView> listRecurring(UUID user, RecurringKind kind) {
    return recurring.findByUserIdOrderByNextDueDateAsc(user).stream()
        .filter(item -> kind == null || item.kind == kind)
        .map(RecurringView::of)
        .toList();
  }

  RecurringView createRecurring(UUID user, RecurringInput input) {
    AppUser owner = lockUser(user);
    RecurringItem item = new RecurringItem();
    item.user = owner;
    assign(user, item, input);
    recurring.saveAndFlush(item);
    audit.record(user, "RECURRING_CREATED", item.id, "Recurring payment planned");
    return RecurringView.of(item);
  }

  RecurringView updateRecurring(UUID user, UUID id, RecurringInput input) {
    lockUser(user);
    RecurringItem item = ownedRecurring(user, id);
    assign(user, item, input);
    if (transactions.findByRecurringIdAndRecurringDueDate(item.id, item.nextDueDate).isPresent())
      throw ApiException.invalid(
          "That recurring occurrence is already paid. Choose another due date.");
    audit.record(user, "RECURRING_UPDATED", id, "Recurring payment updated");
    return RecurringView.of(item);
  }

  void deleteRecurring(UUID user, UUID id) {
    lockUser(user);
    recurring.delete(ownedRecurring(user, id));
    recurring.flush();
    // The database detaches its payment links; posted expense history and balances remain intact.
    audit.record(user, "RECURRING_DELETED", id, "Recurring plan deleted; posted payments retained");
  }

  TransactionView payRecurring(UUID user, UUID id, RecurringPayment input) {
    lockUser(user);
    RecurringItem item = ownedRecurring(user, id);
    Optional<LedgerTransaction> paid =
        transactions.findByRecurringIdAndRecurringDueDate(id, input.dueDate());
    if (paid.isPresent()) return TransactionView.of(paid.get());
    if (!item.active)
      throw ApiException.invalid("Activate this recurring item before recording a payment.");
    if (!input.dueDate().equals(item.nextDueDate))
      throw ApiException.invalid("The due date changed. Refresh before recording this payment.");
    TransactionView created =
        finance.createTransaction(
            user,
            new TransactionInput(
                item.account.getId(),
                TransactionType.EXPENSE,
                item.amount,
                item.name + " payment",
                input.date(),
                item.category.getId(),
                null));
    LedgerTransaction payment = transactions.findById(created.id()).orElseThrow();
    payment.recurring = item;
    payment.recurringDueDate = input.dueDate();
    transactions.saveAndFlush(payment);
    item.nextDueDate = nextOccurrence(item, input.dueDate());
    while (transactions.findByRecurringIdAndRecurringDueDate(id, item.nextDueDate).isPresent())
      item.nextDueDate = nextOccurrence(item, item.nextDueDate);
    audit.record(user, "RECURRING_PAID", id, "Recurring occurrence paid once");
    return TransactionView.of(payment);
  }

  private LocalDate nextOccurrence(RecurringItem item, LocalDate date) {
    YearMonth next =
        YearMonth.from(date).plusMonths(item.frequency == RecurringFrequency.MONTHLY ? 1 : 12);
    return next.atDay(Math.min(item.anchorDay, next.lengthOfMonth()));
  }

  private void assign(UUID user, RecurringItem item, RecurringInput input) {
    if (item.nextDueDate == null || !item.nextDueDate.equals(input.nextDueDate()))
      item.anchorDay = input.nextDueDate().getDayOfMonth();
    item.name = input.name().trim();
    item.kind = input.kind();
    item.amount = input.amount().setScale(2);
    item.frequency = input.frequency();
    item.nextDueDate = input.nextDueDate();
    item.active = input.active();
    item.account =
        accounts.findByIdAndUserId(input.accountId(), user).orElseThrow(ApiException::missing);
    item.category = expenseCategory(user, input.categoryId());
  }

  @Transactional(readOnly = true)
  List<GoalContributionView> listGoalContributions(UUID user, UUID goalId) {
    goals.findByIdAndUserId(goalId, user).orElseThrow(ApiException::missing);
    return contributions
        .findByGoalIdAndGoalUserIdOrderByDateDescCreatedAtDesc(goalId, user)
        .stream()
        .map(GoalContributionView::of)
        .toList();
  }

  GoalView contributeToGoal(UUID user, UUID id, GoalContributionInput input) {
    lockUser(user);
    SavingsGoal goal = goals.findByIdAndUserId(id, user).orElseThrow(ApiException::missing);
    BigDecimal current = goal.currentAmount.add(input.amount());
    if (current.compareTo(new BigDecimal("999999999999.99")) > 0)
      throw ApiException.invalid("Goal savings exceed the supported amount.");
    GoalContribution contribution = new GoalContribution();
    contribution.goal = goal;
    contribution.amount = input.amount().setScale(2);
    contribution.date = input.date();
    contribution.note = Objects.toString(input.note(), "").trim();
    contributions.save(contribution);
    // This is an earmarking ledger, not a bank transfer. Never invent a debit or credit on
    // accounts.
    goal.currentAmount = current;
    audit.record(user, "GOAL_CONTRIBUTED", id, "Savings allocation recorded");
    return GoalView.of(goal);
  }

  GoalView deleteGoalContribution(UUID user, UUID goalId, UUID contributionId) {
    lockUser(user);
    SavingsGoal goal = goals.findByIdAndUserId(goalId, user).orElseThrow(ApiException::missing);
    GoalContribution contribution =
        contributions
            .findByIdAndGoalIdAndGoalUserId(contributionId, goalId, user)
            .orElseThrow(ApiException::missing);
    goal.currentAmount = goal.currentAmount.subtract(contribution.amount);
    contributions.delete(contribution);
    audit.record(user, "GOAL_CONTRIBUTION_DELETED", contributionId, "Savings allocation reversed");
    return GoalView.of(goal);
  }

  private AppUser lockUser(UUID user) {
    return users.lockById(user).orElseThrow(ApiException::missing);
  }

  private FinanceCategory ownedCategory(UUID user, UUID id) {
    return org.hibernate.Hibernate.unproxy(
        categories.findByIdAndUserId(id, user).orElseThrow(ApiException::missing),
        FinanceCategory.class);
  }

  private FinanceCategory expenseCategory(UUID user, UUID id) {
    FinanceCategory category = ownedCategory(user, id);
    if (category.type != CategoryType.EXPENSE)
      throw ApiException.invalid("Choose an expense category.");
    return category;
  }

  private RecurringItem ownedRecurring(UUID user, UUID id) {
    return recurring.findByIdAndUserId(id, user).orElseThrow(ApiException::missing);
  }
}
