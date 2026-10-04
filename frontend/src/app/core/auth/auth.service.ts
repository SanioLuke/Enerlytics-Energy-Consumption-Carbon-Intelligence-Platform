import { Injectable, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { type Observable, of, shareReplay, tap, throwError } from 'rxjs';
import { catchError, map, switchMap } from 'rxjs/operators';
import { ApiClient } from '../api/api-client';
import {
  type OrganizationMembership,
  type TokenResponse,
  type UserInfoResponse,
} from '../api/contracts';
import { TokenStorage } from './token-storage';

const ACTIVE_ORG_KEY = 'ely.active_org';

/**
 * Authentication and identity state.
 *
 * Holds the current user, active organization, and derived permission set as
 * signals. Token refresh is single-flight: concurrent 401s share one refresh
 * request.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly api = inject(ApiClient);
  private readonly tokens = inject(TokenStorage);
  private readonly router = inject(Router);

  private readonly userSignal = signal<UserInfoResponse | null>(null);
  private readonly readySignal = signal(false);
  private readonly activeOrgSignal = signal<OrganizationMembership | null>(null);
  private refreshInFlight: Observable<TokenResponse> | null = null;

  readonly user = this.userSignal.asReadonly();
  readonly ready = this.readySignal.asReadonly();
  readonly activeOrganization = this.activeOrgSignal.asReadonly();

  /** Permissions granted to the user within the active organization. */
  readonly permissions = computed<ReadonlySet<string>>(
    () => new Set(this.activeOrgSignal()?.permissions ?? []),
  );

  readonly isAuthenticated = computed(() => this.userSignal() !== null);

  /**
   * Restores session state on application start and guard checks.
   * Idempotent: once the session has been resolved this returns immediately
   * without re-probing /me on every navigation.
   */
  restoreSession(): Observable<boolean> {
    if (this.readySignal()) {
      return of(this.isAuthenticated());
    }
    if (!this.tokens.accessToken()) {
      this.readySignal.set(true);
      return of(false);
    }
    return this.loadProfile().pipe(
      map(() => true),
      catchError(() => {
        // Access token present but rejected — try one refresh before giving up.
        if (!this.tokens.refreshToken()) {
          this.clearSession();
          return of(false);
        }
        return this.refreshTokens().pipe(
          switchMap(() => this.loadProfile()),
          map(() => true),
          catchError(() => {
            this.clearSession();
            return of(false);
          }),
        );
      }),
    );
  }

  login(email: string, password: string): Observable<UserInfoResponse> {
    return this.api
      .post<TokenResponse>('/v1/auth/login', { email, password })
      .pipe(
        tap((tokens) => this.tokens.save(tokens)),
        switchMap(() => this.loadProfile()),
      );
  }

  /**
   * Rotates the refresh token. Single-flight: if a refresh is already running
   * the same observable is returned to every caller.
   */
  refreshTokens(): Observable<TokenResponse> {
    const refreshToken = this.tokens.refreshToken();
    if (!refreshToken) {
      return throwError(() => new Error('No refresh token available'));
    }
    if (!this.refreshInFlight) {
      this.refreshInFlight = this.api
        .post<TokenResponse>('/v1/auth/refresh', { refreshToken })
        .pipe(
          tap((tokens) => this.tokens.save(tokens)),
          shareReplay({ bufferSize: 1, refCount: false }),
        );
    }
    const inFlight = this.refreshInFlight;
    return inFlight.pipe(
      tap({ finalize: () => (this.refreshInFlight = null) }),
    );
  }

  logout(): Observable<void> {
    const refreshToken = this.tokens.refreshToken();
    this.clearSession();
    this.router.navigate(['/login']);
    if (!refreshToken) {
      return of(void 0);
    }
    // Best-effort revocation — a failed call must not block local logout.
    return this.api
      .post<void>('/v1/auth/logout', { refreshToken })
      .pipe(catchError(() => of(void 0)));
  }

  /** Drops local session state without notifying the backend. */
  forceLogout(): void {
    this.clearSession();
    this.router.navigate(['/login']);
  }

  selectOrganization(organizationId: string): void {
    const membership = this.userSignal()?.organizations.find(
      (o) => o.organizationId === organizationId,
    );
    if (membership) {
      this.activeOrgSignal.set(membership);
      localStorage.setItem(ACTIVE_ORG_KEY, membership.organizationId);
    }
  }

  hasAuthority(permission: string): boolean {
    return this.permissions().has(permission);
  }

  hasAnyAuthority(permissions: string[]): boolean {
    if (permissions.length === 0) {
      return true;
    }
    const granted = this.permissions();
    return permissions.some((p) => granted.has(p));
  }

  private loadProfile(): Observable<UserInfoResponse> {
    return this.api.get<UserInfoResponse>('/v1/auth/me').pipe(
      tap((user) => {
        this.userSignal.set(user);
        this.resolveActiveOrganization(user);
        this.readySignal.set(true);
      }),
    );
  }

  private resolveActiveOrganization(user: UserInfoResponse): void {
    if (user.organizations.length === 0) {
      this.activeOrgSignal.set(null);
      return;
    }
    const stored = localStorage.getItem(ACTIVE_ORG_KEY);
    const match =
      user.organizations.find((o) => o.organizationId === stored) ??
      user.organizations[0];
    this.activeOrgSignal.set(match);
  }

  private clearSession(): void {
    this.tokens.clear();
    this.userSignal.set(null);
    this.activeOrgSignal.set(null);
    localStorage.removeItem(ACTIVE_ORG_KEY);
    this.readySignal.set(true);
  }
}
