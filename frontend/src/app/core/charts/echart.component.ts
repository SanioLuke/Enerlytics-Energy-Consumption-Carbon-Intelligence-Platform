import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  type ElementRef,
  effect,
  inject,
  input,
  viewChild,
} from '@angular/core';
import type { EChartsCoreOption, ECharts } from 'echarts/core';
import type * as echartsCore from 'echarts/core';

type EChartsModule = typeof echartsCore;

/**
 * Thin ECharts wrapper. The library is dynamically imported so its ~1 MB
 * payload lands in the lazy feature chunk, not the initial bundle. The chart
 * instance resizes via ResizeObserver and is disposed on destroy — no leaks.
 *
 * Register the components a feature needs via ensureEChartsRegistered()
 * before creating charts (idempotent).
 */
@Component({
  selector: 'app-echart',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<div #host class="chart-host" role="img" [attr.aria-label]="ariaLabel()"></div>`,
  styles: `
    :host { display: block; width: 100%; height: 100%; }
    .chart-host { width: 100%; height: 100%; min-height: 240px; }
  `,
})
export class EchartComponent {
  readonly option = input.required<EChartsCoreOption>();
  readonly ariaLabel = input.required<string>();

  private readonly host = viewChild.required<ElementRef<HTMLElement>>('host');
  private readonly destroyRef = inject(DestroyRef);

  private chart: ECharts | null = null;
  private resizeObserver: ResizeObserver | null = null;
  private initPromise: Promise<void> | null = null;

  constructor() {
    effect(() => {
      const option = this.option();
      void this.render(option);
    });
    this.destroyRef.onDestroy(() => this.dispose());
  }

  private async render(option: EChartsCoreOption): Promise<void> {
    if (!this.initPromise) {
      this.initPromise = this.init();
    }
    await this.initPromise;
    this.chart?.setOption(option, { notMerge: true });
  }

  private async init(): Promise<void> {
    const echarts = await this.loadModule();
    const el = this.host().nativeElement;
    this.chart = echarts.init(el, undefined, { renderer: 'canvas' });

    this.resizeObserver = new ResizeObserver(() => this.chart?.resize());
    this.resizeObserver.observe(el);
  }

  private loadModule(): Promise<EChartsModule> {
    return ensureEChartsRegistered();
  }

  private dispose(): void {
    this.resizeObserver?.disconnect();
    this.resizeObserver = null;
    this.chart?.dispose();
    this.chart = null;
  }
}

let modulePromise: Promise<EChartsModule> | null = null;

/**
 * Registers the ECharts features Enerlytics dashboards use (tree-shaken via
 * echarts/core). Idempotent — safe to call from multiple components.
 */
export function ensureEChartsRegistered(): Promise<EChartsModule> {
  modulePromise ??= Promise.all([
    import('echarts/core'),
    import('echarts/charts'),
    import('echarts/components'),
    import('echarts/renderers'),
  ]).then(([core, charts, components, renderers]) => {
    core.use([
      charts.LineChart,
      charts.BarChart,
      components.GridComponent,
      components.TooltipComponent,
      components.LegendComponent,
      components.DataZoomComponent,
      components.MarkLineComponent,
      components.MarkAreaComponent,
      components.AriaComponent,
      renderers.CanvasRenderer,
    ]);
    return core;
  });
  return modulePromise;
}
