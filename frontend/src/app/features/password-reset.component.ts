import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { ApiService, errorMessage } from '../core/api.service';
import { passwordByteLimit } from '../core/validators';
import { IconComponent } from '../shared/icon.component';

@Component({
  selector: 'ml-password-reset',
  imports: [ReactiveFormsModule, RouterLink, IconComponent],
  templateUrl: './password-reset.component.html',
})
export class PasswordResetComponent {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly api = inject(ApiService);
  private readonly fb = inject(FormBuilder);
  readonly confirm = this.route.snapshot.data['mode'] === 'confirm';
  private readonly token = this.route.snapshot.queryParamMap.get('token') ?? '';
  readonly busy = signal(false);
  readonly error = signal('');
  readonly success = signal('');
  readonly missingToken = this.confirm && !/^[A-Za-z0-9_-]{43}$/.test(this.token);
  readonly form = this.fb.nonNullable.group({
    email: [
      '',
      this.confirm ? [] : [Validators.required, Validators.email, Validators.maxLength(254)],
    ],
    password: [
      '',
      this.confirm
        ? [
            Validators.required,
            Validators.minLength(12),
            passwordByteLimit,
            Validators.pattern(/^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[^A-Za-z0-9]).+$/),
          ]
        : [],
    ],
    repeat: ['', this.confirm ? [Validators.required] : []],
  });
  async submit(): Promise<void> {
    this.form.markAllAsTouched();
    this.error.set('');
    if (this.form.invalid || this.busy() || this.missingToken) return;
    const input = this.form.getRawValue();
    if (this.confirm && input.password !== input.repeat) {
      this.error.set('The passwords must match.');
      return;
    }
    this.busy.set(true);
    try {
      const result = await this.api.save<{ message: string }>(
        this.confirm ? '/auth/reset-password' : '/auth/forgot-password',
        this.confirm ? { token: this.token, password: input.password } : { email: input.email },
      );
      this.success.set(result.message);
      this.form.reset();
      if (this.confirm)
        await this.router.navigate([], {
          relativeTo: this.route,
          queryParams: {},
          replaceUrl: true,
        });
    } catch (error) {
      this.error.set(errorMessage(error));
    } finally {
      this.busy.set(false);
    }
  }
}
