import { CurrencyPipe } from '@angular/common';
import { Component, computed, input } from '@angular/core';
import { CashFlowPoint } from '../core/models';
@Component({
  selector: 'ml-cashflow-chart',
  imports: [CurrencyPipe],
  templateUrl: './cashflow-chart.component.html',
})
export class CashflowChartComponent {
  readonly points = input<CashFlowPoint[]>([]);
  readonly title = input('Income and expenses over time');
  readonly maximum = computed(() =>
    Math.max(1, ...this.points().flatMap((p) => [p.income, p.expenses])),
  );
  readonly currency = new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD' });
  readonly summary = computed(
    () =>
      `${this.title()}. ${this.points().length ? `${this.points().length} months. Total income ${this.currency.format(this.points().reduce((s, p) => s + p.income, 0))}; total expenses ${this.currency.format(this.points().reduce((s, p) => s + p.expenses, 0))}. Detailed values follow.` : 'No data available.'}`,
  );
  x(index: number): number {
    return 35 + index * (650 / Math.max(1, this.points().length));
  }
  width(): number {
    return Math.min(34, 250 / Math.max(1, this.points().length));
  }
  height(value: number): number {
    return (value / this.maximum()) * 155;
  }
  monthLabel(month: string): string {
    return /^\d{4}-\d{2}$/.test(month)
      ? new Date(`${month}-15T12:00:00`).toLocaleDateString('en-US', { month: 'short' })
      : month;
  }
}
