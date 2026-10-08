import { TestBed } from '@angular/core/testing';
import {
  ActivatedRouteSnapshot,
  Router,
  RouterStateSnapshot,
  provideRouter,
} from '@angular/router';
import { authGuard, adminGuard } from './auth.guard';
import { AuthService } from './auth.service';
describe('protected route guards', () => {
  const session = vi.fn();
  beforeEach(() => {
    session.mockReset();
    TestBed.configureTestingModule({
      providers: [provideRouter([]), { provide: AuthService, useValue: { session } }],
    });
  });
  it('preserves the destination when a session is absent', async () => {
    session.mockResolvedValue(null);
    const result = await TestBed.runInInjectionContext(() =>
      authGuard({} as ActivatedRouteSnapshot, { url: '/transactions' } as RouterStateSnapshot),
    );
    expect(TestBed.inject(Router).serializeUrl(result as ReturnType<Router['createUrlTree']>)).toBe(
      '/login?returnUrl=%2Ftransactions',
    );
  });
  it('allows an authenticated user', async () => {
    session.mockResolvedValue({ role: 'USER' });
    expect(
      await TestBed.runInInjectionContext(() =>
        authGuard({} as ActivatedRouteSnapshot, { url: '/accounts' } as RouterStateSnapshot),
      ),
    ).toBe(true);
  });
  it('blocks regular users from audit routes', async () => {
    session.mockResolvedValue({ role: 'USER' });
    const result = await TestBed.runInInjectionContext(() =>
      adminGuard({} as ActivatedRouteSnapshot, {} as RouterStateSnapshot),
    );
    expect(TestBed.inject(Router).serializeUrl(result as ReturnType<Router['createUrlTree']>)).toBe(
      '/dashboard',
    );
  });
  it('allows administrators', async () => {
    session.mockResolvedValue({ role: 'ADMIN' });
    expect(
      await TestBed.runInInjectionContext(() =>
        adminGuard({} as ActivatedRouteSnapshot, {} as RouterStateSnapshot),
      ),
    ).toBe(true);
  });
});
