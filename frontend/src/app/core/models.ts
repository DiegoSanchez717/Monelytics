export interface User {
  id: string;
  firstName: string;
  lastName: string;
  email: string;
  role: 'USER' | 'ADMIN';
  mfaEnabled: boolean;
}
export interface Beneficiary {
  id?: string;
  name: string;
  relationship: string;
  percentage: number;
}
export interface Account {
  id: string;
  name: string;
  type: AccountType;
  balance: number;
  annualContributions: number;
  beneficiaries: Beneficiary[];
}
export type AccountType = 'CHECKING' | 'SAVINGS' | 'CREDIT_CARD' | 'ROTH_IRA' | 'TRADITIONAL_IRA';
export type TransactionType =
  | 'INCOME'
  | 'EXPENSE'
  | 'TRANSFER'
  | 'CONTRIBUTION'
  | 'WITHDRAWAL'
  | 'ROLLOVER'
  | 'RETURN';
export interface Transaction {
  id: string;
  accountId: string;
  accountName: string;
  type: TransactionType;
  amount: number;
  description: string;
  date: string;
  categoryId: string | null;
  categoryName: string | null;
  destinationAccountId: string | null;
  destinationAccountName: string | null;
}
export interface Goal {
  id: string;
  name: string;
  targetAmount: number;
  currentAmount: number;
  targetDate: string;
  monthlyContribution: number;
  expectedReturn: number;
}
export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}
export interface Dashboard {
  month: string;
  totalBalance: number;
  annualContributions: number;
  contributionLimit: number;
  goalProgress: number;
  monthlyChange: number;
  accounts: Account[];
  recentTransactions: Transaction[];
  balanceHistory: { month: string; balance: number }[];
  allocation: { name: string; value: number }[];
  income: number;
  expenses: number;
  cashFlow: number;
  savingsRate: number;
  budgets: Budget[];
  goals: Goal[];
  spendingTrends: CashFlowPoint[];
  categoryBreakdown: CategoryTotal[];
  notifications: FinanceNotification[];
}
export interface Category {
  id: string;
  name: string;
  type: 'INCOME' | 'EXPENSE';
  color: string;
}
export interface Budget {
  id: string;
  categoryId: string | null;
  categoryName: string;
  month: string;
  limitAmount: number;
  spentAmount: number;
  remainingAmount: number;
  percentage: number;
  status: 'ON_TRACK' | 'NEAR_LIMIT' | 'OVER_BUDGET';
}
export interface RecurringItem {
  id: string;
  name: string;
  kind: 'BILL' | 'SUBSCRIPTION';
  amount: number;
  accountId: string;
  accountName: string;
  categoryId: string;
  categoryName: string;
  frequency: 'MONTHLY' | 'YEARLY';
  nextDueDate: string;
  active: boolean;
}
export interface GoalContribution {
  id: string;
  amount: number;
  date: string;
  note: string;
}
export interface CashFlowPoint {
  month: string;
  income: number;
  expenses: number;
  cashFlow: number;
}
export interface CategoryTotal {
  categoryId: string | null;
  name: string;
  color: string;
  value: number;
}
export interface Analytics {
  month: string;
  income: number;
  expenses: number;
  cashFlow: number;
  savingsRate: number;
  spendingTrends: CashFlowPoint[];
  categoryBreakdown: CategoryTotal[];
}
export interface FinanceNotification {
  id: string;
  type: 'BUDGET' | 'BILL' | 'UNUSUAL_SPENDING' | 'GOAL_MILESTONE';
  title: string;
  message: string;
  severity: 'INFO' | 'WARNING' | 'CRITICAL';
  resourceId: string;
}
export interface Projection {
  projectedBalance: number;
  totalContributions: number;
  investmentGrowth: number;
  gap: number;
  monthlyNeeded: number;
  yearlyProjection: { age: number; balance: number; contributions: number }[];
}
export interface AuditEntry {
  id: string;
  actorId: string;
  action: string;
  resourceId: string;
  detail: string;
  occurredAt: string;
}
export interface AssistantQuestion {
  question: string;
  purchaseAmount?: number;
  categoryId?: string;
  month?: string;
}
export interface AssistantReply {
  answer: string;
  provider: 'MOCK' | 'LOCAL';
  decision:
    | 'NOT_REQUESTED'
    | 'LIKELY_AFFORDABLE'
    | 'CAUTION'
    | 'NOT_AFFORDABLE'
    | 'INSUFFICIENT_DATA';
  factors: { label: string; value: number; explanation: string }[];
  recommendations: string[];
  disclaimer: string;
  readOnly: true;
  asOf: string;
}
