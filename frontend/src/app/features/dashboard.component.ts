import { CurrencyPipe, DatePipe, DecimalPipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ApiService, errorMessage } from '../core/api.service';
import { AuthService } from '../core/auth.service';
import {
  accountLabel,
  currentMonth,
  isOutflow,
  monthLabel,
  transactionLabel,
} from '../core/finance.utils';
import { Budget, Dashboard, Goal, RecurringItem } from '../core/models';
import { ChartComponent } from '../shared/chart.component';
import { CashflowChartComponent } from '../shared/cashflow-chart.component';
import { CategoryBreakdownComponent } from '../shared/category-breakdown.component';
import { IconComponent } from '../shared/icon.component';
import { StateComponent } from '../shared/state.component';
@Component({
  selector: 'ml-dashboard',
  imports: [
    CurrencyPipe,
    DatePipe,
    DecimalPipe,
    RouterLink,
    ChartComponent,
    CashflowChartComponent,
    CategoryBreakdownComponent,
    IconComponent,
    StateComponent,
  ],
  templateUrl: './dashboard.component.html',
})
export class DashboardComponent {
  private readonly api = inject(ApiService);
  readonly auth = inject(AuthService);
  readonly data = signal<Dashboard | null>(null);
  readonly bills = signal<RecurringItem[]>([]);
  readonly month = signal(currentMonth());
  readonly monthLabel = monthLabel;
  readonly accountLabel = accountLabel;
  readonly transactionLabel = transactionLabel;
  readonly isOutflow = isOutflow;
  readonly loading = signal(true);
  readonly error = signal('');
  readonly today = new Date();
  readonly greeting =
    this.today.getHours() < 12
      ? 'Good morning'
      : this.today.getHours() < 18
        ? 'Good afternoon'
        : 'Good evening';
  constructor() {
    void this.load();
  }
  async load(): Promise<void> {
    this.loading.set(true);
    this.error.set('');
    try {
      const [data, bills] = await Promise.all([
        this.api.dashboard(this.month()),
        this.api.recurring(),
      ]);
      this.data.set(data);
      this.bills.set(
        bills
          .filter((i) => i.active)
          .sort((a, b) => a.nextDueDate.localeCompare(b.nextDueDate))
          .slice(0, 3),
      );
    } catch (error) {
      this.error.set(errorMessage(error));
    } finally {
      this.loading.set(false);
    }
  }
  changeMonth(event: Event): void {
    const value = (event.target as HTMLInputElement).value;
    if (value) {
      this.month.set(value);
      void this.load();
    }
  }
  historyValues(): number[] {
    return this.data()?.balanceHistory.map((i) => i.balance) ?? [];
  }
  historyLabels(): string[] {
    return this.data()?.balanceHistory.map((i) => i.month) ?? [];
  }
  goalPercent(goal: Goal): number {
    return Math.min(100, (goal.currentAmount / goal.targetAmount) * 100);
  }
  budgetPercent(budget: Budget): number {
    return Math.max(0, Math.min(100, budget.percentage));
  }
}
