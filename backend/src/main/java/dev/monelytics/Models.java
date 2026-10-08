package dev.monelytics;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

enum AccountType {
  CHECKING,
  SAVINGS,
  CREDIT_CARD,
  ROTH_IRA,
  TRADITIONAL_IRA
}

enum TransactionType {
  INCOME,
  EXPENSE,
  TRANSFER,
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
@Table(name = "financial_accounts")
class FinancialAccount {
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
  FinancialAccount account;

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
  FinancialAccount account;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "destination_account_id")
  FinancialAccount destinationAccount;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "category_id")
  FinanceCategory category;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "recurring_id")
  RecurringItem recurring;

  LocalDate recurringDueDate;

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
@Table(name = "savings_goals")
class SavingsGoal {
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

  @Column(nullable = false, precision = 19, scale = 2)
  BigDecimal openingAmount;

  @Column(nullable = false)
  LocalDate targetDate;

  @Column(nullable = false, precision = 19, scale = 2)
  BigDecimal monthlyContribution;

  @Column(nullable = false, precision = 5, scale = 2)
  BigDecimal expectedReturn;

  @Version long version;
}

enum CategoryType {
  INCOME,
  EXPENSE
}

enum RecurringKind {
  BILL,
  SUBSCRIPTION
}

enum RecurringFrequency {
  MONTHLY,
  YEARLY
}

@Entity
@Table(name = "finance_categories")
class FinanceCategory {
  @Id UUID id = UUID.randomUUID();

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id")
  AppUser user;

  @Column(nullable = false, length = 80)
  String name;

  @Column(nullable = false, length = 80)
  String nameKey;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 12)
  CategoryType type;

  @Column(nullable = false, length = 7)
  String color;

  public UUID getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public String getColor() {
    return color;
  }

  public CategoryType getType() {
    return type;
  }
}

@Entity
@Table(name = "monthly_budgets")
class MonthlyBudget {
  @Id UUID id = UUID.randomUUID();

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id")
  AppUser user;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "category_id")
  FinanceCategory category;

  @Column(name = "budget_month", nullable = false, length = 7)
  String month;

  @Column(nullable = false, length = 36)
  String scopeKey;

  @Column(nullable = false, precision = 19, scale = 2)
  BigDecimal limitAmount;

  @Version long version;
}

@Entity
@Table(name = "recurring_items")
class RecurringItem {
  @Id UUID id = UUID.randomUUID();

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id")
  AppUser user;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "account_id")
  FinancialAccount account;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "category_id")
  FinanceCategory category;

  @Column(nullable = false, length = 100)
  String name;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  RecurringKind kind;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  RecurringFrequency frequency;

  @Column(nullable = false, precision = 19, scale = 2)
  BigDecimal amount;

  @Column(nullable = false)
  LocalDate nextDueDate;

  @Column(nullable = false)
  int anchorDay;

  @Column(nullable = false)
  boolean active;

  @Version long version;
}

@Entity
@Table(name = "goal_contributions")
class GoalContribution {
  @Id UUID id = UUID.randomUUID();

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "goal_id")
  SavingsGoal goal;

  @Column(nullable = false, precision = 19, scale = 2)
  BigDecimal amount;

  @Column(name = "contribution_date", nullable = false)
  LocalDate date;

  @Column(nullable = false, length = 240)
  String note;

  @Column(nullable = false)
  Instant createdAt = Instant.now();
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
