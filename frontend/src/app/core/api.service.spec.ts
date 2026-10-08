import { provideHttpClient, HttpErrorResponse } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ApiService, errorMessage } from './api.service';
describe('REST communication', () => {
  let api: ApiService;
  let http: HttpTestingController;
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    api = TestBed.inject(ApiService);
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => http.verify());
  it('sends server-side transaction filters, sort and page parameters', async () => {
    const promise = api.transactions({
      search: 'monthly plan',
      type: 'CONTRIBUTION',
      page: 2,
      size: 10,
      sort: 'amount,desc',
    });
    const request = http.expectOne((r) => r.url === '/api/transactions');
    expect(request.request.params.get('search')).toBe('monthly plan');
    expect(request.request.params.get('page')).toBe('2');
    expect(request.request.params.get('sort')).toBe('amount,desc');
    request.flush({
      content: [],
      number: 2,
      totalPages: 3,
      totalElements: 21,
      size: 10,
    });
    expect((await promise).number).toBe(2);
  });
  it('uses PUT for an existing UUID resource', async () => {
    const promise = api.save('/goals', { name: 'Freedom' }, 'abc-123');
    const request = http.expectOne('/api/goals/abc-123');
    expect(request.request.method).toBe('PUT');
    request.flush({ name: 'Freedom' });
    await promise;
  });
  it('scopes analytics to the selected month', async () => {
    const promise = api.analytics('2026-10');
    const request = http.expectOne((r) => r.url === '/api/analytics');
    expect(request.request.params.get('month')).toBe('2026-10');
    request.flush({ month: '2026-10', income: 100, expenses: 30 });
    expect((await promise).income).toBe(100);
  });
  it('exports all matching transactions as a CSV blob with category filters', async () => {
    const promise = api.exportTransactions({
      categoryId: 'expense-category',
      from: '2026-10-01',
      sort: 'date,asc',
    });
    const request = http.expectOne((r) => r.url === '/api/transactions/export.csv');
    expect(request.request.responseType).toBe('blob');
    expect(request.request.params.get('categoryId')).toBe('expense-category');
    expect(request.request.params.has('page')).toBe(false);
    const csv = new Blob(['description,amount\nGroceries,20'], { type: 'text/csv' });
    request.flush(csv);
    expect(await promise).toBe(csv);
  });
  it('includes the due-date snapshot so recurring payment retries stay idempotent', async () => {
    const promise = api.payRecurring(
      { id: 'bill', nextDueDate: '2026-10-05' } as import('./models').RecurringItem,
      '2026-10-07',
    );
    const request = http.expectOne('/api/recurring/bill/pay');
    expect(request.request.body).toEqual({ dueDate: '2026-10-05', date: '2026-10-07' });
    request.flush({ id: 'payment' });
    await promise;
  });
  it('records earmarked savings through the goal endpoint', async () => {
    const value = { amount: 200, date: '2026-10-07', note: 'Paycheck' };
    const promise = api.contribute('goal', value);
    const request = http.expectOne('/api/goals/goal/contributions');
    expect(request.request.body).toEqual(value);
    request.flush({ id: 'goal', currentAmount: 200 });
    expect((await promise).currentAmount).toBe(200);
  });
  it('reads the updated goal after a contribution is removed', async () => {
    const promise = api.removeContribution('goal', 'contribution');
    const request = http.expectOne('/api/goals/goal/contributions/contribution');
    expect(request.request.method).toBe('DELETE');
    request.flush({ id: 'goal', currentAmount: 0 });
    expect((await promise).currentAmount).toBe(0);
  });
  it('sends beneficiary changes to the dedicated PUT endpoint', async () => {
    const promise = api.update('/accounts/abc/beneficiaries', {
      beneficiaries: [],
    });
    const request = http.expectOne('/api/accounts/abc/beneficiaries');
    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toEqual({ beneficiaries: [] });
    request.flush({});
    await promise;
  });
  it('posts retirement scenarios to the API origin rather than the SPA route', async () => {
    const scenario = {
      currentAge: 64,
      retirementAge: 65,
      currentSavings: 1000,
      monthlyContribution: 100,
      annualReturn: 0,
      targetAmount: 2200,
    };
    const promise = api.calculate(scenario);
    const request = http.expectOne('/api/goals/calculate');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual(scenario);
    request.flush({ projectedBalance: 2200 });
    expect((await promise).projectedBalance).toBe(2200);
  });
  it('surfaces server business-rule errors', () => {
    expect(
      errorMessage(
        new HttpErrorResponse({
          status: 409,
          error: { detail: 'Withdrawal exceeds your account balance.' },
        }),
      ),
    ).toBe('Withdrawal exceeds your account balance.');
  });
  it('turns a transport failure into a useful retry message', () => {
    expect(errorMessage(new HttpErrorResponse({ status: 0 }))).toContain('check your connection');
  });
  it('shows specific server field validation messages in the error summary', () => {
    const error = new HttpErrorResponse({
      status: 400,
      error: {
        detail: 'Check the highlighted fields.',
        errors: { targetDate: 'must be a future date' },
      },
    });
    expect(errorMessage(error)).toBe(
      'Please review these values: target date: must be a future date.',
    );
  });
});
