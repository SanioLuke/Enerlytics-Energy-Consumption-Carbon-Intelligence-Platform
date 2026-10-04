import { LowerCasePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { ContextService } from '../../core/context/context.service';
import { LoadingStateComponent } from '../../core/ui/loading-state.component';
import { EmptyStateComponent } from '../../core/ui/empty-state.component';

/**
 * Executive overview — structural scaffold implementing the §4.2 wireframe
 * layout (context bar, KPI strip, card grid). Live API wiring lands in the
 * dashboard phase; for now the page demonstrates the state components and
 * the 12-col grid with the required card placement.
 */
@Component({
  selector: 'app-overview',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [LoadingStateComponent, EmptyStateComponent, LowerCasePipe],
  template: `
    <div class="page">
      <header class="page-header">
        <div>
          <h1 class="title">Overview</h1>
          <p class="subtitle">
            Portfolio health at a glance — {{ context.period() | lowercase }}
          </p>
        </div>
      </header>

      @if (loading()) {
        <app-loading-state variant="page" [tiles]="7" label="Loading overview" />
      } @else {
        <div class="kpi-strip">
          @for (kpi of kpis; track kpi.label) {
            <div class="ely-card kpi-tile">
              <span class="kpi-label">{{ kpi.label }}</span>
              <span class="kpi-value ely-numeric">—<small>{{ kpi.unit }}</small></span>
              <span class="kpi-meta ely-muted">Pending API wiring</span>
            </div>
          }
        </div>

        <div class="grid">
          <section class="ely-card span-8">
            <div class="ely-card__header">
              <h2 class="ely-card__title">Consumption over time</h2>
            </div>
            <app-empty-state heading="No consumption data yet" icon="query_stats">
              Energy aggregates will render here once the analytics API is wired to
              this dashboard.
            </app-empty-state>
          </section>

          <section class="ely-card span-4">
            <div class="ely-card__header">
              <h2 class="ely-card__title">Grid carbon intensity</h2>
            </div>
            <app-empty-state heading="Intensity unavailable" icon="eco">
              Current grid carbon intensity will appear here.
            </app-empty-state>
          </section>

          <section class="ely-card span-6">
            <div class="ely-card__header">
              <h2 class="ely-card__title">Demand curve</h2>
            </div>
            <app-empty-state heading="No demand data" icon="show_chart">
              The demand curve appears once meter telemetry is aggregated.
            </app-empty-state>
          </section>

          <section class="ely-card span-6">
            <div class="ely-card__header">
              <h2 class="ely-card__title">Site consumption comparison</h2>
            </div>
            <app-empty-state heading="No sites to compare" icon="domain">
              Site comparison appears when multiple sites report data.
            </app-empty-state>
          </section>

          <section class="ely-card span-4">
            <div class="ely-card__header">
              <h2 class="ely-card__title">Recent alerts</h2>
            </div>
            <app-empty-state heading="No active alerts" icon="notifications_none">
              Open and acknowledged alerts will be listed here.
            </app-empty-state>
          </section>

          <section class="ely-card span-4">
            <div class="ely-card__header">
              <h2 class="ely-card__title">Carbon emissions trend</h2>
            </div>
            <app-empty-state heading="No emissions data" icon="cloud">
              Emissions trend renders once carbon calculations run.
            </app-empty-state>
          </section>

          <section class="ely-card span-4">
            <div class="ely-card__header">
              <h2 class="ely-card__title">Cost trend</h2>
            </div>
            <app-empty-state heading="No cost data" icon="payments">
              Cost trend appears once tariffs price consumption.
            </app-empty-state>
          </section>
        </div>
      }
    </div>
  `,
  styles: `
    .page { display: flex; flex-direction: column; gap: var(--ely-space-6); }

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
      overflow-x: auto;
    }
    .kpi-tile {
      display: flex;
      flex-direction: column;
      gap: var(--ely-space-2);
      min-width: 148px;
      padding: var(--ely-space-4);
    }
    .kpi-label {
      font: var(--ely-text-caption);
      text-transform: uppercase;
      letter-spacing: 0.04em;
      color: var(--ely-text-3);
    }
    .kpi-value { font: var(--ely-text-kpi-dense); }
    .kpi-value small {
      font: var(--ely-text-small);
      color: var(--ely-text-3);
      margin-left: var(--ely-space-1);
    }
    .kpi-meta { font: var(--ely-text-micro); color: var(--ely-text-3); }

    .grid {
      display: grid;
      grid-template-columns: repeat(12, minmax(0, 1fr));
      gap: var(--ely-space-5);
    }
    .span-4 { grid-column: span 4; }
    .span-6 { grid-column: span 6; }
    .span-8 { grid-column: span 8; }

    @media (max-width: 1439px) {
      .kpi-strip { grid-template-columns: repeat(4, minmax(0, 1fr)); }
      .span-4, .span-6, .span-8 { grid-column: span 6; }
    }
    @media (max-width: 1023px) {
      .kpi-strip {
        display: flex;
        scroll-snap-type: x mandatory;
      }
      .kpi-tile { scroll-snap-align: start; flex: 0 0 180px; }
      .span-4, .span-6, .span-8 { grid-column: span 12; }
    }
  `,
})
export class OverviewComponent {
  protected readonly context = inject(ContextService);
  protected readonly loading = signal(false);

  protected readonly kpis = [
    { label: 'Current demand', unit: 'kW' },
    { label: "Today's consumption", unit: 'kWh' },
    { label: "Today's energy cost", unit: '' },
    { label: "Today's carbon emissions", unit: 'tCO₂e' },
    { label: 'Grid carbon intensity', unit: 'gCO₂e/kWh' },
    { label: 'Renewable energy', unit: '%' },
    { label: 'Active alerts', unit: '' },
  ];
}
