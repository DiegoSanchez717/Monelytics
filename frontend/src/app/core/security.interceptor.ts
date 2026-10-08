import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, from, switchMap, throwError } from 'rxjs';
import { Router } from '@angular/router';
import { AuthService } from './auth.service';
import { CsrfService } from './csrf.service';
export const securityInterceptor: HttpInterceptorFn = (request, next) => {
  if (!request.url.startsWith('/api/')) return next(request);
  const authenticatedRequest = request.clone({ withCredentials: true });
  const auth = inject(AuthService);
  const router = inject(Router);
  const handleError = (error: unknown) => {
    if (
      error instanceof HttpErrorResponse &&
      error.status === 401 &&
      !request.url.startsWith('/api/auth/')
    ) {
      auth.expire();
      void router.navigate(['/login'], {
        queryParams: { returnUrl: router.url },
      });
    }
    return throwError(() => error);
  };
  if (['GET', 'HEAD', 'OPTIONS'].includes(request.method))
    return next(authenticatedRequest).pipe(catchError(handleError));
  const csrf = inject(CsrfService);
  // Session cookies stay HttpOnly; only the synchronizer CSRF token enters JavaScript.
  return from(csrf.token()).pipe(
    switchMap((value) =>
      next(
        authenticatedRequest.clone({
          setHeaders: { [value.headerName]: value.token },
        }),
      ),
    ),
    catchError(handleError),
  );
};
