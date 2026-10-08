import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { AuthService } from './auth.service';
describe('authentication service', () => {
  let auth: AuthService;
  let http: HttpTestingController;
  const user = {
    id: 'u',
    firstName: 'Alex',
    lastName: 'Morgan',
    email: 'alex@example.com',
    role: 'USER',
    mfaEnabled: false,
  };
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    auth = TestBed.inject(AuthService);
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => http.verify());
  it('posts only the registration DTO and stores the returned session user', async () => {
    const value = {
      firstName: 'Alex',
      lastName: 'Morgan',
      email: 'alex@example.com',
      password: 'SecurePath!2026',
      consent: true,
      code: '',
    };
    const promise = auth.register(value);
    const req = http.expectOne('/api/auth/register');
    expect(req.request.body).toEqual({
      firstName: value.firstName,
      lastName: value.lastName,
      email: value.email,
      password: value.password,
    });
    req.flush(user);
    await promise;
    expect(auth.user()?.firstName).toBe('Alex');
  });
  it('coalesces initial session reads', async () => {
    const a = auth.session();
    const b = auth.session();
    http.expectOne('/api/auth/me').flush(user);
    expect(await a).toEqual(await b);
    http.expectNone('/api/auth/me');
  });
  it('clears local identity after successful logout', async () => {
    auth.user.set(user as Parameters<typeof auth.user.set>[0]);
    const promise = auth.logout();
    http.expectOne('/api/auth/logout').flush({});
    await promise;
    expect(auth.user()).toBeNull();
  });
});
