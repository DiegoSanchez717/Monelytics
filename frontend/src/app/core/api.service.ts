import { HttpClient, HttpErrorResponse, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { Account, AuditEntry, Dashboard, Goal, Page, Projection, Transaction } from './models';
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
  dashboard(): Promise<Dashboard> {
    return this.get<Dashboard>('/dashboard');
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
