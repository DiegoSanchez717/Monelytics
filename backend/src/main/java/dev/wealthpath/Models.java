package dev.wealthpath;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

enum AccountType {
  ROTH_IRA,
  TRADITIONAL_IRA
}

enum TransactionType {
  CONTRIBUTION,
  WITHDRAWAL,
  ROLLOVER,
  RETURN
}

enum Role {
  USER,
  ADMIN
}

@Entity
@Table(name = "app_users")
class AppUser {
  @Id UUID id = UUID.randomUUID();

  @Column(nullable = false, length = 80)
  String firstName;

  @Column(nullable = false, length = 80)
  String lastName;

  @Column(nullable = false, unique = true, length = 254)
  String email;

  @Column(nullable = false, length = 100)
  String passwordHash;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 12)
  Role role = Role.USER;

  @Column(nullable = false)
  boolean mfaEnabled;

  @Column(length = 512)
  String mfaSecret;

  @Column(nullable = false)
  long lastMfaStep = -1;

  @Column(nullable = false)
  int failedLogins;

  Instant lockedUntil;

  @Column(nullable = false)
  Instant createdAt = Instant.now();

  @Version long version;
}

@Entity
@Table(name = "ira_accounts")
class IraAccount {
  public UUID getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  @Id UUID id = UUID.randomUUID();

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id")
  AppUser user;

  @Column(nullable = false, length = 100)
  String name;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 24)
  AccountType type;

  @Column(nullable = false, precision = 19, scale = 2)
  BigDecimal balance = BigDecimal.ZERO;

  @Column(nullable = false, precision = 19, scale = 2)
  BigDecimal openingBalance = BigDecimal.ZERO;

  @Column(nullable = false)
  Instant createdAt = Instant.now();

  @Version long version;

  @OneToMany(mappedBy = "account", cascade = CascadeType.ALL, orphanRemoval = true)
  List<Beneficiary> beneficiaries = new ArrayList<>();
}

@Entity
@Table(name = "beneficiaries")
class Beneficiary {
  @Id UUID id = UUID.randomUUID();

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "account_id")
  IraAccount account;

  @Column(nullable = false, length = 100)
  String name;

  @Column(nullable = false, length = 50)
  String relationship;

  @Column(nullable = false, precision = 5, scale = 2)
  BigDecimal percentage;
}

@Entity
@Table(name = "ledger_transactions")
class LedgerTransaction {
  @Id UUID id = UUID.randomUUID();

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "account_id")
  IraAccount account;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 24)
  TransactionType type;

  @Column(nullable = false, precision = 19, scale = 2)
  BigDecimal amount;

  @Column(nullable = false, length = 240)
  String description;

  @Column(name = "transaction_date", nullable = false)
  LocalDate date;

  @Column(nullable = false)
  Instant createdAt = Instant.now();

  @Version long version;
}

@Entity
@Table(name = "retirement_goals")
class RetirementGoal {
  @Id UUID id = UUID.randomUUID();

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id")
  AppUser user;

  @Column(nullable = false, length = 100)
  String name;

  @Column(nullable = false, precision = 19, scale = 2)
  BigDecimal targetAmount;

  @Column(nullable = false, precision = 19, scale = 2)
  BigDecimal currentAmount;

  @Column(nullable = false)
  LocalDate targetDate;

  @Column(nullable = false, precision = 19, scale = 2)
  BigDecimal monthlyContribution;

  @Column(nullable = false, precision = 5, scale = 2)
  BigDecimal expectedReturn;

  @Version long version;
}

@Entity
@Table(name = "audit_events")
class AuditEvent {
  @Id UUID id = UUID.randomUUID();
  UUID actorId;

  @Column(nullable = false, length = 50)
  String action;

  UUID resourceId;

  @Column(nullable = false, length = 240)
  String detail;

  @Column(nullable = false)
  Instant occurredAt = Instant.now();
}
