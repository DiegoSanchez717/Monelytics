package dev.monelytics;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

public final class FinanceDtos {
  private FinanceDtos() {}

  public record CategoryInput(
      @NotBlank @Size(max = 80) String name,
      @NotNull CategoryType type,
      @NotBlank @Pattern(regexp = "#[0-9a-fA-F]{6}") String color) {}

  public record CategoryView(UUID id, String name, CategoryType type, String color) {
    static CategoryView of(FinanceCategory category) {
      return new CategoryView(category.id, category.name, category.type, category.color);
    }
  }

  public record BudgetInput(
      UUID categoryId,
      @NotBlank @Pattern(regexp = "[0-9]{4}-(0[1-9]|1[0-2])") String month,
      @NotNull
          @DecimalMin(value = "0", inclusive = false)
          @DecimalMax("999999999999.99")
          @Digits(integer = 12, fraction = 2)
          BigDecimal limitAmount) {}

  public record BudgetView(
      UUID id,
      UUID categoryId,
      String categoryName,
      String month,
      BigDecimal limitAmount,
      BigDecimal spentAmount,
      BigDecimal remainingAmount,
      BigDecimal percentage,
      String status) {}

  public record RecurringInput(
      @NotBlank @Size(max = 100) String name,
      @NotNull RecurringKind kind,
      @NotNull UUID accountId,
      @NotNull UUID categoryId,
      @NotNull
          @DecimalMin(value = "0", inclusive = false)
          @DecimalMax("999999999999.99")
          @Digits(integer = 12, fraction = 2)
          BigDecimal amount,
      @NotNull RecurringFrequency frequency,
      @NotNull LocalDate nextDueDate,
      boolean active) {}

  public record RecurringView(
      UUID id,
      String name,
      RecurringKind kind,
      UUID accountId,
      String accountName,
      UUID categoryId,
      String categoryName,
      BigDecimal amount,
      RecurringFrequency frequency,
      LocalDate nextDueDate,
      boolean active) {
    static RecurringView of(RecurringItem item) {
      return new RecurringView(
          item.id,
          item.name,
          item.kind,
          item.account.getId(),
          item.account.getName(),
          item.category.getId(),
          item.category.getName(),
          item.amount,
          item.frequency,
          item.nextDueDate,
          item.active);
    }
  }

  public record RecurringPayment(
      @NotNull LocalDate dueDate, @NotNull @PastOrPresent LocalDate date) {}

  public record GoalContributionInput(
      @NotNull
          @DecimalMin(value = "0", inclusive = false)
          @DecimalMax("999999999999.99")
          @Digits(integer = 12, fraction = 2)
          BigDecimal amount,
      @NotNull @PastOrPresent LocalDate date,
      @Size(max = 240) String note) {}

  public record GoalContributionView(UUID id, BigDecimal amount, LocalDate date, String note) {
    static GoalContributionView of(GoalContribution contribution) {
      return new GoalContributionView(
          contribution.id, contribution.amount, contribution.date, contribution.note);
    }
  }

  public record MonthlyTrend(
      String month, BigDecimal income, BigDecimal expenses, BigDecimal cashFlow) {}

  public record CategorySpending(UUID categoryId, String name, String color, BigDecimal value) {}

  public record NotificationView(
      String id, String type, String title, String message, String severity, UUID resourceId) {}

  public record AnalyticsView(
      String month,
      BigDecimal income,
      BigDecimal expenses,
      BigDecimal cashFlow,
      BigDecimal savingsRate,
      List<MonthlyTrend> spendingTrends,
      List<CategorySpending> categoryBreakdown) {}
}
