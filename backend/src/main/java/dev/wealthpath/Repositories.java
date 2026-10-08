package dev.wealthpath;

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

interface AccountRepository extends JpaRepository<IraAccount, UUID> {
  @EntityGraph(attributePaths = "beneficiaries")
  List<IraAccount> findByUserIdOrderByCreatedAtAsc(UUID userId);

  Optional<IraAccount> findByIdAndUserId(UUID id, UUID userId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select a from IraAccount a where a.id=:id and a.user.id=:userId")
  Optional<IraAccount> lockOwned(@Param("id") UUID id, @Param("userId") UUID userId);
}

interface TransactionRepository
    extends JpaRepository<LedgerTransaction, UUID>, JpaSpecificationExecutor<LedgerTransaction> {
  Optional<LedgerTransaction> findByIdAndAccountUserId(UUID id, UUID userId);

  boolean existsByAccountId(UUID accountId);

  @EntityGraph(attributePaths = "account")
  List<LedgerTransaction> findByAccountUserId(UUID userId, Pageable pageable);

  @Override
  @EntityGraph(attributePaths = "account")
  Page<LedgerTransaction> findAll(
      Specification<LedgerTransaction> specification, Pageable pageable);

  @Query(
      "select t.account.id as accountId, sum(t.amount) as total from LedgerTransaction t where t.account.user.id=:userId and t.type=dev.wealthpath.TransactionType.CONTRIBUTION and t.date between :start and :end group by t.account.id")
  List<AccountContribution> contributionsByAccount(
      @Param("userId") UUID userId, @Param("start") LocalDate start, @Param("end") LocalDate end);

  @Query(
      "select coalesce(sum(case when t.type=dev.wealthpath.TransactionType.WITHDRAWAL then -t.amount else t.amount end),0) from LedgerTransaction t where t.account.user.id=:userId and t.date>:end and t.account.createdAt<:cutoff")
  BigDecimal effectAfter(
      @Param("userId") UUID userId, @Param("end") LocalDate end, @Param("cutoff") Instant cutoff);

  @Query(
      "select coalesce(sum(t.amount),0) from LedgerTransaction t where t.account.user.id=:userId and t.type=dev.wealthpath.TransactionType.CONTRIBUTION and t.date between :start and :end")
  BigDecimal contributions(
      @Param("userId") UUID userId, @Param("start") LocalDate start, @Param("end") LocalDate end);

  @Query(
      "select coalesce(sum(t.amount),0) from LedgerTransaction t where t.account.id=:accountId and t.type=dev.wealthpath.TransactionType.CONTRIBUTION and t.date between :start and :end")
  BigDecimal accountContributions(
      @Param("accountId") UUID accountId,
      @Param("start") LocalDate start,
      @Param("end") LocalDate end);
}

interface AccountContribution {
  UUID getAccountId();

  BigDecimal getTotal();
}

interface GoalRepository extends JpaRepository<RetirementGoal, UUID> {
  List<RetirementGoal> findByUserIdOrderByTargetDateAsc(UUID userId);

  Optional<RetirementGoal> findByIdAndUserId(UUID id, UUID userId);
}

interface AuditRepository extends JpaRepository<AuditEvent, UUID> {}
