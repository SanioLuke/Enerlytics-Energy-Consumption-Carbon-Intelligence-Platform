import { HttpErrorResponse, type HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, switchMap, throwError } from 'rxjs';
import { ApiError } from '../api/api-error';
import { AuthService } from './auth.service';
import { TokenStorage } from './token-storage';

const AUTH_ENDPOINTS = ['/v1/auth/login', '/v1/auth/refresh', '/v1/auth/logout'];

const isAuthEndpoint = (url: string): boolean =>
  AUTH_ENDPOINTS.some((path) => url.includes(path));

/**
 * Attaches the bearer token to API requests. On a 401 from a business endpoint
 * it performs a single-flight token refresh and retries once; if the refresh
 * fails the session is dropped and the user is sent to login.
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const storage = inject(TokenStorage);
  const auth = inject(AuthService);

  const accessToken = storage.accessToken();
  const request =
    accessToken && !isAuthEndpoint(req.url)
      ? req.clone({ setHeaders: { Authorization: `Bearer ${accessToken}` } })
      : req;

  return next(request).pipe(
    catchError((error: unknown) => {
      const status =
        error instanceof HttpErrorResponse
          ? error.status
          : error instanceof ApiError
            ? error.status
            : -1;

      if (status === 401 && !isAuthEndpoint(req.url)) {
        return auth.refreshTokens().pipe(
          switchMap((tokens) =>
            next(
              req.clone({
                setHeaders: { Authorization: `Bearer ${tokens.accessToken}` },
              }),
            ),
          ),
          catchError((refreshError: unknown) => {
            auth.forceLogout();
            return throwError(() => refreshError);
          }),
        );
      }
      return throwError(() => error);
    }),
  );
};
