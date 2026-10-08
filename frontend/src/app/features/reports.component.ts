import { CurrencyPipe, DecimalPipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ApiService, errorMessage } from '../core/api.service';
import { currentMonth, monthLabel } from '../core/finance.utils';
import { Analytics } from '../core/models';
import { CashflowChartComponent } from '../shared/cashflow-chart.component';
import { CategoryBreakdownComponent } from '../shared/category-breakdown.component';
import { IconComponent } from '../shared/icon.component';
import { StateComponent } from '../shared/state.component';
@Component({
  selector: 'ml-reports',
  imports: [
    CurrencyPipe,
    DecimalPipe,
    RouterLink,
    CashflowChartComponent,
    CategoryBreakdownComponent,
    IconComponent,
    StateComponent,
  ],
  templateUrl: './reports.component.html',
})
export class ReportsComponent {
  private readonly api = inject(ApiService);
  readonly month = signal(currentMonth());
  readonly monthLabel = monthLabel;
  readonly data = signal<Analytics | null>(null);
  readonly loading = signal(true);
  readonly error = signal('');
  constructor() {
    void this.load();
  }
  async load(): Promise<void> {
    this.loading.set(true);
    this.error.set('');
    try {
      this.data.set(await this.api.analytics(this.month()));
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
}
