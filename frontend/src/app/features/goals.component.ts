import { CurrencyPipe, DatePipe, DecimalPipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import {
  AbstractControl,
  FormBuilder,
  ReactiveFormsModule,
  ValidationErrors,
  Validators,
} from '@angular/forms';
import { ApiService, errorMessage } from '../core/api.service';
import { Goal, GoalContribution, Projection } from '../core/models';
import { localDate } from '../core/finance.utils';
import { ChartComponent } from '../shared/chart.component';
import { DialogComponent } from '../shared/dialog.component';
import { IconComponent } from '../shared/icon.component';
import { StateComponent } from '../shared/state.component';
export function retirementAgeValidator(control: AbstractControl): ValidationErrors | null {
  const current = Number(control.get('currentAge')?.value);
  const retirement = Number(control.get('retirementAge')?.value);
  return retirement > current ? null : { ageOrder: true };
}
@Component({
  selector: 'ml-goals',
  imports: [
    CurrencyPipe,
    DatePipe,
    DecimalPipe,
    ReactiveFormsModule,
    ChartComponent,
    DialogComponent,
    IconComponent,
    StateComponent,
  ],
  templateUrl: './goals.component.html',
})
export class GoalsComponent {
  private readonly api = inject(ApiService);
  private readonly fb = inject(FormBuilder);
  readonly showCalculator = signal(false);
  readonly contributionGoal = signal<Goal | null>(null);
  readonly contributions = signal<GoalContribution[]>([]);
  readonly historyLoading = signal(false);
  readonly contributionError = signal('');
  readonly contributionSuccess = signal('');
  readonly confirmingRemoval = signal<string | null>(null);
  readonly today = localDate();
  readonly contributionForm = this.fb.nonNullable.group({
    amount: [0, [Validators.required, Validators.min(0.01), Validators.max(100000000)]],
    date: [localDate(), Validators.required],
    note: ['', Validators.maxLength(200)],
  });
  readonly goals = signal<Goal[]>([]);
  readonly projection = signal<Projection | null>(null);
  readonly calculating = signal(false);
  readonly calcError = signal('');
  readonly loading = signal(true);
  readonly error = signal('');
  readonly success = signal('');
  readonly editing = signal(false);
  readonly selected = signal<Goal | null>(null);
  readonly deleting = signal<Goal | null>(null);
  readonly saving = signal(false);
  readonly formError = signal('');
  readonly calculator = this.fb.nonNullable.group(
    {
      currentAge: [
        30,
        [Validators.required, Validators.min(18), Validators.max(90), Validators.pattern(/^\d+$/)],
      ],
      retirementAge: [
        65,
        [Validators.required, Validators.min(19), Validators.max(100), Validators.pattern(/^\d+$/)],
      ],
      currentSavings: [0, [Validators.required, Validators.min(0), Validators.max(100000000)]],
      monthlyContribution: [500, [Validators.required, Validators.min(0), Validators.max(100000)]],
      annualReturn: [6, [Validators.required, Validators.min(0), Validators.max(20)]],
      targetAmount: [1000000, [Validators.required, Validators.min(1), Validators.max(100000000)]],
    },
    { validators: retirementAgeValidator },
  );
  readonly form = this.fb.nonNullable.group({
    name: ['', [Validators.required, Validators.maxLength(100)]],
    targetAmount: [10000, [Validators.required, Validators.min(1), Validators.max(100000000)]],
    currentAmount: [0, [Validators.required, Validators.min(0), Validators.max(100000000)]],
    targetDate: ['', Validators.required],
    monthlyContribution: [500, [Validators.required, Validators.min(0), Validators.max(100000)]],
    expectedReturn: [6, [Validators.required, Validators.min(0), Validators.max(20)]],
  });
  constructor() {
    void this.load();
  }
  async load(): Promise<void> {
    this.loading.set(true);
    this.error.set('');
    try {
      this.goals.set(await this.api.goals());
    } catch (error) {
      this.error.set(errorMessage(error));
    } finally {
      this.loading.set(false);
    }
  }
  async calculate(): Promise<void> {
    this.calculator.markAllAsTouched();
    if (this.calculator.invalid) return;
    this.calculating.set(true);
    this.calcError.set('');
    try {
      this.projection.set(await this.api.calculate(this.calculator.getRawValue()));
    } catch (error) {
      this.calcError.set(errorMessage(error));
    } finally {
      this.calculating.set(false);
    }
  }
  projectionValues(): number[] {
    return this.projection()?.yearlyProjection.map((i) => i.balance) ?? [];
  }
  projectionLabels(): string[] {
    const points = this.projection()?.yearlyProjection ?? [];
    return points
      .filter(
        (_, i) => i === 0 || i === points.length - 1 || i % Math.ceil(points.length / 5) === 0,
      )
      .map((p) => `Age ${p.age}`);
  }
  percent(goal: Goal): number {
    return Math.min(100, (goal.currentAmount / goal.targetAmount) * 100);
  }
  invalid(name: 'name' | 'targetDate'): boolean {
    return this.form.controls[name].invalid && this.form.controls[name].touched;
  }
  open(goal?: Goal): void {
    this.selected.set(goal ?? null);
    this.form.reset({
      name: goal?.name ?? '',
      targetAmount: goal?.targetAmount ?? 10000,
      currentAmount: goal?.currentAmount ?? 0,
      targetDate: goal?.targetDate ?? '',
      monthlyContribution: goal?.monthlyContribution ?? 250,
      expectedReturn: goal?.expectedReturn ?? 0,
    });
    this.formError.set('');
    this.editing.set(true);
  }
  async save(): Promise<void> {
    this.form.markAllAsTouched();
    if (this.form.invalid) return;
    this.saving.set(true);
    try {
      await this.api.save('/goals', this.form.getRawValue(), this.selected()?.id);
      this.editing.set(false);
      this.success.set('Your savings goal is saved.');
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
      await this.api.remove('/goals', this.deleting()!.id);
      this.deleting.set(null);
      this.success.set('Your goal was removed.');
      await this.load();
    } catch (error) {
      this.formError.set(errorMessage(error));
    } finally {
      this.saving.set(false);
    }
  }
  async openContributions(goal: Goal): Promise<void> {
    this.contributionGoal.set(goal);
    this.contributions.set([]);
    this.contributionError.set('');
    this.contributionSuccess.set('');
    this.confirmingRemoval.set(null);
    this.contributionForm.reset({ amount: 0, date: localDate(), note: '' });
    await this.loadContributions();
  }
  async loadContributions(): Promise<void> {
    this.historyLoading.set(true);
    try {
      this.contributions.set(await this.api.contributions(this.contributionGoal()!.id));
    } catch (error) {
      this.contributionError.set(errorMessage(error));
    } finally {
      this.historyLoading.set(false);
    }
  }
  private updateGoal(goal: Goal): void {
    this.goals.update((goals) => goals.map((g) => (g.id === goal.id ? goal : g)));
    this.contributionGoal.set(goal);
  }
  async contribute(): Promise<void> {
    this.contributionForm.markAllAsTouched();
    if (this.contributionForm.invalid || this.contributionForm.controls.date.value > this.today) {
      this.contributionError.set(
        'Enter a positive amount and a contribution date that is today or earlier.',
      );
      return;
    }
    this.saving.set(true);
    this.contributionError.set('');
    try {
      this.updateGoal(
        await this.api.contribute(this.contributionGoal()!.id, this.contributionForm.getRawValue()),
      );
      this.contributionSuccess.set('Your savings contribution is recorded.');
      this.contributionForm.reset({ amount: 0, date: localDate(), note: '' });
      await this.loadContributions();
    } catch (error) {
      this.contributionError.set(errorMessage(error));
    } finally {
      this.saving.set(false);
    }
  }
  async removeContribution(id: string): Promise<void> {
    this.saving.set(true);
    this.contributionError.set('');
    try {
      this.updateGoal(await this.api.removeContribution(this.contributionGoal()!.id, id));
      this.confirmingRemoval.set(null);
      this.contributionSuccess.set('Contribution removed and goal progress updated.');
      await this.loadContributions();
    } catch (error) {
      this.contributionError.set(errorMessage(error));
    } finally {
      this.saving.set(false);
    }
  }
}
