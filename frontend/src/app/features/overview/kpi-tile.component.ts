import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import type { KpiValue } from './dashboard.models';

/**
 * Executive KPI tile per UX_SPEC §3.5 — label, tabular value with explicit
 * unit, optional delta vs previous period, quality chip, deep-link. An
 * unavailable metric renders 'N/A', never a fabricated zero.
 */
@Component({
  selector: 'app-kpi-tile',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink],
  template: `
    <a
      class="tile"
      [routerLink]="link()"
      [attr.aria-label]="ariaLabel()"
    >
      <span class="label">{{ label() }}</span>
      <span class="value ely-numeric">
        @if (kpi().value !== null) {
          {{ formatted() }}<small>{{ kpi().unit }}</small>
        } @else {
          <span class="na">N/A</span>
        }
      </span>
      @if (kpi().deltaPct !== null && kpi().deltaPct !== undefined) {
        <span class="delta" [class.up]="kpi().deltaPct! > 0" [class.down]="kpi().deltaPct! < 0">
          {{ kpi().deltaPct! > 0 ? '▲' : kpi().deltaPct! < 0 ? '▼' : '—' }}
          {{ absDelta() }}% vs prior
        </span>
      }
      <span class="meta-row">
        @if (kpi().quality !== 'VALID') {
          <span class="chip" [class]="'chip-' + kpi().quality.toLowerCase()">
            {{ chipLabel() }}
          </span>
        }
        @if (kpi().meta; as meta) {
          <span class="meta">{{ meta }}</span>
        }
      </span>
    </a>
  `,
  styles: `
    .tile {
      display: flex;
      flex-direction: column;
      gap: var(--ely-space-2);
      min-width: 0;
      min-height: 118px;
      padding: var(--ely-space-4);
      background: var(--ely-surface);
      border: 1px solid var(--ely-border);
      border-radius: var(--ely-radius-md);
      text-decoration: none;
      color: var(--ely-text);
    }
    .tile:hover { border-color: var(--ely-accent-border); }
    .tile:focus-visible {
      outline: 2px solid var(--ely-accent);
      outline-offset: 1px;
    }

    .label {
      font: var(--ely-text-caption);
      text-transform: uppercase;
      letter-spacing: 0.04em;
      color: var(--ely-text-3);
      line-height: 1.25;
    }

    .value { font: var(--ely-text-kpi-dense); }
    .value small {
      font: var(--ely-text-small);
      color: var(--ely-text-3);
      margin-left: var(--ely-space-1);
    }
    .na { color: var(--ely-text-3); font: var(--ely-text-kpi-dense); }

    .delta { font: var(--ely-text-small); color: var(--ely-text-2); }
    .delta.up { color: var(--ely-critical); }
    .delta.down { color: var(--ely-success); }

    .meta-row {
      display: flex;
      align-items: center;
      gap: var(--ely-space-2);
      margin-top: auto;
      min-height: 20px;
    }
    .meta {
      font: var(--ely-text-micro);
      color: var(--ely-text-3);
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }

    .chip {
      display: inline-flex;
      align-items: center;
      height: 20px;
      padding: 0 var(--ely-space-2);
      border-radius: var(--ely-radius-sm);
      font: var(--ely-text-micro);
      font-weight: 600;
    }
    .chip-estimated {
      background: color-mix(in srgb, var(--ely-estimated) 10%, white);
      color: var(--ely-estimated);
    }
    .chip-partial {
      background: color-mix(in srgb, var(--ely-warning) 10%, white);
      color: var(--ely-warning);
    }
    .chip-unavailable {
      background: var(--ely-bg-subtle);
      color: var(--ely-text-3);
    }
  `,
})
export class KpiTileComponent {
  readonly label = input.required<string>();
  readonly kpi = input.required<KpiValue>();
  readonly link = input<string | null>(null);

  protected readonly formatted = computed(() => {
    const k = this.kpi();
    if (k.value === null) {
      return '';
    }
    return new Intl.NumberFormat('en-US', {
      minimumFractionDigits: k.decimals,
      maximumFractionDigits: k.decimals,
    }).format(k.value);
  });

  protected readonly absDelta = computed(() =>
    Math.abs(this.kpi().deltaPct ?? 0).toFixed(1),
  );

  protected readonly chipLabel = computed(() => {
    switch (this.kpi().quality) {
      case 'ESTIMATED':
        return 'EST';
      case 'PARTIAL':
        return 'PARTIAL';
      default:
        return 'N/A';
    }
  });

  protected readonly ariaLabel = computed(() => {
    const k = this.kpi();
    const value =
      k.value !== null ? `${this.formatted()} ${k.unit}`.trim() : 'unavailable';
    return `${this.label()}: ${value}`;
  });
}
