package dev.monelytics;

import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

interface UserRepository extends JpaRepository<AppUser, UUID> {
  Optional<AppUser> findByEmail(String email);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select u from AppUser u where u.email=:email")
  Optional<AppUser> lockByEmail(@Param("email") String email);

  boolean existsByEmail(String email);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select u from AppUser u where u.id=:id")
  Optional<AppUser> lockById(@Param("id") UUID id);
}

interface AccountRepository extends JpaRepository<FinancialAccount, UUID> {
  @EntityGraph(attributePaths = "beneficiaries")
  List<FinancialAccount> findByUserIdOrderByCreatedAtAsc(UUID userId);

  Optional<FinancialAccount> findByIdAndUserId(UUID id, UUID userId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select a from FinancialAccount a where a.id=:id and a.user.id=:userId")
  Optional<FinancialAccount> lockOwned(@Param("id") UUID id, @Param("userId") UUID userId);
}

interface TransactionRepository
    extends JpaRepository<LedgerTransaction, UUID>, JpaSpecificationExecutor<LedgerTransaction> {
  Optional<LedgerTransaction> findByIdAndAccountUserId(UUID id, UUID userId);

  boolean existsByAccountId(UUID accountId);

  boolean existsByAccountIdOrDestinationAccountId(UUID accountId, UUID destinationAccountId);

  boolean existsByCategoryId(UUID categoryId);

  @EntityGraph(attributePaths = {"account", "category", "destinationAccount"})
  List<LedgerTransaction> findByAccountUserId(UUID userId, Pageable pageable);

  @Override
  @EntityGraph(attributePaths = {"account", "category", "destinationAccount"})
  Page<LedgerTransaction> findAll(
      Specification<LedgerTransaction> specification, Pageable pageable);

  @Query(
      "select t.account.id as accountId, sum(t.amount) as total from LedgerTransaction t where t.account.user.id=:userId and t.type=dev.monelytics.TransactionType.CONTRIBUTION and t.date between :start and :end group by t.account.id")
  List<AccountContribution> contributionsByAccount(
      @Param("userId") UUID userId, @Param("start") LocalDate start, @Param("end") LocalDate end);

  @Query(
      "select coalesce(sum(case when t.type in (dev.monelytics.TransactionType.WITHDRAWAL,dev.monelytics.TransactionType.EXPENSE,dev.monelytics.TransactionType.TRANSFER) then -t.amount else t.amount end),0) from LedgerTransaction t where t.account.user.id=:userId and t.date>:end and t.account.createdAt<:cutoff")
  BigDecimal effectAfter(
      @Param("userId") UUID userId, @Param("end") LocalDate end, @Param("cutoff") Instant cutoff);

  @Query(
      "select coalesce(sum(t.amount),0) from LedgerTransaction t where t.account.user.id=:userId and t.type=dev.monelytics.TransactionType.CONTRIBUTION and t.date between :start and :end")
  BigDecimal contributions(
      @Param("userId") UUID userId, @Param("start") LocalDate start, @Param("end") LocalDate end);

  @Query(
      "select coalesce(sum(t.amount),0) from LedgerTransaction t where t.account.id=:accountId and t.type=dev.monelytics.TransactionType.CONTRIBUTION and t.date between :start and :end")
  BigDecimal accountContributions(
      @Param("accountId") UUID accountId,
      @Param("start") LocalDate start,
      @Param("end") LocalDate end);

  @Query(
      "select coalesce(sum(t.amount),0) from LedgerTransaction t where t.account.user.id=:userId and t.type=dev.monelytics.TransactionType.TRANSFER and t.date>:end and t.destinationAccount.createdAt<:cutoff")
  BigDecimal destinationEffectAfter(
      @Param("userId") UUID userId, @Param("end") LocalDate end, @Param("cutoff") Instant cutoff);

  @Query(
      "select year(t.date) as yearNumber, month(t.date) as monthNumber, sum(case when t.type=dev.monelytics.TransactionType.INCOME then t.amount else 0 end) as income, sum(case when t.type=dev.monelytics.TransactionType.EXPENSE then t.amount else 0 end) as expenses from LedgerTransaction t where t.account.user.id=:userId and t.date between :start and :end and t.type in (dev.monelytics.TransactionType.INCOME,dev.monelytics.TransactionType.EXPENSE) group by year(t.date),month(t.date)")
  List<MonthAggregate> monthlyTotals(
      @Param("userId") UUID userId, @Param("start") LocalDate start, @Param("end") LocalDate end);

  @Query(
      "select c.id as categoryId, coalesce(c.name,'Uncategorized') as name, coalesce(c.color,'#94A3B8') as color, sum(t.amount) as total from LedgerTransaction t left join t.category c where t.account.user.id=:userId and t.type=dev.monelytics.TransactionType.EXPENSE and t.date between :start and :end group by c.id,c.name,c.color order by sum(t.amount) desc")
  List<CategoryAggregate> categorySpending(
      @Param("userId") UUID userId, @Param("start") LocalDate start, @Param("end") LocalDate end);

  @Query(
      "select t.category.id as categoryId, sum(t.amount) as total, count(t) as count from LedgerTransaction t where t.account.user.id=:userId and t.type=dev.monelytics.TransactionType.EXPENSE and t.date between :start and :end group by t.category.id")
  List<ExpenseAverage> expenseAverages(
      @Param("userId") UUID userId, @Param("start") LocalDate start, @Param("end") LocalDate end);

  @EntityGraph(attributePaths = {"account", "category", "destinationAccount"})
  Optional<LedgerTransaction> findByRecurringIdAndRecurringDueDate(
      UUID recurringId, LocalDate dueDate);
}

interface MonthAggregate {
  int getYearNumber();

  int getMonthNumber();

  BigDecimal getIncome();

  BigDecimal getExpenses();
}

interface CategoryAggregate {
  UUID getCategoryId();

  String getName();

  String getColor();

  BigDecimal getTotal();
}

interface ExpenseAverage {
  UUID getCategoryId();

  BigDecimal getTotal();

  long getCount();
}

interface AccountContribution {
  UUID getAccountId();

  BigDecimal getTotal();
}

interface GoalRepository extends JpaRepository<SavingsGoal, UUID> {
  List<SavingsGoal> findByUserIdOrderByTargetDateAsc(UUID userId);

  Optional<SavingsGoal> findByIdAndUserId(UUID id, UUID userId);
}

interface CategoryRepository extends JpaRepository<FinanceCategory, UUID> {
  List<FinanceCategory> findByUserIdOrderByNameAsc(UUID userId);

  Optional<FinanceCategory> findByIdAndUserId(UUID id, UUID userId);

  boolean existsByUserIdAndNameKey(UUID userId, String nameKey);
}

interface BudgetRepository extends JpaRepository<MonthlyBudget, UUID> {
  @EntityGraph(attributePaths = "category")
  List<MonthlyBudget> findByUserIdAndMonthOrderByScopeKeyAsc(UUID userId, String month);

  Optional<MonthlyBudget> findByIdAndUserId(UUID id, UUID userId);

  boolean existsByUserIdAndMonthAndScopeKey(UUID userId, String month, String scopeKey);

  boolean existsByCategoryId(UUID categoryId);
}

interface RecurringRepository extends JpaRepository<RecurringItem, UUID> {
  @EntityGraph(attributePaths = {"account", "category"})
  List<RecurringItem> findByUserIdOrderByNextDueDateAsc(UUID userId);

  @EntityGraph(attributePaths = {"account", "category"})
  Optional<RecurringItem> findByIdAndUserId(UUID id, UUID userId);

  boolean existsByCategoryId(UUID categoryId);

  boolean existsByAccountId(UUID accountId);
}

interface GoalContributionRepository extends JpaRepository<GoalContribution, UUID> {
  List<GoalContribution> findByGoalIdAndGoalUserIdOrderByDateDescCreatedAtDesc(
      UUID goalId, UUID userId);

  Optional<GoalContribution> findByIdAndGoalIdAndGoalUserId(UUID id, UUID goalId, UUID userId);

  @Query("select coalesce(sum(c.amount),0) from GoalContribution c where c.goal.id=:goalId")
  BigDecimal contributed(@Param("goalId") UUID goalId);
}

interface AuditRepository extends JpaRepository<AuditEvent, UUID> {}
