import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { type Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export type ApiQueryParams = Record<string, string | number | boolean | null | undefined>;

/**
 * Centralized HTTP entry point. All API traffic goes through this client so
 * base URL, parameter serialization, and header concerns stay in one place.
 * Auth attachment and error normalization are handled by interceptors.
 */
@Injectable({ providedIn: 'root' })
export class ApiClient {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = environment.apiBaseUrl;

  get<T>(path: string, params?: ApiQueryParams): Observable<T> {
    return this.http.get<T>(this.url(path), { params: this.toParams(params) });
  }

  post<T>(path: string, body: unknown, params?: ApiQueryParams): Observable<T> {
    return this.http.post<T>(this.url(path), body, { params: this.toParams(params) });
  }

  put<T>(path: string, body: unknown, params?: ApiQueryParams): Observable<T> {
    return this.http.put<T>(this.url(path), body, { params: this.toParams(params) });
  }

  delete<T>(path: string, params?: ApiQueryParams): Observable<T> {
    return this.http.delete<T>(this.url(path), { params: this.toParams(params) });
  }

  /**
   * Tenant-scoped path helper — every business endpoint lives under
   * /v1/organizations/{orgId}/...
   */
  orgPath(orgId: string, path: string): string {
    if (path === '') {
      return `/v1/organizations/${orgId}`;
    }
    return `/v1/organizations/${orgId}${path.startsWith('/') ? path : '/' + path}`;
  }

  private url(path: string): string {
    return `${this.baseUrl}${path.startsWith('/') ? path : '/' + path}`;
  }

  private toParams(params?: ApiQueryParams): HttpParams | undefined {
    if (!params) {
      return undefined;
    }
    let httpParams = new HttpParams();
    for (const [key, value] of Object.entries(params)) {
      if (value !== null && value !== undefined && value !== '') {
        httpParams = httpParams.set(key, String(value));
      }
    }
    return httpParams;
  }
}
