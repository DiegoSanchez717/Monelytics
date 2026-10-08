import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { ApiService } from '../core/api.service';
import { AssistantReply } from '../core/models';
import { AssistantComponent } from './assistant.component';

describe('financial assistant', () => {
  const categories = [
    { id: 'groceries', name: 'Groceries', type: 'EXPENSE', color: '#EF7A6C' },
    { id: 'salary', name: 'Salary', type: 'INCOME', color: '#48D1CC' },
  ];
  const response: AssistantReply = {
    answer: 'Consider this purchase alongside your recorded budget.',
    provider: 'MOCK',
    decision: 'CAUTION',
    factors: [
      { label: 'Monthly cash flow', value: 431.27, explanation: 'Recorded income minus expenses.' },
    ],
    recommendations: ['Keep upcoming bills covered.'],
    disclaimer: 'Educational information only. Monelytics never moves money.',
    readOnly: true,
    asOf: '2026-10-08T12:00:00Z',
  };
  const api = { categories: vi.fn(), askAssistant: vi.fn(), save: vi.fn() };
  beforeEach(() => {
    vi.resetAllMocks();
    api.categories.mockResolvedValue(categories);
    api.askAssistant.mockResolvedValue(response);
    TestBed.configureTestingModule({
      imports: [AssistantComponent],
      providers: [provideRouter([]), { provide: ApiService, useValue: api }],
    });
  });
  function component(): AssistantComponent {
    return TestBed.runInInjectionContext(() => new AssistantComponent());
  }
  it.each([0, -20, 1.001])(
    'rejects invalid purchase amount %s without requesting guidance',
    async (amount) => {
      const c = component();
      c.form.patchValue({ question: 'Could I buy this?', purchaseAmount: amount });
      await c.ask();
      expect(api.askAssistant).not.toHaveBeenCalled();
      expect(c.error()).toContain('two decimal places');
    },
  );
  it('rejects a whitespace-only question', async () => {
    const c = component();
    c.form.controls.question.setValue('   ');
    await c.ask();
    expect(api.askAssistant).not.toHaveBeenCalled();
  });
  it('omits optional context instead of sending empty strings to UUID or amount fields', async () => {
    const c = component();
    c.form.patchValue({ question: '  How can I build an emergency fund?  ', month: '2026-10' });
    await c.ask();
    expect(api.askAssistant).toHaveBeenCalledWith({
      question: 'How can I build an emergency fund?',
      month: '2026-10',
    });
    expect(api.save).not.toHaveBeenCalled();
  });
  it('allows only owned expense categories in an affordability request', async () => {
    const c = component();
    await c.loadCategories();
    expect(c.categories().map((category) => category.id)).toEqual(['groceries']);
    c.form.patchValue({
      question: 'Is this affordable?',
      purchaseAmount: 200,
      categoryId: 'salary',
    });
    await c.ask();
    expect(api.askAssistant).not.toHaveBeenCalled();
    expect(c.error()).toContain('own category list');
    c.form.controls.categoryId.setValue('groceries');
    await c.ask();
    expect(api.askAssistant).toHaveBeenCalledWith(
      expect.objectContaining({ purchaseAmount: 200, categoryId: 'groceries' }),
    );
  });
  it('renders only the server response, its actual factors, and the educational disclaimer', async () => {
    const fixture = TestBed.createComponent(AssistantComponent);
    fixture.detectChanges();
    fixture.componentInstance.form.controls.question.setValue('Can this fit?');
    await fixture.componentInstance.ask();
    fixture.detectChanges();
    const answer: HTMLElement = fixture.nativeElement.querySelector('.assistant-reply');
    expect(answer.textContent).toContain(response.answer);
    expect(answer.textContent).toContain('$431.27');
    expect(answer.textContent).toContain(response.disclaimer);
    expect(answer.textContent).toContain('Demo assistant');
    expect(answer.textContent).toContain('Read-only · no transactions performed');
    expect(api.save).not.toHaveBeenCalled();
    expect(answer.querySelectorAll('button')).toHaveLength(0);
  });
  it('shows the actual local provider badge when the server used a local model', async () => {
    api.askAssistant.mockResolvedValueOnce({ ...response, provider: 'LOCAL' });
    const fixture = TestBed.createComponent(AssistantComponent);
    fixture.componentInstance.form.controls.question.setValue('How do budgets help?');
    await fixture.componentInstance.ask();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.assistant-reply').textContent).toContain(
      'Local assistant',
    );
  });
  it('keeps the question editable and shows no invented answer on failure', async () => {
    api.askAssistant.mockRejectedValueOnce(
      new HttpErrorResponse({
        status: 503,
        error: { detail: 'The assistant is temporarily unavailable.' },
      }),
    );
    const c = component();
    c.form.controls.question.setValue('How can I plan this month?');
    await c.ask();
    expect(c.error()).toBe('The assistant is temporarily unavailable.');
    expect(c.reply()).toBeNull();
    expect(c.form.controls.question.value).toBe('How can I plan this month?');
    expect(c.loading()).toBe(false);
  });
  it('prevents duplicate requests while a reply is pending', async () => {
    let resolveReply!: (value: AssistantReply) => void;
    api.askAssistant.mockReturnValueOnce(
      new Promise<AssistantReply>((resolve) => {
        resolveReply = resolve;
      }),
    );
    const c = component();
    c.form.controls.question.setValue('How should I plan?');
    const request = c.ask();
    await c.ask();
    expect(api.askAssistant).toHaveBeenCalledTimes(1);
    resolveReply(response);
    await request;
    expect(c.loading()).toBe(false);
  });
});
