import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { securityInterceptor } from './security.interceptor';
import { AuthService } from './auth.service';
describe('session and CSRF interception', () => {
  let http: HttpTestingController;
  let client: HttpClient;
  const expire = vi.fn();
  const navigate = vi.fn();
  beforeEach(() => {
    expire.mockReset();
    navigate.mockReset();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([securityInterceptor])),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: { expire } },
        { provide: Router, useValue: { url: '/accounts', navigate } },
      ],
    });
    client = TestBed.inject(HttpClient);
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => http.verify());
  it('adds credentials to API reads without fetching CSRF', async () => {
    const promise = firstValueFrom(client.get('/api/accounts'));
    const req = http.expectOne('/api/accounts');
    expect(req.request.withCredentials).toBe(true);
    req.flush([]);
    await promise;
  });
  it('fetches CSRF before a mutation and sends the server header', async () => {
    const promise = firstValueFrom(client.post('/api/accounts', { name: 'IRA' }));
    const csrf = http.expectOne('/api/auth/csrf');
    expect(csrf.request.withCredentials).toBe(true);
    csrf.flush({ token: 'token-value', headerName: 'X-XSRF-TOKEN' });
    await new Promise((resolve) => setTimeout(resolve, 0));
    const req = http.expectOne('/api/accounts');
    expect(req.request.headers.get('X-XSRF-TOKEN')).toBe('token-value');
    expect(req.request.withCredentials).toBe(true);
    req.flush({ id: 'a' });
    await promise;
  });
  it('clears expired sessions and preserves the current protected route', async () => {
    const promise = firstValueFrom(client.get('/api/accounts')).catch((e) => e);
    http
      .expectOne('/api/accounts')
      .flush({ detail: 'Unauthorized' }, { status: 401, statusText: 'Unauthorized' });
    await promise;
    expect(expire).toHaveBeenCalledOnce();
    expect(navigate).toHaveBeenCalledWith(['/login'], {
      queryParams: { returnUrl: '/accounts' },
    });
  });
  it('does not redirect when MFA is required during login', async () => {
    const promise = firstValueFrom(client.post('/api/auth/login', {})).catch((e) => e);
    http.expectOne('/api/auth/csrf').flush({ token: 'token', headerName: 'X-XSRF-TOKEN' });
    await new Promise((resolve) => setTimeout(resolve, 0));
    http
      .expectOne('/api/auth/login')
      .flush({ code: 'MFA_REQUIRED' }, { status: 401, statusText: 'Unauthorized' });
    await promise;
    expect(expire).not.toHaveBeenCalled();
    expect(navigate).not.toHaveBeenCalled();
  });
  it('does not attach session credentials to unrelated URLs', async () => {
    const promise = firstValueFrom(client.get('/public.json'));
    const req = http.expectOne('/public.json');
    expect(req.request.withCredentials).toBe(false);
    req.flush({});
    await promise;
  });
});
