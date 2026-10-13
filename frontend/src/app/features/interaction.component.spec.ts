import { HttpErrorResponse } from '@angular/common/http';
import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';
import { ApiService } from '../core/api.service';
import { AuthService } from '../core/auth.service';
import {
  Analytics,
  Budget,
  Dashboard,
  Goal,
  GoalContribution,
  Page,
  Transaction,
} from '../core/models';
import { AuthComponent } from './auth.component';
import { GoalsComponent } from './goals.component';
import { ReportsComponent } from './reports.component';
import { DashboardComponent } from './dashboard.component';
import { BudgetsComponent } from './budgets.component';
import { TransactionsComponent } from './transactions.component';

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
