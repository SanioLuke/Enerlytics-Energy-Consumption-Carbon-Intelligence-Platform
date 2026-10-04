import { ChangeDetectionStrategy, Component, input } from '@angular/core';

/**
 * Analytics card shell — section title, optional unit subtitle, and a
 * last-updated stamp per the spec's explicit freshness requirement.
 */
@Component({
  selector: 'app-chart-card',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="ely-card card">
      <header class="header">
        <div class="titles">
          <h2 class="title">{{ title() }}</h2>
          @if (subtitle(); as sub) {
            <span class="subtitle">{{ sub }}</span>
          }
        </div>
        @if (updatedAt(); as stamp) {
          <span class="updated" title="Data last fetched">
            updated {{ stamp }}
          </span>
        }
      </header>
      <div class="body">
        <ng-content />
      </div>
    </section>
  `,
  styles: `
    .card { display: flex; flex-direction: column; min-height: 300px; }
    .header {
      display: flex;
      align-items: baseline;
      justify-content: space-between;
      gap: var(--ely-space-3);
      margin-bottom: var(--ely-space-3);
    }
    .titles { display: flex; align-items: baseline; gap: var(--ely-space-2); min-width: 0; flex-wrap: wrap; }
    .title { margin: 0; font: var(--ely-text-section); }
    .subtitle { font: var(--ely-text-micro); color: var(--ely-text-3); }
    .updated { font: var(--ely-text-micro); color: var(--ely-text-3); white-space: nowrap; }
    .body { flex: 1; min-height: 0; display: flex; flex-direction: column; }
  `,
})
export class ChartCardComponent {
  readonly title = input.required<string>();
  readonly subtitle = input<string | null>(null);
  readonly updatedAt = input<string | null>(null);
}
