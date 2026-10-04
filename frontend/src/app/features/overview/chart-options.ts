import type { EChartsCoreOption } from 'echarts/core';
import type {
  AggregationGranularity,
  CarbonEmissionResponse,
  CostBucketResponse,
} from '../../core/api/contracts';
import { AXIS_BASE, CHART_BASE, GRID_BASE, TOOLTIP_BASE, aria, unitAxis } from '../../core/charts/chart-theme';
import type { SiteEnergySlice, TimeSeriesPoint } from './dashboard.models';

/**
 * ECharts option builders for the executive overview. Pure functions — the
 * component feeds derived view data in, options come out. All axes carry
 * explicit units per UX_SPEC; prior-period overlays are index-aligned and
 * rendered dashed slate (chart palette slot 2).
 */

const PRIOR_COLOR = '#64748B';
const PRIMARY_COLOR = '#0F766E';

function bucketLabel(iso: string, granularity: AggregationGranularity, timeZone: string): string {
  const date = new Date(iso);
  if (granularity === 'HOUR') {
    return new Intl.DateTimeFormat('en-GB', {
      hour: '2-digit',
      minute: '2-digit',
      hour12: false,
      timeZone,
    }).format(date);
  }
  if (granularity === 'MONTH') {
    return new Intl.DateTimeFormat('en-US', { month: 'short', timeZone }).format(date);
  }
  return new Intl.DateTimeFormat('en-US', {
    month: 'short',
    day: 'numeric',
    timeZone,
  }).format(date);
}

function categoryAxis(labels: string[]) {
  return {
    type: 'category' as const,
    data: labels,
    boundaryGap: false,
    ...AXIS_BASE,
  };
}

export function consumptionOption(
  points: TimeSeriesPoint[],
  previous: TimeSeriesPoint[] | null,
  granularity: AggregationGranularity,
  timeZone: string,
): EChartsCoreOption {
  const labels = points.map((p) => bucketLabel(p.bucketStart, granularity, timeZone));
  const series: Record<string, unknown>[] = [
    {
      name: 'Consumption',
      type: 'line',
      data: points.map((p) => p.value),
      showSymbol: false,
      connectNulls: false,
      lineStyle: { width: 2 },
      areaStyle: { opacity: 0.08 },
    },
  ];
  if (previous) {
    series.push({
      name: 'Previous period',
      type: 'line',
      // Index-aligned: prior buckets share the same axis positions.
      data: previous.map((p) => p.value),
      showSymbol: false,
      connectNulls: false,
      lineStyle: { width: 1.5, type: 'dashed', color: PRIOR_COLOR },
      itemStyle: { color: PRIOR_COLOR },
    });
  }

  return {
    ...CHART_BASE,
    ...aria(`Energy consumption over time in ${unitForGranularity(granularity)}`),
    tooltip: { ...TOOLTIP_BASE, valueFormatter: (v: unknown) => `${fmt(v)} kWh` },
    xAxis: categoryAxis(labels),
    yAxis: unitAxis('kWh', 'Consumption'),
    series,
  };
}

export function demandOption(
  points: TimeSeriesPoint[],
  timeZone: string,
): EChartsCoreOption {
  const labels = points.map((p) => bucketLabel(p.bucketStart, 'HOUR', timeZone));
  return {
    ...CHART_BASE,
    ...aria('Hourly average and peak demand in kilowatts for today'),
    tooltip: { ...TOOLTIP_BASE, valueFormatter: (v: unknown) => `${fmt(v)} kW` },
    xAxis: categoryAxis(labels),
    yAxis: unitAxis('kW', 'Demand'),
    series: [
      {
        name: 'Average demand',
        type: 'line',
        data: points.map((p) => p.value),
        showSymbol: false,
        connectNulls: false,
        lineStyle: { width: 2 },
        areaStyle: { opacity: 0.08 },
      },
      {
        name: 'Peak demand',
        type: 'line',
        data: points.map((p) => p.secondary ?? null),
        showSymbol: false,
        connectNulls: false,
        lineStyle: { width: 1.5, type: 'dashed' },
        itemStyle: { color: '#D97706' },
      },
    ],
  };
}

export function siteEnergyOption(slices: SiteEnergySlice[]): EChartsCoreOption {
  const sorted = slices.slice(0, 12);
  return {
    ...CHART_BASE,
    ...aria('Energy consumption by site in kilowatt-hours'),
    tooltip: {
      ...TOOLTIP_BASE,
      trigger: 'item',
      valueFormatter: (v: unknown) => `${fmt(v)} kWh`,
    },
    grid: { ...GRID_BASE, left: 120 },
    xAxis: {
      type: 'value' as const,
      name: 'kWh',
      nameTextStyle: { color: '#8B857D', fontSize: 11 },
      ...AXIS_BASE,
    },
    yAxis: {
      type: 'category' as const,
      data: sorted.map((s) => s.siteName),
      inverse: true,
      ...AXIS_BASE,
      splitLine: { show: false },
    },
    series: [
      {
        name: 'Energy',
        type: 'bar',
        data: sorted.map((s) => s.energyKwh),
        barMaxWidth: 18,
        itemStyle: { color: PRIMARY_COLOR, borderRadius: [0, 3, 3, 0] },
      },
    ],
  };
}

export function carbonOption(
  buckets: CarbonEmissionResponse[],
  previous: CarbonEmissionResponse[] | null,
  granularity: AggregationGranularity,
  timeZone: string,
): EChartsCoreOption {
  const labels = buckets.map((b) => bucketLabel(b.bucketStart, granularity, timeZone));
  const series: Record<string, unknown>[] = [
    {
      name: 'Emissions',
      type: 'line',
      // UNAVAILABLE buckets carry null emissions — rendered as gaps, not zero.
      data: buckets.map((b) => b.emissionsKgCo2Eq ?? null),
      showSymbol: false,
      connectNulls: false,
      lineStyle: { width: 2 },
      areaStyle: { opacity: 0.08 },
    },
  ];
  if (previous) {
    series.push({
      name: 'Previous period',
      type: 'line',
      data: previous.map((b) => b.emissionsKgCo2Eq ?? null),
      showSymbol: false,
      connectNulls: false,
      lineStyle: { width: 1.5, type: 'dashed', color: PRIOR_COLOR },
      itemStyle: { color: PRIOR_COLOR },
    });
  }
  return {
    ...CHART_BASE,
    ...aria('Carbon emissions trend in kilograms of CO2 equivalent'),
    tooltip: { ...TOOLTIP_BASE, valueFormatter: (v: unknown) => `${fmt(v)} kgCO₂e` },
    xAxis: categoryAxis(labels),
    yAxis: unitAxis('kgCO₂e', 'Emissions'),
    series,
  };
}

export function costOption(
  buckets: CostBucketResponse[],
  previous: CostBucketResponse[] | null,
  currency: string,
  granularity: AggregationGranularity,
  timeZone: string,
): EChartsCoreOption {
  const labels = buckets.map((b) => bucketLabel(b.bucketStart, granularity, timeZone));
  const series: Record<string, unknown>[] = [
    {
      name: 'Cost',
      type: 'line',
      data: buckets.map((b) => b.totalCost ?? null),
      showSymbol: false,
      connectNulls: false,
      lineStyle: { width: 2 },
      areaStyle: { opacity: 0.08 },
    },
  ];
  if (previous) {
    series.push({
      name: 'Previous period',
      type: 'line',
      data: previous.map((b) => b.totalCost ?? null),
      showSymbol: false,
      connectNulls: false,
      lineStyle: { width: 1.5, type: 'dashed', color: PRIOR_COLOR },
      itemStyle: { color: PRIOR_COLOR },
    });
  }
  return {
    ...CHART_BASE,
    ...aria(`Energy cost trend in ${currency}`),
    tooltip: { ...TOOLTIP_BASE, valueFormatter: (v: unknown) => `${fmt(v)} ${currency}` },
    xAxis: categoryAxis(labels),
    yAxis: unitAxis(currency, 'Cost'),
    series,
  };
}

function unitForGranularity(granularity: AggregationGranularity): string {
  return granularity === 'MONTH' ? 'kWh/month' : granularity === 'DAY' ? 'kWh/day' : 'kWh';
}

function fmt(value: unknown): string {
  return typeof value === 'number' && Number.isFinite(value)
    ? new Intl.NumberFormat('en-US', { maximumFractionDigits: 2 }).format(value)
    : 'N/A';
}
