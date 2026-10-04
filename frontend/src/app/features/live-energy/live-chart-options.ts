import type { EChartsCoreOption } from 'echarts/core';
import {
  AXIS_BASE,
  CHART_BASE,
  TOOLTIP_BASE,
  aria,
  unitAxis,
} from '../../core/charts/chart-theme';
import type { TrendPoint } from '../../core/api/contracts';

export function liveTrendOption(
  points: TrendPoint[],
  timeZone: string,
): EChartsCoreOption {
  const labels = points.map((p) =>
    new Intl.DateTimeFormat('en-GB', {
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit',
      hour12: false,
      timeZone,
    }).format(new Date(p.timestamp)),
  );
  return {
    ...CHART_BASE,
    ...aria('Live site demand trend in kilowatts'),
    tooltip: { ...TOOLTIP_BASE, valueFormatter: (v: unknown) => `${fmt(v)} kW` },
    xAxis: {
      type: 'category' as const,
      data: labels,
      boundaryGap: false,
      ...AXIS_BASE,
    },
    yAxis: unitAxis('kW', 'Demand'),
    series: [
      {
        name: 'Demand (kW)',
        type: 'line',
        data: points.map((p) => p.demandKw),
        showSymbol: false,
        connectNulls: false,
        lineStyle: { width: 2 },
        areaStyle: { opacity: 0.08 },
      },
    ],
    animation: false,
  };
}

function fmt(value: unknown): string {
  return typeof value === 'number' && Number.isFinite(value)
    ? new Intl.NumberFormat('en-US', { maximumFractionDigits: 1 }).format(value)
    : 'N/A';
}
