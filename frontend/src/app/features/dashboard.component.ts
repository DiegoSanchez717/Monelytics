import { CurrencyPipe, DatePipe, DecimalPipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ApiService, errorMessage } from '../core/api.service';
import { AuthService } from '../core/auth.service';
import { Dashboard, Goal } from '../core/models';
import { ChartComponent } from '../shared/chart.component';
import { IconComponent } from '../shared/icon.component';
import { StateComponent } from '../shared/state.component';
@Component({
  selector: 'wp-dashboard',
  imports: [
    CurrencyPipe,
    DatePipe,
    DecimalPipe,
    RouterLink,
    ChartComponent,
    IconComponent,
    StateComponent,
  ],
  templateUrl: './dashboard.component.html',
})
export class DashboardComponent {
  private readonly api = inject(ApiService);
  readonly auth = inject(AuthService);
  readonly data = signal<Dashboard | null>(null);
  readonly goals = signal<Goal[]>([]);
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
      const [data, goals] = await Promise.all([this.api.dashboard(), this.api.goals()]);
      this.data.set(data);
      this.goals.set(goals);
    } catch (error) {
      this.error.set(errorMessage(error));
    } finally {
      this.loading.set(false);
    }
  }
  historyValues(): number[] {
    return this.data()?.balanceHistory.map((i) => i.balance) ?? [];
  }
  historyLabels(): string[] {
    return this.data()?.balanceHistory.map((i) => i.month) ?? [];
  }
  remaining(): number {
    return Math.max(
      0,
      (this.data()?.contributionLimit ?? 0) - (this.data()?.annualContributions ?? 0),
    );
  }
  contributionPercent(): number {
    const d = this.data();
    return d && d.contributionLimit
      ? Math.min(100, (d.annualContributions / d.contributionLimit) * 100)
      : 0;
  }
  goalPercent(goal: Goal): number {
    return Math.min(100, (goal.currentAmount / goal.targetAmount) * 100);
  }
  transactionLabel(type: string): string {
    return (
      {
        CONTRIBUTION: 'Contribution',
        WITHDRAWAL: 'Withdrawal',
        ROLLOVER: 'Rollover',
        RETURN: 'Investment return',
      }[type] ?? type
    );
  }
}
