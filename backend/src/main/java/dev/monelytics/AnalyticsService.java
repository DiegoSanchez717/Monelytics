package dev.monelytics;

import static dev.monelytics.FinanceDtos.*;

import java.math.*;
import java.time.*;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
class AnalyticsService {
  private final TransactionRepository transactions;
  private final BudgetRepository budgets;
  private final RecurringRepository recurring;
  private final GoalRepository goals;

  AnalyticsService(
      TransactionRepository transactions,
      BudgetRepository budgets,
      RecurringRepository recurring,
      GoalRepository goals) {
    this.transactions = transactions;
    this.budgets = budgets;
    this.recurring = recurring;
    this.goals = goals;
  }

  static YearMonth month(String value) {
    try {
      YearMonth result =
          value == null || value.isBlank() ? YearMonth.now(ZoneOffset.UTC) : YearMonth.parse(value);
      if (result.getYear() < 1900 || result.getYear() > 2100) throw new DateTimeException("year");
      return result;
    } catch (DateTimeException e) {
      throw ApiException.invalid("Month must use YYYY-MM between 1900 and 2100.");
    }
  }

  AnalyticsView analytics(UUID user, String requestedMonth) {
    YearMonth selected = month(requestedMonth);
    Map<YearMonth, MonthAggregate> totals = new HashMap<>();
    for (MonthAggregate item :
        transactions.monthlyTotals(user, selected.minusMonths(5).atDay(1), selected.atEndOfMonth()))
      totals.put(YearMonth.of(item.getYearNumber(), item.getMonthNumber()), item);
    List<MonthlyTrend> trends = new ArrayList<>();
    for (int offset = 5; offset >= 0; offset--) {
      YearMonth period = selected.minusMonths(offset);
      MonthAggregate item = totals.get(period);
      BigDecimal income = item == null ? money(BigDecimal.ZERO) : money(item.getIncome());
      BigDecimal expenses = item == null ? money(BigDecimal.ZERO) : money(item.getExpenses());
      trends.add(new MonthlyTrend(period.toString(), income, expenses, income.subtract(expenses)));
    }
    MonthlyTrend current = trends.getLast();
    BigDecimal rate =
        current.income().signum() == 0
            ? BigDecimal.ZERO
            : current
                .cashFlow()
                .multiply(new BigDecimal("100"))
                .divide(current.income(), 2, RoundingMode.HALF_UP);
    return new AnalyticsView(
        selected.toString(),
        current.income(),
        current.expenses(),
        current.cashFlow(),
        rate,
        trends,
        spending(user, selected).stream()
            .map(
                item ->
                    new CategorySpending(
                        item.getCategoryId(),
                        item.getName(),
                        item.getColor(),
                        money(item.getTotal())))
            .toList());
  }

  List<BudgetView> budgets(UUID user, String requestedMonth) {
    YearMonth selected = month(requestedMonth);
    Map<UUID, BigDecimal> spending = new HashMap<>();
    BigDecimal total = BigDecimal.ZERO;
    for (CategoryAggregate item : spending(user, selected)) {
      spending.put(item.getCategoryId(), money(item.getTotal()));
      total = total.add(item.getTotal());
    }
    List<BudgetView> result = new ArrayList<>();
    for (MonthlyBudget budget :
        budgets.findByUserIdAndMonthOrderByScopeKeyAsc(user, selected.toString())) {
      BigDecimal spent =
          budget.category == null
              ? total
              : spending.getOrDefault(budget.category.getId(), BigDecimal.ZERO);
      BigDecimal percentage =
          spent.multiply(new BigDecimal("100")).divide(budget.limitAmount, 2, RoundingMode.HALF_UP);
      String status =
          percentage.compareTo(new BigDecimal("100")) >= 0
              ? "OVER_BUDGET"
              : percentage.compareTo(new BigDecimal("80")) >= 0 ? "NEAR_LIMIT" : "ON_TRACK";
      result.add(
          new BudgetView(
              budget.id,
              budget.category == null ? null : budget.category.getId(),
              budget.category == null ? "Overall budget" : budget.category.getName(),
              budget.month,
              budget.limitAmount,
              money(spent),
              money(budget.limitAmount.subtract(spent)),
              percentage,
              status));
    }
    return result;
  }

  BigDecimal budgetRoom(UUID user, String requestedMonth, UUID categoryId) {
    // Overall and category budgets constrain the same dollars: use the tighter room, never sum.
    return budgets(user, requestedMonth).stream()
        .filter(item -> item.categoryId() == null || Objects.equals(item.categoryId(), categoryId))
        .map(BudgetView::remainingAmount)
        .min(BigDecimal::compareTo)
        .orElse(null);
  }

  List<NotificationView> notifications(UUID user, String requestedMonth) {
    YearMonth selected = month(requestedMonth);
    List<NotificationView> result = new ArrayList<>();
    for (BudgetView budget : budgets(user, selected.toString())) {
      if (budget.percentage().compareTo(new BigDecimal("80")) >= 0) {
        boolean limit = budget.percentage().compareTo(new BigDecimal("100")) >= 0;
        result.add(
            new NotificationView(
                "budget:" + budget.id() + ":" + selected,
                "BUDGET",
                limit ? "Budget limit reached" : "Budget getting close",
                budget.categoryName()
                    + " has used "
                    + budget.percentage().stripTrailingZeros().toPlainString()
                    + "% of its monthly budget.",
                limit ? "CRITICAL" : "WARNING",
                budget.id()));
      }
    }
    LocalDate today = LocalDate.now(ZoneOffset.UTC);
    recurring.findByUserIdOrderByNextDueDateAsc(user).stream()
        .filter(item -> item.active && !item.nextDueDate.isAfter(today.plusDays(7)))
        .limit(10)
        .forEach(
            item -> {
              boolean overdue = item.nextDueDate.isBefore(today);
              result.add(
                  new NotificationView(
                      "bill:" + item.id + ":" + item.nextDueDate,
                      "BILL",
                      overdue ? "Payment overdue" : "Payment coming up",
                      item.name + " · $" + item.amount.toPlainString() + " due " + item.nextDueDate,
                      overdue ? "CRITICAL" : "INFO",
                      item.id));
            });
    Map<UUID, ExpenseAverage> averages = new HashMap<>();
    for (ExpenseAverage item :
        transactions.expenseAverages(
            user, selected.atDay(1).minusMonths(3), selected.atDay(1).minusDays(1)))
      averages.put(item.getCategoryId(), item);
    List<LedgerTransaction> candidates =
        transactions
            .findAll(
                (root, query, cb) ->
                    cb.and(
                        cb.equal(root.get("account").get("user").get("id"), user),
                        cb.equal(root.get("type"), TransactionType.EXPENSE),
                        cb.between(root.get("date"), selected.atDay(1), selected.atEndOfMonth())),
                PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "amount", "id")))
            .getContent();
    for (LedgerTransaction item : candidates) {
      UUID categoryId = item.category == null ? null : item.category.getId();
      ExpenseAverage average = averages.get(categoryId);
      if (average == null || average.getCount() < 3) continue;
      // Compare sum/count by cross-multiplication, keeping cents exact at the alert boundary.
      if (item.amount.compareTo(new BigDecimal("100")) > 0
          && item.amount
                  .multiply(BigDecimal.valueOf(average.getCount()))
                  .compareTo(average.getTotal().multiply(new BigDecimal("2")))
              > 0)
        result.add(
            new NotificationView(
                "unusual:" + item.id,
                "UNUSUAL_SPENDING",
                "A larger expense than usual",
                item.description
                    + " · $"
                    + item.amount.toPlainString()
                    + " is above twice your recent category average.",
                "WARNING",
                item.id));
    }
    for (SavingsGoal goal : goals.findByUserIdOrderByTargetDateAsc(user)) {
      int percent =
          goal.currentAmount
              .multiply(new BigDecimal("100"))
              .divide(goal.targetAmount, 0, RoundingMode.DOWN)
              .min(new BigDecimal("100"))
              .intValue();
      int milestone =
          percent >= 100 ? 100 : percent >= 75 ? 75 : percent >= 50 ? 50 : percent >= 25 ? 25 : 0;
      if (milestone > 0)
        result.add(
            new NotificationView(
                "goal:" + goal.id + ":" + milestone,
                "GOAL_MILESTONE",
                milestone == 100 ? "Goal reached" : "Savings milestone",
                goal.name + " has reached " + milestone + "% of its target.",
                "INFO",
                goal.id));
    }
    return result.stream().limit(30).toList();
  }

  private List<CategoryAggregate> spending(UUID user, YearMonth selected) {
    return transactions.categorySpending(user, selected.atDay(1), selected.atEndOfMonth());
  }

  static BigDecimal money(BigDecimal value) {
    return value.setScale(2, RoundingMode.HALF_UP);
  }
}
