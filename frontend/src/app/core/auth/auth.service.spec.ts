import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { type TokenResponse, type UserInfoResponse } from '../api/contracts';
import { AuthService } from './auth.service';
import { TokenStorage } from './token-storage';

const TOKENS: TokenResponse = {
  accessToken: 'access-1',
  refreshToken: 'refresh-1',
  tokenType: 'Bearer',
  expiresIn: 3600,
  defaultOrganizationId: 'org-1',
};

const USER: UserInfoResponse = {
  id: 'user-1',
  email: 'analyst@example.com',
  displayName: 'Ana Analyst',
  status: 'ACTIVE',
  organizations: [
    {
      organizationId: 'org-1',
      organizationKey: 'acme',
      organizationName: 'Acme Corp',
      roles: ['ENERGY_ANALYST'],
      permissions: ['analytics:read', 'carbon:read'],
    },
    {
      organizationId: 'org-2',
      organizationKey: 'globex',
      organizationName: 'Globex',
      roles: ['VIEWER'],
      permissions: ['analytics:read'],
    },
  ],
};

describe('AuthService', () => {
  let service: AuthService;
  let http: HttpTestingController;
  let storage: TokenStorage;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    service = TestBed.inject(AuthService);
    http = TestBed.inject(HttpTestingController);
    storage = TestBed.inject(TokenStorage);
  });

  afterEach(() => {
    http.verify();
    localStorage.clear();
  });

  it('logs in, stores tokens, loads profile, and selects the stored org', () => {
    let user: UserInfoResponse | undefined;
    service.login('analyst@example.com', 'secret').subscribe((u) => (user = u));

    http.expectOne((r) => r.url.endsWith('/v1/auth/login')).flush(TOKENS);
    http.expectOne((r) => r.url.endsWith('/v1/auth/me')).flush(USER);

    expect(user?.email).toBe('analyst@example.com');
    expect(storage.accessToken()).toBe('access-1');
    expect(service.isAuthenticated()).toBe(true);
    expect(service.activeOrganization()?.organizationId).toBe('org-1');
  });

  it('derives permissions from the active organization', () => {
    service.login('a@b.c', 'pw').subscribe();
    http.expectOne((r) => r.url.endsWith('/v1/auth/login')).flush(TOKENS);
    http.expectOne((r) => r.url.endsWith('/v1/auth/me')).flush(USER);

    expect(service.hasAuthority('analytics:read')).toBe(true);
    expect(service.hasAuthority('alert:write')).toBe(false);
    expect(service.hasAnyAuthority(['alert:write', 'carbon:read'])).toBe(true);
    expect(service.hasAnyAuthority([])).toBe(true);
  });

  it('switches the active organization and its permission set', () => {
    service.login('a@b.c', 'pw').subscribe();
    http.expectOne((r) => r.url.endsWith('/v1/auth/login')).flush(TOKENS);
    http.expectOne((r) => r.url.endsWith('/v1/auth/me')).flush(USER);

    service.selectOrganization('org-2');

    expect(service.activeOrganization()?.organizationName).toBe('Globex');
    expect(service.hasAuthority('carbon:read')).toBe(false);
  });

  it('reports unauthenticated when no token is stored', () => {
    let restored: boolean | undefined;
    service.restoreSession().subscribe((r) => (restored = r));

    expect(restored).toBe(false);
    expect(service.isAuthenticated()).toBe(false);
    http.expectNone((r) => r.url.endsWith('/v1/auth/me'));
  });

  it('restores an existing session via /me', () => {
    storage.save(TOKENS);

    let restored: boolean | undefined;
    service.restoreSession().subscribe((r) => (restored = r));
    http.expectOne((r) => r.url.endsWith('/v1/auth/me')).flush(USER);

    expect(restored).toBe(true);
    expect(service.user()?.displayName).toBe('Ana Analyst');
  });

  it('clears the session when /me rejects and refresh fails', () => {
    storage.save(TOKENS);

    let restored: boolean | undefined;
    service.restoreSession().subscribe((r) => (restored = r));
    http.expectOne((r) => r.url.endsWith('/v1/auth/me')).flush(
      { code: 'UNAUTHENTICATED', detail: 'Token expired' },
      { status: 401, statusText: 'Unauthorized' },
    );
    http.expectOne((r) => r.url.endsWith('/v1/auth/refresh')).flush(
      { code: 'UNAUTHENTICATED' },
      { status: 401, statusText: 'Unauthorized' },
    );

    expect(restored).toBe(false);
    expect(storage.accessToken()).toBeNull();
    expect(service.isAuthenticated()).toBe(false);
  });
});
