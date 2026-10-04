import { type HttpErrorResponse, type HttpInterceptorFn } from '@angular/common/http';
import { catchError, throwError } from 'rxjs';
import { ApiError } from './api-error';

/**
 * Maps every HttpErrorResponse into a normalized ApiError (RFC 9457 aware).
 * Registered last so it also wraps errors surfaced by the auth interceptor's
 * token-refresh retry.
 */
export const apiErrorInterceptor: HttpInterceptorFn = (req, next) =>
  next(req).pipe(
    catchError((error: unknown) =>
      throwError(() =>
        error instanceof ApiError
          ? error
          : ApiError.fromHttp(error as HttpErrorResponse),
      ),
    ),
  );
