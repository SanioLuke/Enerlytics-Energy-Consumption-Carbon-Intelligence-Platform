import {
  ChangeDetectionStrategy,
  Component,
  computed,
  input,
} from '@angular/core';

/**
 * Skeleton-matched loading placeholder. Per UX_SPEC §3.5, skeletons must
 * mirror the real layout 1:1 — pick the variant matching the content shape.
 */
@Component({
  selector: 'app-loading-state',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="loading" role="status" [attr.aria-label]="label()">
      @switch (variant()) {
        @case ('kpi-strip') {
          <div class="kpi-strip">
            @for (tile of tileArray(); track $index) {
              <div class="kpi-tile">
                <div class="ely-skeleton skeleton-line" style="width: 60%"></div>
                <div class="ely-skeleton skeleton-value" style="width: 45%"></div>
              </div>
            }
          </div>
        }
        @case ('table') {
          <div class="ely-skeleton skeleton-bar"></div>
          @for (row of rowArray(); track $index) {
            <div class="ely-skeleton skeleton-line"></div>
          }
        }
        @case ('page') {
          <div class="ely-skeleton skeleton-heading"></div>
          <div class="kpi-strip">
            @for (tile of tileArray(); track $index) {
              <div class="kpi-tile">
                <div class="ely-skeleton skeleton-line" style="width: 60%"></div>
                <div class="ely-skeleton skeleton-value" style="width: 45%"></div>
              </div>
            }
          </div>
          <div class="ely-skeleton skeleton-chart"></div>
        }
        @default {
          <div class="ely-skeleton skeleton-chart"></div>
          <div class="ely-skeleton skeleton-line" style="width: 55%"></div>
        }
      }
      <span class="sr-only">{{ label() }}</span>
    </div>
  `,
  styles: `
    .loading { display: block; width: 100%; }
    .kpi-strip {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(160px, 1fr));
      gap: var(--ely-space-3);
      margin-bottom: var(--ely-space-4);
    }
    .kpi-tile {
      background: var(--ely-surface);
      border: 1px solid var(--ely-border);
      border-radius: var(--ely-radius-md);
      padding: var(--ely-space-4);
      display: flex;
      flex-direction: column;
      gap: var(--ely-space-3);
    }
    .skeleton-line { height: 12px; }
    .skeleton-value { height: 26px; }
    .skeleton-bar { height: 34px; margin-bottom: var(--ely-space-2); }
    .skeleton-heading { height: 22px; width: 240px; margin-bottom: var(--ely-space-5); }
    .skeleton-chart { height: 260px; margin-bottom: var(--ely-space-3); }
    .loading .skeleton-line { margin-bottom: var(--ely-space-2); }
    .sr-only {
      position: absolute; width: 1px; height: 1px;
      overflow: hidden; clip: rect(0 0 0 0); white-space: nowrap;
    }
  `,
})
export class LoadingStateComponent {
  readonly variant = input<'card' | 'table' | 'kpi-strip' | 'page'>('card');
  /** Number of skeleton rows for the table variant. */
  readonly rows = input(6);
  /** Number of skeleton tiles for kpi variants. */
  readonly tiles = input(4);
  readonly label = input('Loading…');

  protected readonly rowArray = computed(() =>
    Array.from({ length: Math.max(1, this.rows()) }),
  );
  protected readonly tileArray = computed(() =>
    Array.from({ length: Math.max(1, this.tiles()) }),
  );
}
