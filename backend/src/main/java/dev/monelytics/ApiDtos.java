package dev.monelytics;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

public final class ApiDtos {
  private ApiDtos() {}

  public record UserView(
      UUID id, String firstName, String lastName, String email, String role, boolean mfaEnabled) {
    static UserView of(AppUser u) {
      return new UserView(u.id, u.firstName, u.lastName, u.email, u.role.name(), u.mfaEnabled);
    }
  }

  public record Register(
      @NotBlank @Size(max = 80) String firstName,
      @NotBlank @Size(max = 80) String lastName,
      @NotBlank @Email @Size(max = 254) String email,
      @NotBlank
          @Size(min = 12, max = 72)
          @Pattern(
              regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*[0-9])(?=.*[^A-Za-z0-9]).+$",
              message = "must contain upper and lower case letters, a number, and a symbol")
          String password) {
    @Override
    public String toString() {
      return "Register[credentials redacted]";
    }
  }

  public record Login(
      @NotBlank @Email @Size(max = 254) String email,
      @NotBlank @Size(max = 72) String password,
      @Pattern(regexp = "[0-9]{6}") String code) {
    @Override
    public String toString() {
      return "Login[credentials redacted]";
    }
  }

  public record Password(@NotBlank @Size(max = 72) String password) {
    @Override
    public String toString() {
      return "Password[redacted]";
    }
  }

  public record MfaConfirm(
      @NotBlank @Size(max = 72) String password,
      @NotBlank @Pattern(regexp = "[0-9]{6}") String code) {
    @Override
    public String toString() {
      return "MfaConfirm[credentials redacted]";
    }
  }

  public record MfaSetup(String secret, String otpAuthUri) {
    @Override
    public String toString() {
      return "MfaSetup[secret redacted]";
    }
  }

  public record Settings(
      @NotBlank @Size(max = 80) String firstName, @NotBlank @Size(max = 80) String lastName) {}

  public record AccountCreate(
      @NotBlank @Size(max = 100) String name,
      @NotNull AccountType type,
      @NotNull
          @DecimalMin("-999999999999.99")
          @DecimalMax("999999999999.99")
          @Digits(integer = 12, fraction = 2)
          BigDecimal openingBalance) {}

  public record AccountUpdate(@NotBlank @Size(max = 100) String name, @NotNull AccountType type) {}

  public record BeneficiaryInput(
      @NotBlank @Size(max = 100) String name,
      @NotBlank @Size(max = 50) String relationship,
      @NotNull
          @DecimalMin(value = "0", inclusive = false)
          @DecimalMax("100")
          @Digits(integer = 3, fraction = 2)
          BigDecimal percentage) {}

  public record Beneficiaries(
      @NotNull @Size(max = 12) List<@NotNull @Valid BeneficiaryInput> beneficiaries) {}

  public record BeneficiaryView(UUID id, String name, String relationship, BigDecimal percentage) {}

  public record AccountView(
      UUID id,
      String name,
      AccountType type,
      BigDecimal balance,
      BigDecimal annualContributions,
      List<BeneficiaryView> beneficiaries) {}

  public record TransactionInput(
      @NotNull UUID accountId,
      @NotNull TransactionType type,
      @NotNull
          @DecimalMin(value = "0", inclusive = false)
          @DecimalMax("999999999999.99")
          @Digits(integer = 12, fraction = 2)
          BigDecimal amount,
      @NotBlank @Size(max = 240) String description,
      @NotNull @PastOrPresent LocalDate date,
      UUID categoryId,
      UUID destinationAccountId) {
    public TransactionInput(
        UUID accountId,
        TransactionType type,
        BigDecimal amount,
        String description,
        LocalDate date) {
      this(accountId, type, amount, description, date, null, null);
    }
  }

  public record TransactionView(
      UUID id,
      UUID accountId,
      String accountName,
      TransactionType type,
      BigDecimal amount,
      String description,
      LocalDate date,
      UUID categoryId,
      String categoryName,
      UUID destinationAccountId,
      String destinationAccountName) {
    static TransactionView of(LedgerTransaction t) {
      return new TransactionView(
          t.id,
          t.account.getId(),
          t.account.getName(),
          t.type,
          t.amount,
          t.description,
          t.date,
          t.category == null ? null : t.category.getId(),
          t.category == null ? null : t.category.getName(),
          t.destinationAccount == null ? null : t.destinationAccount.getId(),
          t.destinationAccount == null ? null : t.destinationAccount.getName());
    }
  }

  public record GoalInput(
      @NotBlank @Size(max = 100) String name,
      @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 12, fraction = 2)
          BigDecimal targetAmount,
      @NotNull @DecimalMin("0") @Digits(integer = 12, fraction = 2) BigDecimal currentAmount,
      @NotNull @Future LocalDate targetDate,
      @NotNull @DecimalMin("0") @Digits(integer = 12, fraction = 2) BigDecimal monthlyContribution,
      @NotNull @DecimalMin("0") @DecimalMax("20") @Digits(integer = 2, fraction = 2)
          BigDecimal expectedReturn) {}

  public record GoalView(
      UUID id,
      String name,
      BigDecimal targetAmount,
      BigDecimal currentAmount,
      LocalDate targetDate,
      BigDecimal monthlyContribution,
      BigDecimal expectedReturn) {
    static GoalView of(SavingsGoal g) {
      return new GoalView(
          g.id,
          g.name,
          g.targetAmount,
          g.currentAmount,
          g.targetDate,
          g.monthlyContribution,
          g.expectedReturn);
    }
  }

  public record Calculate(
      @Min(18) @Max(90) int currentAge,
      @Min(19) @Max(100) int retirementAge,
      @NotNull @DecimalMin("0") @DecimalMax("999999999999.99") BigDecimal currentSavings,
      @NotNull @DecimalMin("0") @DecimalMax("1000000") BigDecimal monthlyContribution,
      @NotNull @DecimalMin("0") @DecimalMax("20") BigDecimal annualReturn,
      @NotNull @DecimalMin(value = "0", inclusive = false) @DecimalMax("999999999999.99")
          BigDecimal targetAmount) {}

  public record YearProjection(int age, BigDecimal balance, BigDecimal contributions) {}

  public record Calculation(
      BigDecimal projectedBalance,
      BigDecimal totalContributions,
      BigDecimal investmentGrowth,
      BigDecimal gap,
      BigDecimal monthlyNeeded,
      List<YearProjection> yearlyProjection) {}

  public record BalancePoint(String month, BigDecimal balance) {}

  public record Allocation(String name, BigDecimal value) {}

  public record Dashboard(
      BigDecimal totalBalance,
      BigDecimal annualContributions,
      BigDecimal contributionLimit,
      BigDecimal goalProgress,
      BigDecimal monthlyChange,
      List<AccountView> accounts,
      List<TransactionView> recentTransactions,
      List<BalancePoint> balanceHistory,
      List<Allocation> allocation,
      String month,
      BigDecimal income,
      BigDecimal expenses,
      BigDecimal cashFlow,
      BigDecimal savingsRate,
      List<FinanceDtos.BudgetView> budgets,
      List<GoalView> goals,
      List<FinanceDtos.MonthlyTrend> spendingTrends,
      List<FinanceDtos.CategorySpending> categoryBreakdown,
      List<FinanceDtos.NotificationView> notifications) {}

  public record PageView<T>(
      List<T> content, long totalElements, int totalPages, int number, int size) {}

  public record AuditView(
      UUID id, UUID actorId, String action, UUID resourceId, String detail, Instant occurredAt) {}
}
