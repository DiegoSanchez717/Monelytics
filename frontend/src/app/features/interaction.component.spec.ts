import { HttpErrorResponse } from '@angular/common/http';
import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';
import { ApiService } from '../core/api.service';
import { AuthService } from '../core/auth.service';
import {
  Analytics,
  Account,
  Budget,
  Dashboard,
  Goal,
  GoalContribution,
  Page,
  RecurringItem,
  Transaction,
} from '../core/models';
import { AuthComponent } from './auth.component';
import { GoalsComponent } from './goals.component';
import { ReportsComponent } from './reports.component';
import { DashboardComponent } from './dashboard.component';
import { BudgetsComponent } from './budgets.component';
import { TransactionsComponent } from './transactions.component';
import { AccountsComponent } from './accounts.component';
import { CategoriesComponent } from './categories.component';
import { BillsComponent } from './bills.component';

function deferred<T>() {
  let resolve!: (value: T) => void;
  let reject!: (reason: unknown) => void;
  const promise = new Promise<T>((yes, no) => {
    resolve = yes;
    reject = no;
  });
  return { promise, resolve, reject };
}
describe('interactive request boundaries', () => {
  const auth = { user: signal(null), login: vi.fn(), register: vi.fn() };
  const api = {
    accounts: vi.fn(),
    categories: vi.fn(),
    recurring: vi.fn(),
    goals: vi.fn(),
    contributions: vi.fn(),
    contribute: vi.fn(),
    removeContribution: vi.fn(),
    analytics: vi.fn(),
    dashboard: vi.fn(),
    budgets: vi.fn(),
    transactions: vi.fn(),
    save: vi.fn(),
    update: vi.fn(),
    payRecurring: vi.fn(),
  };
  const router = { url: '/login', navigateByUrl: vi.fn() };
  beforeEach(() => {
    vi.resetAllMocks();
    api.accounts.mockResolvedValue([]);
    api.categories.mockResolvedValue([]);
    api.recurring.mockResolvedValue([]);
    api.goals.mockResolvedValue([]);
    api.contributions.mockResolvedValue([]);
    api.budgets.mockResolvedValue([]);
    api.save.mockResolvedValue({});
    api.update.mockResolvedValue({});
    api.analytics.mockImplementation((month: string) => Promise.resolve({ month }));
    api.dashboard.mockImplementation((month: string) => Promise.resolve({ month }));
    api.transactions.mockResolvedValue({
      content: [],
      number: 0,
      size: 10,
      totalPages: 0,
      totalElements: 0,
    });
    TestBed.configureTestingModule({
      providers: [
        { provide: ApiService, useValue: api },
        { provide: AuthService, useValue: auth },
        { provide: Router, useValue: router },
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { queryParamMap: convertToParamMap({}) } },
        },
      ],
    });
  });
  it('waits for bill account options before opening a valid draft', async () => {
    const pending = deferred<Account[]>();
    api.accounts.mockReturnValue(pending.promise);
    api.categories.mockResolvedValue([
      { id: 'housing', name: 'Housing', type: 'EXPENSE', color: '#EF7A6C' },
    ]);
    const c = TestBed.runInInjectionContext(() => new BillsComponent());
    const initialization = c.initialize();
    c.open();
    expect(c.editing()).toBe(false);
    pending.resolve([
      {
        id: 'checking',
        name: 'Checking',
        type: 'CHECKING',
        balance: 1000,
        annualContributions: 0,
        beneficiaries: [],
      },
    ]);
    await initialization;
    c.open();
    expect(c.editing()).toBe(true);
    expect(c.form.controls.accountId.value).toBe('checking');
    expect(c.form.controls.categoryId.value).toBe('housing');
  });
  const editors = [
    {
      name: 'accounts',
      create: () => {
        const c = new AccountsComponent();
        return {
          component: c,
          ready: () => c.load(),
          fill: (name: string) => c.form.controls.name.setValue(name),
          draft: () => c.form.getRawValue(),
          refresh: api.accounts,
        };
      },
    },
    {
      name: 'categories',
      create: () => {
        const c = new CategoriesComponent();
        return {
          component: c,
          ready: () => c.load(),
          fill: (name: string) => c.form.controls.name.setValue(name),
          draft: () => c.form.getRawValue(),
          refresh: api.categories,
        };
      },
    },
    {
      name: 'budgets',
      create: () => {
        const c = new BudgetsComponent();
        c.month.set('2026-10');
        return {
          component: c,
          ready: () => c.initialize(),
          fill: (name: string) =>
            c.form.patchValue({
              month: name === 'old' ? '2026-10' : '2026-11',
              limitAmount: name === 'old' ? 100 : 200,
            }),
          draft: () => c.form.getRawValue(),
          refresh: api.budgets,
        };
      },
    },
    {
      name: 'bills',
      create: () => {
        const c = new BillsComponent();
        return {
          component: c,
          ready: () => c.initialize(),
          fill: (name: string) =>
            c.form.patchValue({ name, amount: 25, accountId: 'checking', categoryId: 'expense' }),
          draft: () => c.form.getRawValue(),
          refresh: api.recurring,
        };
      },
    },
    {
      name: 'goals',
      create: () => {
        const c = new GoalsComponent();
        return {
          component: c,
          ready: () => c.load(),
          fill: (name: string) => c.form.patchValue({ name, targetDate: '2099-12-31' }),
          draft: () => c.form.getRawValue(),
          refresh: api.goals,
        };
      },
    },
    {
      name: 'transactions',
      create: () => {
        const c = new TransactionsComponent();
        return {
          component: c,
          ready: () => c.initialize(),
          fill: (name: string) =>
            c.form.patchValue({ accountId: 'checking', description: name, amount: 25 }),
          draft: () => c.form.getRawValue(),
          refresh: api.transactions,
        };
      },
    },
  ];
  it.each(editors)(
    '$name refreshes the saved record without closing or changing a new draft',
    async ({ create }) => {
      const editor = TestBed.runInInjectionContext(() => create());
      const c = editor.component;
      await editor.ready();
      editor.refresh.mockClear();
      c.open();
      editor.fill('old');
      const pending = deferred<unknown>();
      api.save.mockReturnValueOnce(pending.promise);
      const first = c.save();
      expect(c.saving()).toBe(true);
      c.editing.set(false);
      c.open();
      editor.fill('new');
      const freshDraft = editor.draft();
      await c.save();
      expect(api.save).toHaveBeenCalledTimes(1);
      expect(c.saving()).toBe(true);
      pending.resolve({});
      await first;
      expect(c.editing()).toBe(true);
      expect(editor.draft()).toEqual(freshDraft);
      expect(c.formError()).toBe('');
      expect(c.saving()).toBe(false);
      expect(editor.refresh).toHaveBeenCalledTimes(1);
      if (c instanceof BudgetsComponent) expect(c.month()).toBe('2026-10');
    },
  );
  it.each(editors)(
    '$name keeps an older save failure outside a reopened draft',
    async ({ create }) => {
      const editor = TestBed.runInInjectionContext(() => create());
      const c = editor.component;
      await editor.ready();
      editor.refresh.mockClear();
      c.open();
      editor.fill('old');
      const pending = deferred<unknown>();
      api.save.mockReturnValueOnce(pending.promise);
      const first = c.save();
      c.editing.set(false);
      c.open();
      editor.fill('new');
      const freshDraft = editor.draft();
      pending.reject(
        new HttpErrorResponse({
          status: 409,
          error: { detail: 'The previous draft could not be saved.' },
        }),
      );
      await first;
      expect(c.editing()).toBe(true);
      expect(editor.draft()).toEqual(freshDraft);
      expect(c.formError()).toBe('');
      expect(c.error()).toBe('The previous draft could not be saved.');
      expect(c.saving()).toBe(false);
      expect(editor.refresh).not.toHaveBeenCalled();
    },
  );
  it.each(['success', 'failure'] as const)(
    'keeps a reopened beneficiary editor separate from the previous %s',
    async (outcome) => {
      const c = TestBed.runInInjectionContext(() => new AccountsComponent());
      await c.load();
      api.accounts.mockClear();
      const account = {
        id: 'ira',
        name: 'Retirement',
        type: 'ROTH_IRA',
        balance: 0,
        annualContributions: 0,
        beneficiaries: [],
      } as Account;
      c.openBeneficiaries(account);
      c.beneficiaryControls.at(0).patchValue({ name: 'First person', relationship: 'Spouse' });
      const pending = deferred<unknown>();
      api.update.mockReturnValueOnce(pending.promise);
      const first = c.saveBeneficiaries();
      c.beneficiaryAccount.set(null);
      c.openBeneficiaries(account);
      c.beneficiaryControls.at(0).patchValue({ name: 'New draft', relationship: 'Child' });
      const freshDraft = c.beneficiaryForm.getRawValue();
      await c.saveBeneficiaries();
      expect(api.update).toHaveBeenCalledTimes(1);
      if (outcome === 'success') pending.resolve({});
      else
        pending.reject(
          new HttpErrorResponse({
            status: 409,
            error: { detail: 'The previous beneficiary update failed.' },
          }),
        );
      await first;
      expect(c.beneficiaryAccount()?.id).toBe(account.id);
      expect(c.beneficiaryForm.getRawValue()).toEqual(freshDraft);
      expect(c.formError()).toBe('');
      expect(c.saving()).toBe(false);
      expect(api.accounts).toHaveBeenCalledTimes(outcome === 'success' ? 1 : 0);
    },
  );
  it.each(['success', 'failure'] as const)(
    'keeps a reopened payment dialog separate from the previous %s',
    async (outcome) => {
      const c = TestBed.runInInjectionContext(() => new BillsComponent());
      await c.initialize();
      api.recurring.mockClear();
      const item = {
        id: 'rent',
        name: 'Rent',
        kind: 'BILL',
        accountId: 'checking',
        categoryId: 'housing',
        amount: 25,
        frequency: 'MONTHLY',
        nextDueDate: c.today,
        active: true,
      } as RecurringItem;
      c.openPayment(item);
      const pending = deferred<unknown>();
      api.payRecurring.mockReturnValueOnce(pending.promise);
      const first = c.pay();
      c.paying.set(null);
      c.openPayment(item);
      c.payment.controls.date.setValue('2026-01-01');
      await c.pay();
      expect(api.payRecurring).toHaveBeenCalledTimes(1);
      if (outcome === 'success') pending.resolve({});
      else
        pending.reject(
          new HttpErrorResponse({ status: 409, error: { detail: 'The previous payment failed.' } }),
        );
      await first;
      expect(c.paying()?.id).toBe(item.id);
      expect(c.payment.controls.date.value).toBe('2026-01-01');
      expect(c.formError()).toBe('');
      expect(c.saving()).toBe(false);
      expect(api.recurring).toHaveBeenCalledTimes(outcome === 'success' ? 1 : 0);
    },
  );
  it('lets a non-MFA account sign in after switching away from an MFA challenge', async () => {
    auth.login.mockRejectedValueOnce(
      new HttpErrorResponse({ status: 401, error: { code: 'MFA_REQUIRED' } }),
    );
    const c = TestBed.runInInjectionContext(() => new AuthComponent());
    c.form.patchValue({ email: 'mfa@example.com', password: 'PrivatePassword!2026' });
    await c.submit();
    expect(c.mfaRequired()).toBe(true);
    expect(c.form.controls.code.invalid).toBe(true);
    c.fillDemo();
    expect(c.mfaRequired()).toBe(false);
    expect(c.form.controls.code.valid).toBe(true);
    auth.login.mockResolvedValueOnce({ id: 'demo' });
    await c.submit();
    expect(router.navigateByUrl).toHaveBeenCalledWith('/dashboard');
    expect(auth.login).toHaveBeenLastCalledWith({
      email: 'demo@monelytics.dev',
      password: 'Monelytics!2026',
    });
  });
  it('keeps a closed savings dialog closed when a contribution finishes', async () => {
    const c = TestBed.runInInjectionContext(() => new GoalsComponent());
    await c.load();
    const goal = { id: 'a', name: 'Trip', currentAmount: 0, targetAmount: 1000 } as Goal;
    c.goals.set([goal]);
    await c.openContributions(goal);
    c.contributionForm.patchValue({ amount: 100 });
    const pending = deferred<Goal>();
    api.contribute.mockReturnValueOnce(pending.promise);
    const action = c.contribute();
    c.contributionGoal.set(null);
    pending.resolve({ ...goal, currentAmount: 100 });
    await action;
    expect(c.contributionGoal()).toBeNull();
    expect(c.goals()[0].currentAmount).toBe(100);
  });
  it('does not overwrite a new goal history with a slow previous response', async () => {
    const c = TestBed.runInInjectionContext(() => new GoalsComponent());
    await c.load();
    const a = { id: 'a' } as Goal;
    const b = { id: 'b' } as Goal;
    const slow = deferred<GoalContribution[]>();
    api.contributions
      .mockReturnValueOnce(slow.promise)
      .mockResolvedValueOnce([{ id: 'b-entry', amount: 20, date: c.today, note: '' }]);
    const first = c.openContributions(a);
    await c.openContributions(b);
    slow.resolve([{ id: 'a-entry', amount: 100, date: c.today, note: '' }]);
    await first;
    expect(c.contributionGoal()?.id).toBe('b');
    expect(c.contributions()[0].id).toBe('b-entry');
    expect(c.historyLoading()).toBe(false);
  });
  it('preserves the current report when an older month request fails late', async () => {
    const c = TestBed.runInInjectionContext(() => new ReportsComponent());
    await c.load();
    const slow = deferred<Analytics>();
    api.analytics
      .mockReturnValueOnce(slow.promise)
      .mockResolvedValueOnce({ month: '2026-10', income: 1000 });
    c.month.set('2026-09');
    const first = c.load();
    c.month.set('2026-10');
    await c.load();
    slow.reject(new HttpErrorResponse({ status: 500 }));
    await first;
    expect(c.data()?.month).toBe('2026-10');
    expect(c.error()).toBe('');
    expect(c.loading()).toBe(false);
  });
  it('keeps the latest dashboard month when older data arrives afterward', async () => {
    const c = TestBed.runInInjectionContext(() => new DashboardComponent());
    await c.load();
    const slow = deferred<Dashboard>();
    api.dashboard.mockReturnValueOnce(slow.promise).mockResolvedValueOnce({ month: '2026-10' });
    c.month.set('2026-09');
    const first = c.load();
    c.month.set('2026-10');
    await c.load();
    slow.resolve({ month: '2026-09' } as Dashboard);
    await first;
    expect(c.data()?.month).toBe('2026-10');
  });
  it('keeps budgets matched to the most recently requested month', async () => {
    const c = TestBed.runInInjectionContext(() => new BudgetsComponent());
    await c.initialize();
    const slow = deferred<Budget[]>();
    api.budgets
      .mockReturnValueOnce(slow.promise)
      .mockResolvedValueOnce([{ id: 'latest', month: '2026-10' }]);
    c.month.set('2026-09');
    const first = c.load();
    c.month.set('2026-10');
    await c.load();
    slow.resolve([{ id: 'old', month: '2026-09' } as Budget]);
    await first;
    expect(c.budgets()[0].id).toBe('latest');
  });
  it('keeps results from the latest transaction filter request', async () => {
    const c = TestBed.runInInjectionContext(() => new TransactionsComponent());
    await c.initialize();
    const slow = deferred<Page<Transaction>>();
    api.transactions
      .mockReturnValueOnce(slow.promise)
      .mockResolvedValueOnce({ content: [], number: 0, totalElements: 7, totalPages: 1, size: 10 });
    c.filters.controls.search.setValue('old');
    const first = c.load();
    c.filters.controls.search.setValue('new');
    await c.load();
    slow.resolve({ content: [], number: 0, totalElements: 2, totalPages: 1, size: 10 });
    await first;
    expect(c.pageData()?.totalElements).toBe(7);
  });
});
