import { Injectable } from '@angular/core';
import { type TokenResponse } from '../api/contracts';

const ACCESS_KEY = 'ely.access_token';
const REFRESH_KEY = 'ely.refresh_token';
const EXPIRES_AT_KEY = 'ely.token_expires_at';

/**
 * Persistence boundary for auth tokens. Kept as a separate injectable so the
 * storage mechanism can be swapped (e.g. to a hardened cookie strategy) and so
 * tests can stub it without touching window.localStorage.
 */
@Injectable({ providedIn: 'root' })
export class TokenStorage {
  save(tokens: TokenResponse): void {
    const expiresAt = Date.now() + tokens.expiresIn * 1000;
    localStorage.setItem(ACCESS_KEY, tokens.accessToken);
    localStorage.setItem(REFRESH_KEY, tokens.refreshToken);
    localStorage.setItem(EXPIRES_AT_KEY, String(expiresAt));
  }

  accessToken(): string | null {
    return localStorage.getItem(ACCESS_KEY);
  }

  refreshToken(): string | null {
    return localStorage.getItem(REFRESH_KEY);
  }

  /** True when the stored access token is missing or within 30s of expiry. */
  isAccessTokenExpired(): boolean {
    const expiresAt = Number(localStorage.getItem(EXPIRES_AT_KEY) ?? 0);
    return !expiresAt || Date.now() >= expiresAt - 30_000;
  }

  clear(): void {
    localStorage.removeItem(ACCESS_KEY);
    localStorage.removeItem(REFRESH_KEY);
    localStorage.removeItem(EXPIRES_AT_KEY);
  }
}
