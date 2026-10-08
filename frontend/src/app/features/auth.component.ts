import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { AuthService } from '../core/auth.service';
import { errorMessage } from '../core/api.service';
import { IconComponent } from '../shared/icon.component';
import { passwordByteLimit } from '../core/validators';
@Component({
  selector: 'wp-auth',
  imports: [ReactiveFormsModule, RouterLink, IconComponent],
  templateUrl: './auth.component.html',
})
export class AuthComponent {
  private readonly fb = inject(FormBuilder);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly auth = inject(AuthService);
  readonly register = this.router.url.startsWith('/register');
  readonly busy = signal(false);
  readonly error = signal('');
  readonly showPassword = signal(false);
  readonly mfaRequired = signal(false);
  readonly form = this.fb.nonNullable.group({
    firstName: ['', this.register ? [Validators.required, Validators.maxLength(60)] : []],
    lastName: ['', this.register ? [Validators.required, Validators.maxLength(60)] : []],
    email: ['', [Validators.required, Validators.email]],
    password: [
      '',
      this.register
        ? [
            Validators.required,
            passwordByteLimit,
            Validators.minLength(12),
            Validators.pattern(/^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[^A-Za-z0-9]).+$/),
          ]
        : [Validators.required, passwordByteLimit],
    ],
    code: [''],
    consent: [false, this.register ? [Validators.requiredTrue] : []],
  });
  invalid(name: 'firstName' | 'lastName' | 'email' | 'password' | 'code' | 'consent'): boolean {
    return this.form.controls[name].invalid && this.form.controls[name].touched;
  }
  fillDemo(): void {
    this.form.patchValue({
      email: 'demo@wealthpath.dev',
      password: 'DemoPath!2026',
    });
  }
  async submit(): Promise<void> {
    this.form.markAllAsTouched();
    if (this.form.invalid || this.busy()) return;
    this.busy.set(true);
    this.error.set('');
    try {
      const value = this.form.getRawValue();
      if (this.register) {
        await this.auth.register(value);
      } else {
        await this.auth.login({
          email: value.email,
          password: value.password,
          ...(value.code ? { code: value.code } : {}),
        });
      }
      const returnUrl = this.route.snapshot.queryParamMap.get('returnUrl');
      await this.router.navigateByUrl(
        returnUrl?.startsWith('/') && !returnUrl.startsWith('//') ? returnUrl : '/dashboard',
      );
    } catch (error) {
      if (error instanceof HttpErrorResponse && error.error?.code === 'MFA_REQUIRED') {
        this.mfaRequired.set(true);
        this.form.controls.code.setValidators([Validators.required, Validators.pattern(/^\d{6}$/)]);
        this.form.controls.code.updateValueAndValidity();
        this.error.set('One more step: enter your authenticator code to sign in.');
      } else {
        this.error.set(errorMessage(error));
      }
    } finally {
      this.busy.set(false);
    }
  }
}
