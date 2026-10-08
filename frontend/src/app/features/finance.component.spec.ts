import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { ApiService } from '../core/api.service';
import { Budget, Goal, RecurringItem } from '../core/models';
import { TransactionsComponent } from './transactions.component';
import { BudgetsComponent } from './budgets.component';
import { BillsComponent } from './bills.component';
import { GoalsComponent } from './goals.component';

describe('finance business rules in reactive forms', () => {
  const api = {
    accounts: vi.fn().mockResolvedValue([]),
    categories: vi.fn().mockResolvedValue([]),
    transactions: vi
      .fn()
      .mockResolvedValue({ content: [], number: 0, totalPages: 0, totalElements: 0, size: 10 }),
    budgets: vi.fn().mockResolvedValue([]),
    recurring: vi.fn().mockResolvedValue([]),
    goals: vi.fn().mockResolvedValue([]),
    contributions: vi.fn().mockResolvedValue([]),
    save: vi.fn(),
    payRecurring: vi.fn(),
    contribute: vi.fn(),
    removeContribution: vi.fn(),
  };
  beforeEach(() => {
    vi.clearAllMocks();
    api.save.mockResolvedValue({});
    TestBed.configureTestingModule({
      providers: [
        { provide: ApiService, useValue: api },
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { queryParamMap: convertToParamMap({}) } },
        },
      ],
    });
  });
  function transaction(): TransactionsComponent {
    const component = TestBed.runInInjectionContext(() => new TransactionsComponent());
    component.form.patchValue({
      accountId: 'checking',
      type: 'TRANSFER',
      amount: 100,
      description: 'Move savings',
      date: component.today,
    });
    return component;
  }
  it('rejects a transfer back to the source account', async () => {
    const component = transaction();
    component.form.controls.destinationAccountId.setValue('checking');
    await component.save();
    expect(api.save).not.toHaveBeenCalled();
    expect(component.formError()).toContain('different destination');
  });
  it('requires a destination for a transfer', async () => {
    const component = transaction();
    await component.save();
    expect(api.save).not.toHaveBeenCalled();
  });
  it('sends a transfer without an income or expense category', async () => {
    const component = transaction();
    component.form.controls.destinationAccountId.setValue('savings');
    await component.save();
    expect(api.save).toHaveBeenCalledWith(
      '/transactions',
      {
        accountId: 'checking',
        destinationAccountId: 'savings',
        categoryId: null,
        type: 'TRANSFER',
        amount: 100,
        description: 'Move savings',
        date: component.today,
      },
      undefined,
    );
  });
  it('does not submit a category belonging to the wrong transaction type', async () => {
    const component = transaction();
    component.form.patchValue({ type: 'EXPENSE', categoryId: 'salary' });
    component.categories.set([{ id: 'salary', name: 'Salary', type: 'INCOME', color: '#48D1CC' }]);
    await component.save();
    expect(api.save).not.toHaveBeenCalled();
    expect(component.formError()).toContain('matches');
  });
  it('rejects future transactions before sending a request', async () => {
    const component = transaction();
    component.form.patchValue({ type: 'INCOME', date: '9999-01-01' });
    await component.save();
    expect(api.save).not.toHaveBeenCalled();
    expect(component.formError()).toContain('today or earlier');
  });
  it('allows an overall budget without categories and sends a nullable scope', async () => {
    const component = TestBed.runInInjectionContext(() => new BudgetsComponent());
    component.open();
    component.form.patchValue({ limitAmount: 1500, month: '2026-10' });
    await component.save();
    expect(api.save).toHaveBeenCalledWith(
      '/budgets',
      { categoryId: null, month: '2026-10', limitAmount: 1500 },
      undefined,
    );
  });
  it('uses the overall budget summary without double-counting category budgets', () => {
    const component = TestBed.runInInjectionContext(() => new BudgetsComponent());
    component.budgets.set([
      { id: 'all', categoryId: null, limitAmount: 2000, spentAmount: 1500 } as Budget,
      { id: 'food', categoryId: 'food', limitAmount: 400, spentAmount: 250 } as Budget,
    ]);
    expect(component.totalLimit()).toBe(2000);
    expect(component.totalSpent()).toBe(1500);
    expect(component.remaining()).toBe(500);
  });
  it('rejects income categories for category budgets', async () => {
    const component = TestBed.runInInjectionContext(() => new BudgetsComponent());
    component.form.patchValue({ categoryId: 'salary' });
    await component.save();
    expect(api.save).not.toHaveBeenCalled();
    expect(component.formError()).toContain('expense category');
  });
  it('surfaces payment failure without advancing the local recurring item', async () => {
    const component = TestBed.runInInjectionContext(() => new BillsComponent());
    const item = { id: 'bill', nextDueDate: '2026-10-05' } as RecurringItem;
    component.openPayment(item);
    api.payRecurring.mockRejectedValueOnce(
      new HttpErrorResponse({ status: 409, error: { detail: 'Insufficient balance.' } }),
    );
    await component.pay();
    expect(component.paying()).toBe(item);
    expect(item.nextDueDate).toBe('2026-10-05');
    expect(component.formError()).toBe('Insufficient balance.');
    expect(component.saving()).toBe(false);
  });
  it('records goal progress without submitting an account transaction', async () => {
    const component = TestBed.runInInjectionContext(() => new GoalsComponent());
    const goal = { id: 'goal', name: 'Rainy day', currentAmount: 100, targetAmount: 2000 } as Goal;
    await component.openContributions(goal);
    api.contribute.mockResolvedValueOnce({ ...goal, currentAmount: 300 });
    component.contributionForm.patchValue({ amount: 200, note: 'Paycheck' });
    await component.contribute();
    expect(api.contribute).toHaveBeenCalledWith('goal', {
      amount: 200,
      date: component.today,
      note: 'Paycheck',
    });
    expect(api.save).not.toHaveBeenCalled();
    expect(component.contributionGoal()?.currentAmount).toBe(300);
  });
});
