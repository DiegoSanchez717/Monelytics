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
  type: 'ROTH_IRA' | 'TRADITIONAL_IRA';
  balance: number;
  annualContributions: number;
  beneficiaries: Beneficiary[];
}
export type TransactionType = 'CONTRIBUTION' | 'WITHDRAWAL' | 'ROLLOVER' | 'RETURN';
export interface Transaction {
  id: string;
  accountId: string;
  accountName: string;
  type: TransactionType;
  amount: number;
  description: string;
  date: string;
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
  totalBalance: number;
  annualContributions: number;
  contributionLimit: number;
  goalProgress: number;
  monthlyChange: number;
  accounts: Account[];
  recentTransactions: Transaction[];
  balanceHistory: { month: string; balance: number }[];
  allocation: { name: string; value: number }[];
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
