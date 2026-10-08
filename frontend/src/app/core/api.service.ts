import { HttpClient, HttpErrorResponse, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import {
  Account,
  Analytics,
  AuditEntry,
  Budget,
  Category,
  Dashboard,
  FinanceNotification,
  Goal,
  GoalContribution,
  Page,
  Projection,
  RecurringItem,
  Transaction,
} from './models';
@Injectable({ providedIn: 'root' })
export class ApiService {
  private readonly http = inject(HttpClient);
  get<T>(path: string, params?: Record<string, string | number>): Promise<T> {
    return firstValueFrom(
      this.http.get<T>(`/api${path}`, {
        params: new HttpParams({ fromObject: params ?? {} }),
      }),
    );
  }
  save<T>(path: string, value: unknown, id?: string): Promise<T> {
    return firstValueFrom(
      id ? this.http.put<T>(`/api${path}/${id}`, value) : this.http.post<T>(`/api${path}`, value),
    );
  }
  update<T>(path: string, value: unknown): Promise<T> {
    return firstValueFrom(this.http.put<T>(`/api${path}`, value));
  }
  remove(path: string, id: string): Promise<void> {
    return firstValueFrom(this.http.delete<void>(`/api${path}/${id}`));
  }
  dashboard(month?: string): Promise<Dashboard> {
    return this.get<Dashboard>('/dashboard', month ? { month } : undefined);
  }
  accounts(): Promise<Account[]> {
    return this.get<Account[]>('/accounts');
  }
  transactions(params: Record<string, string | number>): Promise<Page<Transaction>> {
    return this.get<Page<Transaction>>('/transactions', params);
  }
  goals(): Promise<Goal[]> {
    return this.get<Goal[]>('/goals');
  }
  calculate(value: unknown): Promise<Projection> {
    return this.save<Projection>('/goals/calculate', value);
  }
  categories(): Promise<Category[]> {
    return this.get<Category[]>('/categories');
  }
  budgets(month: string): Promise<Budget[]> {
    return this.get<Budget[]>('/budgets', { month });
  }
  recurring(): Promise<RecurringItem[]> {
    return this.get<RecurringItem[]>('/recurring');
  }
  analytics(month: string): Promise<Analytics> {
    return this.get<Analytics>('/analytics', { month });
  }
  notifications(month: string): Promise<FinanceNotification[]> {
    return this.get<FinanceNotification[]>('/notifications', { month });
  }
  contributions(goalId: string): Promise<GoalContribution[]> {
    return this.get<GoalContribution[]>(`/goals/${goalId}/contributions`);
  }
  contribute(goalId: string, value: { amount: number; date: string; note: string }): Promise<Goal> {
    return this.save<Goal>(`/goals/${goalId}/contributions`, value);
  }
  removeContribution(goalId: string, id: string): Promise<Goal> {
    return firstValueFrom(this.http.delete<Goal>(`/api/goals/${goalId}/contributions/${id}`));
  }
  payRecurring(item: RecurringItem, date: string): Promise<Transaction> {
    return this.save<Transaction>(`/recurring/${item.id}/pay`, { dueDate: item.nextDueDate, date });
  }
  exportTransactions(params: Record<string, string | number>): Promise<Blob> {
    return firstValueFrom(
      this.http.get('/api/transactions/export.csv', {
        params: new HttpParams({ fromObject: params }),
        responseType: 'blob',
      }),
    );
  }
  audit(page: number): Promise<Page<AuditEntry>> {
    return this.get<Page<AuditEntry>>('/audit', {
      page,
      size: 20,
    });
  }
}
export function errorMessage(error: unknown): string {
  if (error instanceof HttpErrorResponse) {
    if (error.status === 0)
      return 'We could not reach Monelytics. Please check your connection and try again.';
    if (error.status === 401)
      return error.error?.detail ?? 'Your session has expired. Please sign in again.';
    const fields: unknown = error.error?.errors;
    if (fields && typeof fields === 'object' && !Array.isArray(fields)) {
      const messages = Object.entries(fields)
        .filter((entry): entry is [string, string] => typeof entry[1] === 'string')
        .map(([field, message]) => `${field.replace(/([A-Z])/g, ' $1').toLowerCase()}: ${message}`);
      if (messages.length) return `Please review these values: ${messages.join('; ')}.`;
    }
    return (
      error.error?.detail ??
      error.error?.message ??
      'We could not complete this request. Please try again.'
    );
  }
  return 'Something went wrong. Please try again.';
}
