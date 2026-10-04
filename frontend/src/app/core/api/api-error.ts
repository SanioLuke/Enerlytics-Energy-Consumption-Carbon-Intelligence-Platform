import { type HttpErrorResponse } from '@angular/common/http';

/**
 * RFC 9457 problem detail returned by the Enerlytics API.
 * Mirrors backend/src/main/java/com/enerlytics/common/api/Problem.java.
 */
export interface Problem {
  type?: string;
  title?: string;
  status?: number;
  detail?: string;
  instance?: string;
  code?: string;
  correlationId?: string;
  errors?: ApiFieldError[];
}

export interface ApiFieldError {
  field: string;
  code: string;
  message: string;
}

/**
 * Normalized error thrown by all API calls. Components and services should
 * only ever see ApiError — never raw HttpErrorResponse.
 */
export class ApiError extends Error {
  readonly status: number;
  readonly code: string;
  readonly detail: string;
  readonly correlationId?: string;
  readonly fieldErrors: ApiFieldError[];

  constructor(init: {
    status: number;
    code: string;
    detail: string;
    correlationId?: string;
    fieldErrors?: ApiFieldError[];
  }) {
    super(init.detail);
    this.name = 'ApiError';
    this.status = init.status;
    this.code = init.code;
    this.detail = init.detail;
    this.correlationId = init.correlationId;
    this.fieldErrors = init.fieldErrors ?? [];
  }

  get isValidation(): boolean {
    return this.status === 400 && this.fieldErrors.length > 0;
  }

  get isUnauthorized(): boolean {
    return this.status === 401;
  }

  get isForbidden(): boolean {
    return this.status === 403;
  }

  get isNotFound(): boolean {
    return this.status === 404;
  }

  static fromHttp(error: HttpErrorResponse): ApiError {
    // Network-level failure (offline, CORS, unreachable backend).
    if (error.status === 0 || error.error instanceof ProgressEvent) {
      return new ApiError({
        status: 0,
        code: 'NETWORK_ERROR',
        detail: 'Cannot reach the Enerlytics API. Check your connection and try again.',
      });
    }

    const body = error.error as Partial<Problem> | null;
    if (body && typeof body === 'object' && (body.title || body.detail || body.code)) {
      return new ApiError({
        status: body.status ?? error.status,
        code: body.code ?? 'API_ERROR',
        detail: body.detail ?? body.title ?? 'Request failed.',
        correlationId: body.correlationId,
        fieldErrors: body.errors ?? [],
      });
    }

    return new ApiError({
      status: error.status,
      code: 'HTTP_' + error.status,
      detail: error.message || `Request failed with status ${error.status}.`,
    });
  }
}
