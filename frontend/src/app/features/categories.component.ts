import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ApiService, errorMessage } from '../core/api.service';
import { Category } from '../core/models';
import { DialogComponent } from '../shared/dialog.component';
import { IconComponent } from '../shared/icon.component';
import { StateComponent } from '../shared/state.component';
@Component({
  selector: 'ml-categories',
  imports: [ReactiveFormsModule, DialogComponent, IconComponent, StateComponent],
  templateUrl: './categories.component.html',
})
export class CategoriesComponent {
  private readonly api = inject(ApiService);
  private readonly fb = inject(FormBuilder);
  readonly categories = signal<Category[]>([]);
  readonly loading = signal(true);
  readonly error = signal('');
  readonly success = signal('');
  readonly editing = signal(false);
  readonly selected = signal<Category | null>(null);
  readonly deleting = signal<Category | null>(null);
  readonly saving = signal(false);
  readonly formError = signal('');
  readonly form = this.fb.nonNullable.group({
    name: ['', [Validators.required, Validators.maxLength(80)]],
    type: ['EXPENSE'],
    color: ['#EF7A6C', [Validators.required, Validators.pattern(/^#[0-9a-fA-F]{6}$/)]],
  });
  constructor() {
    void this.load();
  }
  async load(): Promise<void> {
    this.loading.set(true);
    this.error.set('');
    try {
      this.categories.set(await this.api.categories());
    } catch (error) {
      this.error.set(errorMessage(error));
    } finally {
      this.loading.set(false);
    }
  }
  open(category?: Category): void {
    this.selected.set(category ?? null);
    this.form.reset({
      name: category?.name ?? '',
      type: category?.type ?? 'EXPENSE',
      color: category?.color ?? '#EF7A6C',
    });
    this.formError.set('');
    this.editing.set(true);
  }
  async save(): Promise<void> {
    this.form.markAllAsTouched();
    if (this.form.invalid) return;
    this.saving.set(true);
    this.formError.set('');
    try {
      await this.api.save('/categories', this.form.getRawValue(), this.selected()?.id);
      this.editing.set(false);
      this.success.set('Your category is saved.');
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
      await this.api.remove('/categories', this.deleting()!.id);
      this.deleting.set(null);
      this.success.set('Your category was removed.');
      await this.load();
    } catch (error) {
      this.formError.set(errorMessage(error));
    } finally {
      this.saving.set(false);
    }
  }
}
