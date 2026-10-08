import { CurrencyPipe, DatePipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ApiService, errorMessage } from '../core/api.service';
import { localDate } from '../core/finance.utils';
import { Account, Category, RecurringItem } from '../core/models';
import { DialogComponent } from '../shared/dialog.component';
import { IconComponent } from '../shared/icon.component';
import { StateComponent } from '../shared/state.component';
@Component({
  selector: 'ml-bills',
  imports: [
    CurrencyPipe,
    DatePipe,
    ReactiveFormsModule,
    RouterLink,
    DialogComponent,
    IconComponent,
    StateComponent,
  ],
  templateUrl: './bills.component.html',
})
export class BillsComponent {
  private readonly api = inject(ApiService);
  private readonly fb = inject(FormBuilder);
  readonly items = signal<RecurringItem[]>([]);
  readonly accounts = signal<Account[]>([]);
  readonly categories = signal<Category[]>([]);
  readonly loading = signal(true);
  readonly error = signal('');
  readonly success = signal('');
  readonly kind = signal('ALL');
  readonly editing = signal(false);
  readonly selected = signal<RecurringItem | null>(null);
  readonly deleting = signal<RecurringItem | null>(null);
  readonly paying = signal<RecurringItem | null>(null);
  readonly saving = signal(false);
  readonly formError = signal('');
  readonly today = localDate();
  readonly form = this.fb.nonNullable.group({
    name: ['', [Validators.required, Validators.maxLength(100)]],
    kind: ['BILL'],
    amount: [0, [Validators.required, Validators.min(0.01), Validators.max(10000000)]],
    accountId: ['', Validators.required],
    categoryId: ['', Validators.required],
    frequency: ['MONTHLY'],
    nextDueDate: [localDate(), Validators.required],
    active: [true],
  });
  readonly payment = this.fb.nonNullable.group({ date: [localDate(), Validators.required] });
  constructor() {
    void this.initialize();
  }
  async initialize(): Promise<void> {
    this.loading.set(true);
    this.error.set('');
    try {
      const [accounts, categories] = await Promise.all([
        this.api.accounts(),
        this.api.categories(),
      ]);
      this.accounts.set(accounts);
      this.categories.set(categories.filter((c) => c.type === 'EXPENSE'));
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
      this.items.set(await this.api.recurring());
    } catch (error) {
      this.error.set(errorMessage(error));
    } finally {
      this.loading.set(false);
    }
  }
  filtered(): RecurringItem[] {
    return this.items()
      .filter((i) => this.kind() === 'ALL' || i.kind === this.kind())
      .sort((a, b) => a.nextDueDate.localeCompare(b.nextDueDate));
  }
  changeKind(event: Event): void {
    this.kind.set((event.target as HTMLSelectElement).value);
  }
  monthlyCost(): number {
    return this.items()
      .filter((i) => i.active)
      .reduce((sum, i) => sum + (i.frequency === 'YEARLY' ? i.amount / 12 : i.amount), 0);
  }
  dueDays(item: RecurringItem): number {
    return Math.round(
      (new Date(`${item.nextDueDate}T12:00:00`).getTime() -
        new Date(`${this.today}T12:00:00`).getTime()) /
        86400000,
    );
  }
  dueSoon(): number {
    return this.items().filter((i) => i.active && this.dueDays(i) <= 7).length;
  }
  status(item: RecurringItem): string {
    return !item.active
      ? 'Paused'
      : this.dueDays(item) < 0
        ? 'Overdue'
        : this.dueDays(item) === 0
          ? 'Due today'
          : this.dueDays(item) <= 7
            ? 'Due soon'
            : 'Upcoming';
  }
  open(item?: RecurringItem): void {
    this.selected.set(item ?? null);
    this.form.reset({
      name: item?.name ?? '',
      kind: item?.kind ?? 'BILL',
      amount: item?.amount ?? 0,
      accountId: item?.accountId ?? this.accounts()[0]?.id ?? '',
      categoryId: item?.categoryId ?? this.categories()[0]?.id ?? '',
      frequency: item?.frequency ?? 'MONTHLY',
      nextDueDate: item?.nextDueDate ?? localDate(),
      active: item?.active ?? true,
    });
    this.formError.set('');
    this.editing.set(true);
  }
  async save(): Promise<void> {
    this.form.markAllAsTouched();
    if (this.form.invalid) {
      this.formError.set(
        'Complete the name, positive amount, account, expense category, and due date.',
      );
      return;
    }
    this.saving.set(true);
    this.formError.set('');
    try {
      await this.api.save('/recurring', this.form.getRawValue(), this.selected()?.id);
      this.editing.set(false);
      this.success.set('Your recurring item is saved.');
      await this.load();
    } catch (error) {
      this.formError.set(errorMessage(error));
    } finally {
      this.saving.set(false);
    }
  }
  openPayment(item: RecurringItem): void {
    this.paying.set(item);
    this.payment.reset({ date: localDate() });
    this.formError.set('');
  }
  async pay(): Promise<void> {
    this.payment.markAllAsTouched();
    if (this.payment.invalid || this.payment.controls.date.value > this.today) {
      this.formError.set('Choose a payment date that is today or earlier.');
      return;
    }
    this.saving.set(true);
    this.formError.set('');
    try {
      await this.api.payRecurring(this.paying()!, this.payment.controls.date.value);
      this.paying.set(null);
      this.success.set('Payment recorded. Your balance, spending, and next due date are updated.');
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
      await this.api.remove('/recurring', this.deleting()!.id);
      this.deleting.set(null);
      this.success.set('Your recurring item was removed. Recorded payments are unchanged.');
      await this.load();
    } catch (error) {
      this.formError.set(errorMessage(error));
    } finally {
      this.saving.set(false);
    }
  }
}
