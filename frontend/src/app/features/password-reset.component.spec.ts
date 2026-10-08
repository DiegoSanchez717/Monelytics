import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter, Router } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { describe, it, expect, vi } from 'vitest';
import { PasswordResetComponent } from './password-reset.component';
import { ApiService } from '../core/api.service';

describe('Password recovery', () => {
  const token = 'a'.repeat(43);
  function setup(mode = 'request', linkToken = token) {
    const save = vi.fn().mockResolvedValue({ message: 'Recovery request received.' });
    TestBed.configureTestingModule({
      imports: [PasswordResetComponent],
      providers: [
        provideRouter([]),
        { provide: ApiService, useValue: { save } },
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: { data: { mode }, queryParamMap: convertToParamMap({ token: linkToken }) },
          },
        },
      ],
    });
    const component = TestBed.createComponent(PasswordResetComponent).componentInstance;
    return { component, save, router: TestBed.inject(Router) };
  }
  it('validates email before requesting a recovery link', async () => {
    const { component, save } = setup();
    component.form.patchValue({ email: 'invalid' });
    await component.submit();
    expect(save).not.toHaveBeenCalled();
    component.form.patchValue({ email: 'test@example.com' });
    await component.submit();
    expect(save).toHaveBeenCalledWith('/auth/forgot-password', { email: 'test@example.com' });
    expect(component.success()).toBe('Recovery request received.');
  });
  it('requires matching strong passwords and an intact token', async () => {
    const { component, save, router } = setup('confirm');
    vi.spyOn(router, 'navigate').mockResolvedValue(true);
    component.form.patchValue({
      password: 'StrongPassword!2026',
      repeat: 'DifferentPassword!2026',
    });
    await component.submit();
    expect(save).not.toHaveBeenCalled();
    expect(component.error()).toContain('match');
    component.form.patchValue({ repeat: 'StrongPassword!2026' });
    await component.submit();
    expect(save).toHaveBeenCalledWith('/auth/reset-password', {
      token,
      password: 'StrongPassword!2026',
    });
    expect(router.navigate).toHaveBeenCalledWith(
      [],
      expect.objectContaining({ replaceUrl: true, queryParams: {} }),
    );
    expect(component.form.controls.password.value).toBe('');
  });
  it('blocks invalid links', async () => {
    const { component, save } = setup('confirm', 'invalid');
    component.form.patchValue({ password: 'StrongPassword!2026', repeat: 'StrongPassword!2026' });
    await component.submit();
    expect(component.missingToken).toBe(true);
    expect(save).not.toHaveBeenCalled();
  });
  it('renders safe API errors and releases loading state for retries', async () => {
    const { component, save } = setup();
    save.mockRejectedValue(
      new HttpErrorResponse({ status: 429, error: { detail: 'Try again later.' } }),
    );
    component.form.patchValue({ email: 'test@example.com' });
    await component.submit();
    expect(component.error()).toBe('Try again later.');
    expect(component.busy()).toBe(false);
    expect(component.success()).toBe('');
  });
});
