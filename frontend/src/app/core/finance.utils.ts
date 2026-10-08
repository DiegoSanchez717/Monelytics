import { AccountType, TransactionType } from './models';
export function accountLabel(type: AccountType): string {
  return {
    CHECKING: 'Checking',
    SAVINGS: 'Savings',
    CREDIT_CARD: 'Credit card',
    ROTH_IRA: 'Roth IRA',
    TRADITIONAL_IRA: 'Traditional IRA',
  }[type];
}
export function transactionLabel(type: TransactionType): string {
  return {
    INCOME: 'Income',
    EXPENSE: 'Expense',
    TRANSFER: 'Transfer',
    CONTRIBUTION: 'Contribution',
    WITHDRAWAL: 'Withdrawal',
    ROLLOVER: 'Rollover',
    RETURN: 'Investment return',
  }[type];
}
export function isOutflow(type: TransactionType): boolean {
  return ['EXPENSE', 'WITHDRAWAL', 'TRANSFER'].includes(type);
}
export function localDate(): string {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}
export function currentMonth(): string {
  return localDate().slice(0, 7);
}
export function monthLabel(month: string): string {
  return new Date(`${month}-15T12:00:00`).toLocaleDateString('en-US', {
    month: 'long',
    year: 'numeric',
  });
}
