import { CurrencyPipe, DecimalPipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ApiService, errorMessage } from '../core/api.service';
import { currentMonth, monthLabel } from '../core/finance.utils';
import { Budget, Category } from '../core/models';
import { DialogComponent } from '../shared/dialog.component';
import { IconComponent } from '../shared/icon.component';
import { StateComponent } from '../shared/state.component';
@Component({
  selector: 'ml-budgets',
  imports: [
    CurrencyPipe,
    DecimalPipe,
    ReactiveFormsModule,
    DialogComponent,
    IconComponent,
    StateComponent,
  ],
  templateUrl: './budgets.component.html',
})
export class BudgetsComponent {
  private readonly api = inject(ApiService);
  private readonly fb = inject(FormBuilder);
  readonly month = signal(currentMonth());
  readonly monthLabel = monthLabel;
  readonly budgets = signal<Budget[]>([]);
  readonly categories = signal<Category[]>([]);
  readonly loading = signal(true);
  readonly error = signal('');
  readonly success = signal('');
  readonly editing = signal(false);
  readonly selected = signal<Budget | null>(null);
  readonly deleting = signal<Budget | null>(null);
  readonly saving = signal(false);
  readonly formError = signal('');
  readonly form = this.fb.nonNullable.group({
    categoryId: [''],
    month: [currentMonth(), [Validators.required, Validators.pattern(/^\d{4}-\d{2}$/)]],
    limitAmount: [500, [Validators.required, Validators.min(0.01), Validators.max(10000000)]],
  });
  constructor() {
    void this.initialize();
  }
  async initialize(): Promise<void> {
    try {
      this.categories.set((await this.api.categories()).filter((c) => c.type === 'EXPENSE'));
      await this.load();
    } catch (error) {
      this.error.set(errorMessage(error));
      this.loading.set(false);
    }
  }
  async load(): Promise<void> {
    this.loading.set(true);
    this.error.set('');
    try {
      this.budgets.set(await this.api.budgets(this.month()));
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
  totalLimit(): number {
    return this.overall()?.limitAmount ?? this.budgets().reduce((sum, b) => sum + b.limitAmount, 0);
  }
  totalSpent(): number {
    return this.overall()?.spentAmount ?? this.budgets().reduce((sum, b) => sum + b.spentAmount, 0);
  }
  overall(): Budget | undefined {
    return this.budgets().find((b) => b.categoryId === null);
  }
  remaining(): number {
    return this.totalLimit() - this.totalSpent();
  }
  width(budget: Budget): number {
    return Math.max(0, Math.min(100, budget.percentage));
  }
  status(budget: Budget): string {
    return { ON_TRACK: 'On track', NEAR_LIMIT: 'Nearly there', OVER_BUDGET: 'Over budget' }[
      budget.status
    ];
  }
  open(budget?: Budget): void {
    this.selected.set(budget ?? null);
    this.form.reset({
      categoryId: budget?.categoryId ?? '',
      month: budget?.month ?? this.month(),
      limitAmount: budget?.limitAmount ?? 500,
    });
    this.formError.set('');
    this.editing.set(true);
  }
  async save(): Promise<void> {
    this.form.markAllAsTouched();
    if (this.form.invalid) {
      this.formError.set('Choose a month and a positive budget amount.');
      return;
    }
    const value = this.form.getRawValue();
    if (value.categoryId && !this.categories().some((c) => c.id === value.categoryId)) {
      this.formError.set('Choose an expense category or the overall monthly budget.');
      return;
    }
    this.saving.set(true);
    this.formError.set('');
    try {
      await this.api.save(
        '/budgets',
        { ...value, categoryId: value.categoryId || null },
        this.selected()?.id,
      );
      this.month.set(this.form.controls.month.value);
      this.editing.set(false);
      this.success.set('Your monthly budget is saved.');
      await this.load();
    } catch (error) {
      this.formError.set(errorMessage(error));
    } finally {
      this.saving.set(false);
    }
  }
  async remove(): Promise<void> {
    this.saving.set(true);
    this.formError.set('');
    try {
      await this.api.remove('/budgets', this.deleting()!.id);
      this.deleting.set(null);
      this.success.set('Your budget was removed. Your transactions are unchanged.');
      await this.load();
    } catch (error) {
      this.formError.set(errorMessage(error));
    } finally {
      this.saving.set(false);
    }
  }
}
