import { Injectable, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { type Granularity } from '../api/contracts';
import { AuthService } from '../auth/auth.service';

export type ContextPeriod = 'TODAY' | 'YESTERDAY' | 'LAST_7_DAYS' | 'LAST_30_DAYS' | 'CUSTOM';

export interface ScopeSelection {
  siteId: string | null;
  buildingId: string | null;
}

const STORAGE_KEY = 'ely.context';

/**
 * Global context state — organization, facility scope, period, granularity —
 * mirrored into the URL query string so every analytical view is shareable
 * and refresh-safe (UX_SPEC §4.2 filter contract).
 *
 * Query params: site, building, period, granularity, compare.
 */
@Injectable({ providedIn: 'root' })
export class ContextService {
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly auth = inject(AuthService);

  private readonly scopeSignal = signal<ScopeSelection>({
    siteId: null,
    buildingId: null,
  });
  private readonly periodSignal = signal<ContextPeriod>('TODAY');
  private readonly granularitySignal = signal<Granularity>('HOUR');
  private readonly compareSignal = signal<'NONE' | 'PRIOR_PERIOD' | 'SAME_PERIOD_LAST_YEAR'>('NONE');

  readonly scope = this.scopeSignal.asReadonly();
  readonly period = this.periodSignal.asReadonly();
  readonly granularity = this.granularitySignal.asReadonly();
  readonly comparison = this.compareSignal.asReadonly();

  /** Current tenant org id — null until the session is restored. */
  readonly organizationId = computed(
    () => this.auth.activeOrganization()?.organizationId ?? null,
  );

  /** Query params representing the current context for routerLink/queryParams. */
  readonly queryParams = computed(() => {
    const scope = this.scopeSignal();
    return {
      site: scope.siteId,
      building: scope.buildingId,
      period: this.periodSignal() === 'TODAY' ? null : this.periodSignal(),
      granularity: this.granularitySignal() === 'HOUR' ? null : this.granularitySignal(),
      compare: this.compareSignal() === 'NONE' ? null : this.compareSignal(),
    };
  });

  /** Rehydrates scope/period from URL params; falls back to localStorage. */
  initFromRoute(): void {
    const params = this.route.snapshot.queryParamMap;
    const stored = this.loadStored();

    const siteId = params.get('site') ?? stored?.siteId ?? null;
    const buildingId = params.get('building') ?? stored?.buildingId ?? null;
    const period = this.parsePeriod(params.get('period')) ?? stored?.period ?? 'TODAY';
    const granularity =
      this.parseGranularity(params.get('granularity')) ?? stored?.granularity ?? 'HOUR';
    const compare =
      this.parseCompare(params.get('compare')) ?? stored?.compare ?? 'NONE';

    this.scopeSignal.set({ siteId, buildingId });
    this.periodSignal.set(period);
    this.granularitySignal.set(granularity);
    this.compareSignal.set(compare);
  }

  setScope(siteId: string | null, buildingId: string | null = null): void {
    // Clearing or changing the site invalidates the building selection.
    this.scopeSignal.set({ siteId, buildingId: siteId ? buildingId : null });
    this.commit();
  }

  setPeriod(period: ContextPeriod): void {
    this.periodSignal.set(period);
    this.commit();
  }

  setGranularity(granularity: Granularity): void {
    this.granularitySignal.set(granularity);
    this.commit();
  }

  setComparison(compare: 'NONE' | 'PRIOR_PERIOD' | 'SAME_PERIOD_LAST_YEAR'): void {
    this.compareSignal.set(compare);
    this.commit();
  }

  /** Writes context to the URL (shareable) and localStorage (persistence). */
  private commit(): void {
    this.persist();
    this.router.navigate([], {
      relativeTo: this.route,
      queryParams: this.queryParams(),
      queryParamsHandling: 'merge',
      replaceUrl: true,
    });
  }

  private persist(): void {
    const scope = this.scopeSignal();
    localStorage.setItem(
      STORAGE_KEY,
      JSON.stringify({
        siteId: scope.siteId,
        buildingId: scope.buildingId,
        period: this.periodSignal(),
        granularity: this.granularitySignal(),
        compare: this.compareSignal(),
      }),
    );
  }

  private loadStored():
    | (ScopeSelection & {
        period: ContextPeriod;
        granularity: Granularity;
        compare: 'NONE' | 'PRIOR_PERIOD' | 'SAME_PERIOD_LAST_YEAR';
      })
    | null {
    try {
      const raw = localStorage.getItem(STORAGE_KEY);
      return raw ? (JSON.parse(raw) as never) : null;
    } catch {
      return null;
    }
  }

  private parsePeriod(value: string | null): ContextPeriod | null {
    return value === 'TODAY' ||
      value === 'YESTERDAY' ||
      value === 'LAST_7_DAYS' ||
      value === 'LAST_30_DAYS' ||
      value === 'CUSTOM'
      ? value
      : null;
  }

  private parseGranularity(value: string | null): Granularity | null {
    return value === 'HOUR' || value === 'DAY' || value === 'MONTH' ? value : null;
  }

  private parseCompare(
    value: string | null,
  ): 'NONE' | 'PRIOR_PERIOD' | 'SAME_PERIOD_LAST_YEAR' | null {
    return value === 'NONE' ||
      value === 'PRIOR_PERIOD' ||
      value === 'SAME_PERIOD_LAST_YEAR'
      ? value
      : null;
  }
}
