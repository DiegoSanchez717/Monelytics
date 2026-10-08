import { TestBed } from '@angular/core/testing';
import { CashflowChartComponent } from './cashflow-chart.component';

describe('cashflow chart accessibility and scale', () => {
  beforeEach(() => TestBed.configureTestingModule({ imports: [CashflowChartComponent] }));
  it('uses a zero baseline and includes actual values in the accessible data table', () => {
    const fixture = TestBed.createComponent(CashflowChartComponent);
    fixture.componentRef.setInput('points', [
      { month: '2026-09', income: 1000, expenses: 750, cashFlow: 250 },
      { month: '2026-10', income: 0, expenses: 100, cashFlow: -100 },
    ]);
    fixture.detectChanges();
    expect(fixture.componentInstance.height(0)).toBe(0);
    expect(fixture.componentInstance.height(1000)).toBe(155);
    const svg = fixture.nativeElement.querySelector('svg');
    expect(svg.getAttribute('aria-label')).toContain(
      'Total income $1,000.00; total expenses $850.00',
    );
    const rows = fixture.nativeElement.querySelectorAll('tbody tr');
    expect(rows).toHaveLength(2);
    expect(rows[1].textContent).toContain('-$100.00');
  });
  it('handles zero activity without a divide-by-zero chart', () => {
    const fixture = TestBed.createComponent(CashflowChartComponent);
    fixture.componentRef.setInput('points', [
      { month: '2026-10', income: 0, expenses: 0, cashFlow: 0 },
    ]);
    fixture.detectChanges();
    expect(fixture.componentInstance.height(0)).toBe(0);
    expect(Number.isFinite(fixture.componentInstance.width())).toBe(true);
  });
  it('announces an empty dataset', () => {
    const fixture = TestBed.createComponent(CashflowChartComponent);
    fixture.detectChanges();
    expect(fixture.componentInstance.summary()).toContain('No data available.');
  });
});
