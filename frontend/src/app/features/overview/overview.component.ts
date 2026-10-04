import { DatePipe } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  computed,
  inject,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { interval } from 'rxjs';
import { type ApiError } from '../../core/api/api-error';
import { EchartComponent } from '../../core/charts/echart.component';
import { ContextService } from '../../core/context/context.service';
import { EmptyStateComponent } from '../../core/ui/empty-state.component';
import { ErrorStateComponent } from '../../core/ui/error-state.component';
import { LoadingStateComponent } from '../../core/ui/loading-state.component';
import { ChartCardComponent } from './chart-card.component';
import {
  carbonOption,
  consumptionOption,
  costOption,
  demandOption,
  siteEnergyOption,
} from './chart-options';
import { DashboardFilterBarComponent } from './dashboard-filter-bar.component';
import { DashboardDataService } from './dashboard-data.service';
import type { DashboardData, DashboardSection } from './dashboard.models';
import { KpiTileComponent } from './kpi-tile.component';

/** Live-metric refresh cadence per UX_SPEC §4.2. */
const AUTO_REFRESH_MS = 60_000;

/**
 * Executive overview — real backend data via DashboardDataService's cohesive
 * query model. Every panel reads from the same resolved query; sections
 * degrade independently (a failed carbon call does not blank the energy
 * charts).
 */
@Component({
  selector: 'app-overview',
  changeDetection: ChangeDetectionStrategy.OnPush,
  providers: [DashboardDataService],
  imports: [
    DatePipe,
    EchartComponent,
    ChartCardComponent,
    DashboardFilterBarComponent,
    EmptyStateComponent,
    ErrorStateComponent,
    KpiTileComponent,
    LoadingStateComponent,
  ],
  template: `
    <div class="page">
      <header class="page-header">
        <div>
          <h1 class="title">Overview</h1>
          <p class="subtitle">Portfolio health at a glance</p>
        </div>
      </header>

      <app-dashboard-filter-bar
        [sites]="sites()"
        [lastUpdated]="lastUpdated()"
        [loading]="loading()"
        (refresh)="dataService.refresh()"
      />

      @if (loading()) {
        <app-loading-state variant="page" [tiles]="7" label="Loading overview" />
      } @else if (data(); as d) {
        <div class="kpi-strip" role="list" aria-label="Key performance indicators">
          <app-kpi-tile role="listitem" label="Current demand" [kpi]="d.kpis.currentDemand" [link]="'/analytics'" />
          <app-kpi-tile role="listitem" label="Today's consumption" [kpi]="d.kpis.todayConsumption" [link]="'/analytics'" />
          <app-kpi-tile role="listitem" label="Today's cost" [kpi]="d.kpis.todayCost" [link]="'/costs'" />
          <app-kpi-tile role="listitem" label="Today's carbon" [kpi]="d.kpis.todayCarbon" [link]="'/carbon'" />
          <app-kpi-tile role="listitem" label="Grid carbon intensity" [kpi]="d.kpis.gridIntensity" [link]="'/carbon'" />
          <app-kpi-tile role="listitem" label="Renewable energy" [kpi]="d.kpis.renewablePct" [link]="'/sustainability'" />
          <app-kpi-tile role="listitem" label="Active alerts" [kpi]="d.kpis.activeAlerts" [link]="'/alerts'" />
        </div>

        <div class="grid">
          <app-chart-card
            class="span-8"
            title="Consumption over time"
            [subtitle]="'kWh · ' + granularityLabel()"
            [updatedAt]="stamp(d.fetchedAt)"
          >
            @if (sectionError(d, 'consumption'); as err) {
              <app-error-state [error]="err" (retry)="dataService.refresh()" />
            } @else if (d.consumption.length === 0) {
              <app-empty-state heading="No consumption data" icon="query_stats">
                No energy aggregates exist for the selected scope and period.
              </app-empty-state>
            } @else {
              <app-echart
                [option]="consumptionChart()"
                ariaLabel="Energy consumption over time in kilowatt-hours"
              />
            }
          </app-chart-card>

          <app-chart-card
            class="span-4"
            title="Demand curve"
            subtitle="kW · today, hourly"
            [updatedAt]="stamp(d.fetchedAt)"
          >
            @if (sectionError(d, 'demand'); as err) {
              <app-error-state [error]="err" (retry)="dataService.refresh()" />
            } @else if (d.demand.length === 0) {
              <app-empty-state heading="No demand data" icon="show_chart">
                No hourly demand buckets exist for today yet.
              </app-empty-state>
            } @else {
              <app-echart
                [option]="demandChart()"
                ariaLabel="Hourly average and peak demand in kilowatts for today"
              />
            }
          </app-chart-card>

          <app-chart-card
            class="span-6"
            title="Energy by site"
            subtitle="kWh · selected period"
            [updatedAt]="stamp(d.fetchedAt)"
          >
            @if (siteScoped()) {
              <app-empty-state heading="Single site selected" icon="domain">
                Site comparison is shown at organization scope. Clear the site
                filter to compare all sites.
              </app-empty-state>
            } @else if (sectionError(d, 'siteEnergy'); as err) {
              <app-error-state [error]="err" (retry)="dataService.refresh()" />
            } @else if (d.siteEnergy.length === 0) {
              <app-empty-state heading="No sites reporting" icon="domain">
                No active sites returned energy data for this period.
              </app-empty-state>
            } @else {
              <app-echart
                [option]="siteEnergyChart()"
                ariaLabel="Energy consumption by site in kilowatt-hours"
              />
            }
          </app-chart-card>

          <app-chart-card
            class="span-6"
            title="Carbon emissions trend"
            subtitle="kgCO₂e"
            [updatedAt]="stamp(d.fetchedAt)"
          >
            @if (sectionError(d, 'carbon'); as err) {
              <app-error-state [error]="err" (retry)="dataService.refresh()" />
            } @else if (d.carbonTrend.length === 0) {
              <app-empty-state heading="No emissions data" icon="cloud">
                Emissions buckets appear once carbon calculations cover this
                scope and period.
              </app-empty-state>
            } @else {
              <app-echart
                [option]="carbonChart()"
                ariaLabel="Carbon emissions trend in kilograms of CO2 equivalent"
              />
            }
          </app-chart-card>

          <app-chart-card
            class="span-6"
            title="Cost trend"
            [subtitle]="(d.currency ?? 'cost') + ' · selected period'"
            [updatedAt]="stamp(d.fetchedAt)"
          >
            @if (sectionError(d, 'cost'); as err) {
              <app-error-state [error]="err" (retry)="dataService.refresh()" />
            } @else if (d.costTrend.length === 0) {
              <app-empty-state heading="No cost data" icon="payments">
                Cost buckets appear once a tariff is assigned to this scope.
              </app-empty-state>
            } @else {
              <app-echart
                [option]="costChart()"
                ariaLabel="Energy cost trend"
              />
            }
          </app-chart-card>

          <app-chart-card
            class="span-3"
            title="Sustainability target"
            [updatedAt]="stamp(d.fetchedAt)"
          >
            <app-empty-state heading="No targets configured" icon="flag">
              Sustainability targets are not yet modeled in the platform. When
              target tracking ships, progress will render here.
            </app-empty-state>
          </app-chart-card>

          <app-chart-card
            class="span-3"
            title="Recent alerts"
            [updatedAt]="stamp(d.fetchedAt)"
          >
            @if (sectionError(d, 'alerts'); as err) {
              <app-error-state [error]="err" (retry)="dataService.refresh()" />
            } @else if (d.alerts.open.length === 0 && d.alerts.acknowledged.length === 0) {
              <app-empty-state heading="No active alerts" icon="notifications_none">
                No open or acknowledged alerts for this organization.
              </app-empty-state>
            } @else {
              <ul class="alert-list" aria-label="Active alerts">
                @for (alert of d.alerts.open.slice(0, 6); track alert.id) {
                  <li class="alert-row" [class]="'sev-' + alert.severity.toLowerCase()">
                    <span class="alert-name">{{ alert.ruleName ?? alert.alertType }}</span>
                    <span class="alert-meta">
                      {{ alert.severity }} · {{ alert.triggeredAt | date: 'MMM d, HH:mm' : 'UTC' }}
                    </span>
                  </li>
                }
              </ul>
            }
          </app-chart-card>
        </div>
      } @else {
        <div class="ely-card">
          <app-empty-state heading="No organization selected" icon="business">
            Select an organization to load its executive overview.
          </app-empty-state>
        </div>
      }
    </div>
  `,
  styles: `
    .page { display: flex; flex-direction: column; gap: var(--ely-space-5); }

    .page-header {
      display: flex;
      align-items: flex-end;
      justify-content: space-between;
      gap: var(--ely-space-4);
    }
    .title { margin: 0; font: var(--ely-text-title); }
    .subtitle { margin: var(--ely-space-1) 0 0; font: var(--ely-text-small); color: var(--ely-text-2); }

    .kpi-strip {
      display: grid;
      grid-template-columns: repeat(7, minmax(0, 1fr));
      gap: var(--ely-space-3);
    }
    .kpi-strip > * { min-width: 0; }

    .grid {
      display: grid;
      grid-template-columns: repeat(12, minmax(0, 1fr));
      gap: var(--ely-space-5);
    }
    .span-3 { grid-column: span 3; }
    .span-4 { grid-column: span 4; }
    .span-6 { grid-column: span 6; }
    .span-8 { grid-column: span 8; }

    .alert-list {
      list-style: none;
      margin: 0;
      padding: 0;
      display: flex;
      flex-direction: column;
      overflow-y: auto;
    }
    .alert-row {
      display: flex;
      flex-direction: column;
      gap: 2px;
      padding: var(--ely-space-2) var(--ely-space-2) var(--ely-space-2) var(--ely-space-3);
      border-left: 3px solid var(--ely-border-strong);
      border-bottom: 1px solid var(--ely-border);
    }
    .alert-row.sev-critical { border-left-color: var(--ely-critical); }
    .alert-row.sev-warning { border-left-color: var(--ely-warning); }
    .alert-row.sev-info { border-left-color: var(--ely-info); }
    .alert-name { font: var(--ely-text-body); font-weight: 500; }
    .alert-meta { font: var(--ely-text-micro); color: var(--ely-text-3); }

    @media (max-width: 1439px) {
      .kpi-strip { grid-template-columns: repeat(4, minmax(0, 1fr)); }
      .span-3, .span-4, .span-6, .span-8 { grid-column: span 6; }
    }
    @media (max-width: 1023px) {
      .kpi-strip {
        display: flex;
        overflow-x: auto;
        scroll-snap-type: x mandatory;
      }
      .kpi-strip > * { flex: 0 0 180px; scroll-snap-align: start; }
      .span-3, .span-4, .span-6, .span-8 { grid-column: span 12; }
    }
  `,
})
export class OverviewComponent {
  protected readonly dataService = inject(DashboardDataService);
  private readonly context = inject(ContextService);
  private readonly destroyRef = inject(DestroyRef);

  constructor() {
    // Auto-refresh live metrics; takeUntilDestroyed ends the stream with the
    // component — no interval leak after navigation.
    interval(AUTO_REFRESH_MS)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.dataService.refresh());
  }

  protected readonly state = this.dataService.state;

  protected readonly loading = computed(() => this.state()?.status === 'loading');
  protected readonly data = computed(() => {
    const s = this.state();
    return s?.status === 'ready' ? s.data : null;
  });
  protected readonly query = computed(() => {
    const s = this.state();
    return s?.status === 'ready' ? s.query : null;
  });
  protected readonly sites = computed(() => this.data()?.sites ?? []);
  protected readonly siteScoped = computed(
    () => this.context.scope().siteId !== null,
  );
  protected readonly lastUpdated = computed(() => {
    const d = this.data();
    return d ? this.stamp(d.fetchedAt) : null;
  });
  protected readonly granularityLabel = computed(() => {
    const g = this.query()?.granularity;
    return g === 'MONTH' ? 'monthly' : g === 'DAY' ? 'daily' : 'hourly';
  });

  protected readonly consumptionChart = computed(() => {
    const d = this.requireData();
    return consumptionOption(
      d.consumption,
      d.consumptionPrevious,
      this.query()!.granularity,
      d.displayTimezone,
    );
  });
  protected readonly demandChart = computed(() =>
    demandOption(this.requireData().demand, this.requireData().displayTimezone),
  );
  protected readonly siteEnergyChart = computed(() =>
    siteEnergyOption(this.requireData().siteEnergy),
  );
  protected readonly carbonChart = computed(() => {
    const d = this.requireData();
    return carbonOption(
      d.carbonTrend,
      d.carbonTrendPrevious,
      this.query()!.granularity,
      d.displayTimezone,
    );
  });
  protected readonly costChart = computed(() => {
    const d = this.requireData();
    return costOption(
      d.costTrend,
      d.costTrendPrevious,
      d.currency ?? '',
      this.query()!.granularity,
      d.displayTimezone,
    );
  });

  protected sectionError(
    data: DashboardData,
    section: DashboardSection,
  ): ApiError | null {
    return data.sectionErrors[section] ?? null;
  }

  protected stamp(iso: string): string {
    return new Intl.DateTimeFormat('en-GB', {
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit',
      hour12: false,
      timeZone: 'UTC',
    }).format(new Date(iso)) + ' UTC';
  }

  private requireData(): DashboardData {
    const d = this.data();
    if (!d) {
      throw new Error('Dashboard data unavailable');
    }
    return d;
  }
}
