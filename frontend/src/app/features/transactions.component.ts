import { CurrencyPipe, DatePipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { ApiService, errorMessage } from '../core/api.service';
import { Account, Page, Transaction } from '../core/models';
import { DialogComponent } from '../shared/dialog.component';
import { IconComponent } from '../shared/icon.component';
import { StateComponent } from '../shared/state.component';
@Component({
  selector: 'ml-transactions',
  imports: [
    CurrencyPipe,
    DatePipe,
    ReactiveFormsModule,
    RouterLink,
    DialogComponent,
    IconComponent,
    StateComponent,
  ],
  templateUrl: './transactions.component.html',
})
export class TransactionsComponent {
  private readonly api = inject(ApiService);
  private readonly fb = inject(FormBuilder);
  private readonly route = inject(ActivatedRoute);
  readonly accounts = signal<Account[]>([]);
  readonly pageData = signal<Page<Transaction> | null>(null);
  readonly loading = signal(true);
  readonly error = signal('');
  readonly success = signal('');
  readonly filterError = signal('');
  readonly advanced = signal(false);
  readonly sort = signal('date,desc');
  readonly page = signal(0);
  readonly editing = signal(false);
  readonly selected = signal<Transaction | null>(null);
  readonly deleting = signal<Transaction | null>(null);
  readonly saving = signal(false);
  readonly formError = signal('');
  readonly today = this.localDate();
  readonly filters = this.fb.nonNullable.group({
    search: [''],
    accountId: [''],
    type: [''],
    from: [''],
    to: [''],
  });
  readonly form = this.fb.nonNullable.group({
    accountId: ['', Validators.required],
    type: ['CONTRIBUTION'],
    amount: [0, [Validators.required, Validators.min(0.01), Validators.max(10000000)]],
    description: ['', [Validators.required, Validators.maxLength(200)]],
    date: [this.localDate(), Validators.required],
  });
  constructor() {
    void this.initialize();
  }
  private localDate(): string {
    const d = new Date();
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
  }
  async initialize(): Promise<void> {
    try {
      this.accounts.set(await this.api.accounts());
      await this.load();
      if (this.route.snapshot.queryParamMap.has('add') && this.accounts().length) this.open();
    } catch (error) {
      this.error.set(errorMessage(error));
      this.loading.set(false);
    }
  }
  async load(): Promise<void> {
    this.loading.set(true);
    this.error.set('');
    try {
      const params: Record<string, string | number> = {
        page: this.page(),
        size: 10,
        sort: this.sort(),
      };
      Object.entries(this.filters.getRawValue()).forEach(([key, value]) => {
        if (value) params[key] = value;
      });
      this.pageData.set(await this.api.transactions(params));
    } catch (error) {
      this.error.set(errorMessage(error));
    } finally {
      this.loading.set(false);
    }
  }
  filter(): void {
    const v = this.filters.getRawValue();
    if (v.from && v.to && v.from > v.to) {
      this.filterError.set('The start date must be before the end date.');
      return;
    }
    this.filterError.set('');
    this.page.set(0);
    void this.load();
  }
  resetFilters(): void {
    this.filters.reset();
    this.filter();
  }
  changeSort(event: Event): void {
    this.sort.set((event.target as HTMLSelectElement).value);
    this.page.set(0);
    void this.load();
  }
  goPage(direction: number): void {
    this.page.update((p) => p + direction);
    void this.load();
  }
  label(type: string): string {
    return (
      {
        CONTRIBUTION: 'Contribution',
        WITHDRAWAL: 'Withdrawal',
        ROLLOVER: 'Rollover',
        RETURN: 'Return',
      }[type] ?? type
    );
  }
  invalid(name: 'accountId' | 'amount' | 'description' | 'date'): boolean {
    return this.form.controls[name].invalid && this.form.controls[name].touched;
  }
  open(tx?: Transaction): void {
    this.selected.set(tx ?? null);
    this.form.reset({
      accountId: tx?.accountId ?? this.accounts()[0]?.id ?? '',
      type: tx?.type ?? 'CONTRIBUTION',
      amount: tx?.amount ?? 0,
      description: tx?.description ?? '',
      date: tx?.date ?? this.localDate(),
    });
    this.formError.set('');
    this.editing.set(true);
  }
  async save(): Promise<void> {
    this.form.markAllAsTouched();
    if (this.form.invalid) return;
    this.saving.set(true);
    try {
      await this.api.save('/transactions', this.form.getRawValue(), this.selected()?.id);
      this.editing.set(false);
      this.success.set('Your transaction is saved and the account balance is updated.');
      await this.load();
    } catch (error) {
      this.formError.set(errorMessage(error));
    } finally {
      this.saving.set(false);
    }
  }
  async remove(): Promise<void> {
    this.saving.set(true);
    try {
      await this.api.remove('/transactions', this.deleting()!.id);
      this.deleting.set(null);
      this.success.set('Your transaction was removed and the account balance is updated.');
      await this.load();
    } catch (error) {
      this.formError.set(errorMessage(error));
    } finally {
      this.saving.set(false);
    }
  }
}
