import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { apiErrorInterceptor } from '../api/api-error.interceptor';
import { ApiError } from '../api/api-error';
import { type TokenResponse } from '../api/contracts';
import { authInterceptor } from './auth.interceptor';
import { TokenStorage } from './token-storage';

const TOKENS: TokenResponse = {
  accessToken: 'access-1',
  refreshToken: 'refresh-1',
  tokenType: 'Bearer',
  expiresIn: 3600,
  defaultOrganizationId: 'org-1',
};

const isMe = (r: { url: string }) => r.url.endsWith('/v1/auth/me');
const isRefresh = (r: { url: string }) => r.url.endsWith('/v1/auth/refresh');

describe('authInterceptor', () => {
  let http: HttpClient;
  let testing: HttpTestingController;
  let storage: TokenStorage;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([authInterceptor, apiErrorInterceptor])),
        provideHttpClientTesting(),
        provideRouter([]),
      ],
    });
    http = TestBed.inject(HttpClient);
    testing = TestBed.inject(HttpTestingController);
    storage = TestBed.inject(TokenStorage);
    // forceLogout navigates to /login — the test router has no routes.
    vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
  });

  afterEach(() => {
    testing.verify();
    localStorage.clear();
  });

  it('attaches the bearer token to API requests', () => {
    storage.save(TOKENS);
    http.get('http://localhost:8080/api/v1/auth/me').subscribe();

    const req = testing.expectOne(isMe);
    expect(req.request.headers.get('Authorization')).toBe('Bearer access-1');
    req.flush({});
  });

  it('does not attach the token to auth endpoints', () => {
    storage.save(TOKENS);
    http.post('http://localhost:8080/api/v1/auth/refresh', {}).subscribe();

    const req = testing.expectOne(isRefresh);
    expect(req.request.headers.get('Authorization')).toBeNull();
    req.flush(TOKENS);
  });

  it('refreshes once on 401 and retries with the new token', () => {
    storage.save(TOKENS);
    let body: unknown;
    http.get('http://localhost:8080/api/v1/auth/me').subscribe((b) => (body = b));

    testing.expectOne(isMe).flush(
      { code: 'UNAUTHENTICATED' },
      { status: 401, statusText: 'Unauthorized' },
    );
    testing.expectOne(isRefresh).flush({
      ...TOKENS,
      accessToken: 'access-2',
      refreshToken: 'refresh-2',
    });

    const retry = testing.expectOne(isMe);
    expect(retry.request.headers.get('Authorization')).toBe('Bearer access-2');
    retry.flush({ id: 'user-1' });

    expect(body).toEqual({ id: 'user-1' });
    expect(storage.accessToken()).toBe('access-2');
  });

  it('surfaces an ApiError when the refresh fails', () => {
    storage.save(TOKENS);
    let error: unknown;
    http.get('http://localhost:8080/api/v1/auth/me').subscribe({
      error: (e) => (error = e),
    });

    testing.expectOne(isMe).flush({}, { status: 401, statusText: 'Unauthorized' });
    testing.expectOne(isRefresh).flush(
      { code: 'UNAUTHENTICATED', detail: 'Refresh token revoked' },
      { status: 401, statusText: 'Unauthorized' },
    );

    expect(error).toBeInstanceOf(ApiError);
    expect((error as ApiError).status).toBe(401);
  });
});
