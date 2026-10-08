import { CurrencyPipe, DecimalPipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { FormArray, FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ApiService, errorMessage } from '../core/api.service';
import { Account } from '../core/models';
import { DialogComponent } from '../shared/dialog.component';
import { IconComponent } from '../shared/icon.component';
import { StateComponent } from '../shared/state.component';
@Component({
  selector: 'wp-accounts',
  imports: [
    CurrencyPipe,
    DecimalPipe,
    ReactiveFormsModule,
    DialogComponent,
    IconComponent,
    StateComponent,
  ],
  templateUrl: './accounts.component.html',
})
export class AccountsComponent {
  private readonly api = inject(ApiService);
  private readonly fb = inject(FormBuilder);
  readonly accounts = signal<Account[]>([]);
  readonly loading = signal(true);
  readonly error = signal('');
  readonly success = signal('');
  readonly editing = signal(false);
  readonly selected = signal<Account | null>(null);
  readonly deleting = signal<Account | null>(null);
  readonly beneficiaryAccount = signal<Account | null>(null);
  readonly saving = signal(false);
  readonly formError = signal('');
  readonly form = this.fb.nonNullable.group({
    name: ['', [Validators.required, Validators.maxLength(80)]],
    type: ['ROTH_IRA'],
    openingBalance: [0, [Validators.required, Validators.min(0), Validators.max(100000000)]],
  });
  readonly beneficiaryForm = this.fb.group({
    beneficiaries: this.fb.array<ReturnType<typeof this.beneficiaryGroup>>([]),
  });
  constructor() {
    void this.load();
  }
  get beneficiaryControls(): FormArray {
    return this.beneficiaryForm.controls.beneficiaries;
  }
  beneficiaryGroup(name = '', relationship = '', percentage = 100) {
    return this.fb.nonNullable.group({
      name: [name, [Validators.required, Validators.maxLength(100)]],
      relationship: [relationship, [Validators.required, Validators.maxLength(40)]],
      percentage: [percentage, [Validators.required, Validators.min(0.01), Validators.max(100)]],
    });
  }
  total(): number {
    return this.accounts().reduce((sum, a) => sum + a.balance, 0);
  }
  invalid(name: 'name' | 'openingBalance'): boolean {
    return this.form.controls[name].invalid && this.form.controls[name].touched;
  }
  async load(): Promise<void> {
    this.loading.set(true);
    this.error.set('');
    try {
      this.accounts.set(await this.api.accounts());
    } catch (error) {
      this.error.set(errorMessage(error));
    } finally {
      this.loading.set(false);
    }
  }
  open(account?: Account): void {
    this.selected.set(account ?? null);
    this.form.reset({
      name: account?.name ?? '',
      type: account?.type ?? 'ROTH_IRA',
      openingBalance: 0,
    });
    this.formError.set('');
    this.editing.set(true);
  }
  async save(): Promise<void> {
    this.form.markAllAsTouched();
    if (this.form.invalid) return;
    this.saving.set(true);
    try {
      const v = this.form.getRawValue();
      await this.api.save(
        '/accounts',
        this.selected() ? { name: v.name, type: v.type } : v,
        this.selected()?.id,
      );
      this.editing.set(false);
      this.success.set('Your IRA account is saved.');
      await this.load();
    } catch (error) {
      this.formError.set(errorMessage(error));
    } finally {
      this.saving.set(false);
    }
  }
  openBeneficiaries(account: Account): void {
    this.formError.set('');
    this.beneficiaryControls.clear();
    for (const b of account.beneficiaries)
      this.beneficiaryControls.push(this.beneficiaryGroup(b.name, b.relationship, b.percentage));
    if (!account.beneficiaries.length) this.addBeneficiary();
    this.beneficiaryAccount.set(account);
  }
  addBeneficiary(): void {
    this.beneficiaryControls.push(
      this.beneficiaryGroup('', '', this.beneficiaryControls.length ? 0 : 100),
    );
  }
  allocationTotal(): number {
    return (
      Math.round(
        this.beneficiaryControls.controls.reduce(
          (sum, c) => sum + Number(c.get('percentage')?.value ?? 0),
          0,
        ) * 100,
      ) / 100
    );
  }
  async saveBeneficiaries(): Promise<void> {
    this.beneficiaryForm.markAllAsTouched();
    if (
      this.beneficiaryForm.invalid ||
      (this.beneficiaryControls.length > 0 && this.allocationTotal() !== 100)
    ) {
      this.formError.set(
        'Complete each beneficiary and assign exactly 100% in total, or remove every entry to clear assignments.',
      );
      return;
    }
    this.saving.set(true);
    try {
      await this.api.update(
        `/accounts/${this.beneficiaryAccount()!.id}/beneficiaries`,
        this.beneficiaryForm.getRawValue(),
      );
      this.beneficiaryAccount.set(null);
      this.success.set('Your beneficiaries are updated.');
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
      await this.api.remove('/accounts', this.deleting()!.id);
      this.deleting.set(null);
      this.success.set('Your account was removed.');
      await this.load();
    } catch (error) {
      this.formError.set(errorMessage(error));
    } finally {
      this.saving.set(false);
    }
  }
}
