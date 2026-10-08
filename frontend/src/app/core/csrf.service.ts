import { HttpBackend, HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';
@Injectable({ providedIn: 'root' })
export class CsrfService {
  private readonly http = new HttpClient(inject(HttpBackend));
  private pending?: Promise<{ token: string; headerName: string }>;
  // Read the server's CSRF token through the raw backend to avoid recursive interception.
  token(): Promise<{ token: string; headerName: string }> {
    return (this.pending ??= firstValueFrom(
      this.http.get<{ token: string; headerName: string }>('/api/auth/csrf', {
        withCredentials: true,
      }),
    ).catch((error) => {
      this.pending = undefined;
      throw error;
    }));
  }
  clear(): void {
    this.pending = undefined;
  }
}
