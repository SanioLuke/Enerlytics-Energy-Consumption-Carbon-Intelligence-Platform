import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  input,
  output,
} from '@angular/core';
import type { SiteResponse } from '../../core/api/contracts';
import { AuthService } from '../../core/auth/auth.service';
import { type ContextPeriod, ContextService } from '../../core/context/context.service';

const PERIODS: { value: ContextPeriod; label: string }[] = [
  { value: 'TODAY', label: 'Today' },
  { value: 'YESTERDAY', label: 'Yesterday' },
  { value: 'LAST_7_DAYS', label: 'Last 7 days' },
  { value: 'LAST_30_DAYS', label: 'Last 30 days' },
  { value: 'CUSTOM', label: 'Custom' },
];

/**
 * Executive dashboard context bar — organization, site, period presets,
 * custom date range, prior-period comparison toggle, manual refresh, and
 * the explicit last-updated indicator required by UX_SPEC §4.2.
 */
@Component({
  selector: 'app-dashboard-filter-bar',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="bar" role="search" aria-label="Dashboard filters">
      <div class="field">
        <label for="f-org">Organization</label>
        <select
          id="f-org"
          [value]="activeOrgId()"
          (change)="onOrg($event)"
        >
          @for (org of organizations(); track org.organizationId) {
            <option [value]="org.organizationId">
              {{ org.organizationName }}
            </option>
          }
        </select>
      </div>

      <div class="field">
        <label for="f-site">Site</label>
        <select
          id="f-site"
          [value]="context.scope().siteId ?? ''"
          (change)="onSite($event)"
        >
          <option value="">All sites</option>
          @for (site of sites(); track site.id) {
            <option [value]="site.id">{{ site.name }}</option>
          }
        </select>
      </div>

      <div class="field">
        <label for="f-period">Period</label>
        <select
          id="f-period"
          [value]="context.period()"
          (change)="onPeriod($event)"
        >
          @for (p of periods; track p.value) {
            <option [value]="p.value">{{ p.label }}</option>
          }
        </select>
      </div>

      @if (context.period() === 'CUSTOM') {
        <div class="field">
          <label for="f-from">From</label>
          <input
            id="f-from"
            type="date"
            [value]="context.customFrom() ?? ''"
            (change)="onCustom(fromInput.value, toInput.value)"
            #fromInput
          />
        </div>
        <div class="field">
          <label for="f-to">To</label>
          <input
            id="f-to"
            type="date"
            [value]="context.customTo() ?? ''"
            (change)="onCustom(fromInput.value, toInput.value)"
            #toInput
          />
        </div>
      }

      <div class="field">
        <label for="f-compare">Compare</label>
        <select
          id="f-compare"
          [value]="context.comparison()"
          (change)="onCompare($event)"
        >
          <option value="NONE">Off</option>
          <option value="PRIOR_PERIOD">Previous period</option>
        </select>
      </div>

      <div class="actions">
        @if (lastUpdated(); as stamp) {
          <span class="updated" aria-live="polite">
            Last updated {{ stamp }}
          </span>
        }
        <button
          type="button"
          class="refresh"
          (click)="refresh.emit()"
          [disabled]="loading()"
          aria-label="Refresh dashboard data"
        >
          <span class="material-icons" aria-hidden="true"
            [class.spinning]="loading()">refresh</span>
          Refresh
        </button>
      </div>
    </div>
  `,
  styles: `
    .bar {
      display: flex;
      align-items: flex-end;
      flex-wrap: wrap;
      gap: var(--ely-space-3);
      padding: var(--ely-space-3) var(--ely-space-4);
      background: var(--ely-surface);
      border: 1px solid var(--ely-border);
      border-radius: var(--ely-radius-md);
    }

    .field { display: flex; flex-direction: column; gap: var(--ely-space-1); }
    .field label {
      font: var(--ely-text-caption);
      text-transform: uppercase;
      letter-spacing: 0.04em;
      color: var(--ely-text-3);
    }
    .field select,
    .field input {
      height: 32px;
      padding: 0 var(--ely-space-2);
      border: 1px solid var(--ely-border-strong);
      border-radius: var(--ely-radius-sm);
      background: var(--ely-surface);
      color: var(--ely-text);
      font: var(--ely-text-body);
      min-width: 140px;
    }
    .field input[type='date'] { min-width: 140px; }
    .field select:focus,
    .field input:focus {
      outline: none;
      border-color: var(--ely-accent);
      box-shadow: 0 0 0 2px var(--ely-focus-ring);
    }

    .actions {
      display: flex;
      align-items: center;
      gap: var(--ely-space-3);
      margin-left: auto;
    }
    .updated { font: var(--ely-text-micro); color: var(--ely-text-3); white-space: nowrap; }

    .refresh {
      display: inline-flex;
      align-items: center;
      gap: var(--ely-space-2);
      height: 32px;
      padding: 0 var(--ely-space-3);
      border: 1px solid var(--ely-border-strong);
      border-radius: var(--ely-radius-sm);
      background: var(--ely-surface);
      color: var(--ely-text);
      font: var(--ely-text-body);
      cursor: pointer;
    }
    .refresh:hover:not(:disabled) { border-color: var(--ely-accent); color: var(--ely-accent); }
    .refresh:disabled { opacity: 0.5; cursor: not-allowed; }
    .refresh .material-icons { font-size: 16px; width: 16px; height: 16px; }
    .spinning { animation: spin 0.9s linear infinite; }
    @keyframes spin { to { transform: rotate(360deg); } }

    @media (max-width: 767px) {
      .bar { flex-direction: column; align-items: stretch; }
      .field select, .field input { width: 100%; }
      .actions { margin-left: 0; justify-content: space-between; }
    }
  `,
})
export class DashboardFilterBarComponent {
  protected readonly context = inject(ContextService);
  private readonly auth = inject(AuthService);

  readonly sites = input.required<SiteResponse[]>();
  readonly lastUpdated = input<string | null>(null);
  readonly loading = input(false);
  readonly refresh = output<void>();

  protected readonly periods = PERIODS;
  protected readonly organizations = computed(
    () => this.auth.user()?.organizations ?? [],
  );
  protected readonly activeOrgId = computed(
    () => this.auth.activeOrganization()?.organizationId ?? '',
  );

  protected onOrg(event: Event): void {
    const id = (event.target as HTMLSelectElement).value;
    this.auth.selectOrganization(id);
    // Changing tenant invalidates the facility scope.
    this.context.setScope(null);
  }

  protected onSite(event: Event): void {
    const id = (event.target as HTMLSelectElement).value;
    this.context.setScope(id || null);
  }

  protected onPeriod(event: Event): void {
    this.context.setPeriod((event.target as HTMLSelectElement).value as ContextPeriod);
  }

  protected onCompare(event: Event): void {
    this.context.setComparison(
      (event.target as HTMLSelectElement).value as 'NONE' | 'PRIOR_PERIOD',
    );
  }

  protected onCustom(from: string, to: string): void {
    if (from && to && to >= from) {
      this.context.setCustomRange(from, to);
    }
  }
}
