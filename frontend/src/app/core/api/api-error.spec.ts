import { HttpErrorResponse } from '@angular/common/http';
import { ApiError } from './api-error';

describe('ApiError', () => {
  it('maps an RFC 9457 problem body', () => {
    const response = new HttpErrorResponse({
      status: 400,
      error: {
        type: 'https://api.enerlytics.dev/problems/validation-failed',
        title: 'Validation failed',
        status: 400,
        detail: 'One or more fields are invalid.',
        instance: '/api/v1/organizations/x/tariffs',
        code: 'VALIDATION_FAILED',
        correlationId: 'corr-123',
        errors: [{ field: 'name', code: 'NotBlank', message: 'must not be blank' }],
      },
    });

    const error = ApiError.fromHttp(response);

    expect(error.status).toBe(400);
    expect(error.code).toBe('VALIDATION_FAILED');
    expect(error.detail).toBe('One or more fields are invalid.');
    expect(error.correlationId).toBe('corr-123');
    expect(error.isValidation).toBe(true);
    expect(error.fieldErrors[0]!.field).toBe('name');
  });

  it('maps network failures to status 0', () => {
    const response = new HttpErrorResponse({
      status: 0,
      error: new ProgressEvent('error'),
    });

    const error = ApiError.fromHttp(response);

    expect(error.status).toBe(0);
    expect(error.code).toBe('NETWORK_ERROR');
    expect(error.detail).toContain('Cannot reach');
  });

  it('falls back for non-problem bodies', () => {
    const response = new HttpErrorResponse({
      status: 502,
      statusText: 'Bad Gateway',
      error: 'upstream failed',
    });

    const error = ApiError.fromHttp(response);

    expect(error.status).toBe(502);
    expect(error.code).toBe('HTTP_502');
  });

  it('classifies auth errors', () => {
    const unauthorized = ApiError.fromHttp(
      new HttpErrorResponse({ status: 401, error: { code: 'UNAUTHENTICATED' } }),
    );
    const forbidden = ApiError.fromHttp(
      new HttpErrorResponse({ status: 403, error: { code: 'ACCESS_DENIED' } }),
    );

    expect(unauthorized.isUnauthorized).toBe(true);
    expect(unauthorized.isForbidden).toBe(false);
    expect(forbidden.isForbidden).toBe(true);
  });
});
