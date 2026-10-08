import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { AuthService } from '../core/auth.service';
import { errorMessage } from '../core/api.service';
import { IconComponent } from '../shared/icon.component';
@Component({
  selector: 'ml-settings',
  imports: [ReactiveFormsModule, IconComponent],
  templateUrl: './settings.component.html',
})
export class SettingsComponent {
  readonly auth = inject(AuthService);
  private readonly fb = inject(FormBuilder);
  readonly busy = signal(false);
  readonly mfaBusy = signal(false);
  readonly error = signal('');
  readonly success = signal('');
  readonly showMfa = signal(false);
  readonly setup = signal<{ secret: string; otpAuthUri: string } | null>(null);
  readonly copied = signal(false);
  readonly profile = this.fb.nonNullable.group({
    firstName: [this.auth.user()?.firstName ?? '', [Validators.required, Validators.maxLength(80)]],
    lastName: [this.auth.user()?.lastName ?? '', [Validators.required, Validators.maxLength(80)]],
  });
  readonly mfa = this.fb.nonNullable.group({
    password: ['', [Validators.required, Validators.maxLength(72)]],
    code: [''],
  });
  async saveProfile(): Promise<void> {
    this.profile.markAllAsTouched();
    if (this.profile.invalid) return;
    this.busy.set(true);
    this.error.set('');
    try {
      await this.auth.profile(this.profile.getRawValue());
      this.success.set('Your profile is updated.');
    } catch (error) {
      this.error.set(errorMessage(error));
    } finally {
      this.busy.set(false);
    }
  }
  async submitMfa(): Promise<void> {
    const confirming = this.auth.user()?.mfaEnabled || !!this.setup();
    this.mfa.controls.code.setValidators(
      confirming ? [Validators.required, Validators.pattern(/^\d{6}$/)] : [],
    );
    this.mfa.controls.code.updateValueAndValidity();
    this.mfa.markAllAsTouched();
    if (this.mfa.invalid) return;
    this.mfaBusy.set(true);
    this.error.set('');
    this.success.set('');
    try {
      const v = this.mfa.getRawValue();
      if (!confirming) {
        this.setup.set(await this.auth.setupMfa(v.password));
      } else {
        const action = this.auth.user()?.mfaEnabled ? 'disable' : 'enable';
        await this.auth.mfa(action, v.password, v.code);
        this.success.set(
          action === 'enable'
            ? 'Two-step verification is enabled. Use your authenticator when you sign in.'
            : 'Two-step verification is disabled.',
        );
        this.cancelMfa();
      }
    } catch (error) {
      this.error.set(errorMessage(error));
    } finally {
      this.mfaBusy.set(false);
    }
  }
  cancelMfa(): void {
    this.showMfa.set(false);
    this.setup.set(null);
    this.mfa.reset();
    this.copied.set(false);
  }
  async copySecret(): Promise<void> {
    try {
      await navigator.clipboard.writeText(this.setup()?.secret ?? '');
      this.copied.set(true);
    } catch {
      this.error.set('Copy is unavailable. Select the setup key and copy it manually.');
    }
  }
}
