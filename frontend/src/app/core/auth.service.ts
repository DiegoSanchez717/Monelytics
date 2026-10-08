import { HttpClient } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { catchError, firstValueFrom, of, tap } from 'rxjs';
import { User } from './models';
import { CsrfService } from './csrf.service';
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly csrf = inject(CsrfService);
  readonly user = signal<User | null>(null);
  private loaded = false;
  private pending?: Promise<User | null>;
  session(): Promise<User | null> {
    if (this.loaded) return Promise.resolve(this.user());
    return (this.pending ??= firstValueFrom(
      this.http.get<User>('/api/auth/me').pipe(
        tap((user) => this.user.set(user)),
        catchError(() => of(null)),
      ),
    ).then((user) => {
      this.loaded = true;
      return user;
    }));
  }
  async login(value: { email: string; password: string; code?: string }): Promise<User> {
    return this.accept(await firstValueFrom(this.http.post<User>('/api/auth/login', value)));
  }
  async register(value: {
    firstName: string;
    lastName: string;
    email: string;
    password: string;
  }): Promise<User> {
    const { firstName, lastName, email, password } = value;
    return this.accept(
      await firstValueFrom(
        this.http.post<User>('/api/auth/register', {
          firstName,
          lastName,
          email,
          password,
        }),
      ),
    );
  }
  expire(): void {
    this.user.set(null);
    this.loaded = true;
    this.csrf.clear();
  }
  private accept(user: User): User {
    this.user.set(user);
    this.loaded = true;
    this.csrf.clear();
    return user;
  }
  async logout(): Promise<void> {
    await firstValueFrom(this.http.post('/api/auth/logout', {}));
    this.user.set(null);
    this.loaded = true;
    this.csrf.clear();
  }
  async profile(value: { firstName: string; lastName: string }): Promise<void> {
    this.user.set(await firstValueFrom(this.http.put<User>('/api/settings', value)));
  }
  setupMfa(password: string): Promise<{ secret: string; otpAuthUri: string }> {
    return firstValueFrom(
      this.http.post<{ secret: string; otpAuthUri: string }>('/api/auth/mfa/setup', { password }),
    );
  }
  async mfa(action: 'enable' | 'disable', password: string, code: string): Promise<void> {
    this.user.set(
      await firstValueFrom(this.http.post<User>(`/api/auth/mfa/${action}`, { password, code })),
    );
  }
}
