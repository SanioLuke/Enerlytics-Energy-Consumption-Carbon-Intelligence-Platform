import type { EChartsCoreOption } from 'echarts/core';

/**
 * Maps the Enerlytics design tokens (--ely-*) onto ECharts option fragments so
 * every chart inherits the design system without hardcoded palette drift.
 * Values are duplicated here (not read at runtime) because ECharts needs
 * concrete colors at option-build time and these tokens are static.
 */
export const CHART_COLORS = [
  '#0F766E', // teal — primary / actual
  '#64748B', // slate — comparison / baseline
  '#D97706', // amber
  '#7C3AED', // violet
  '#0284C7', // sky
  '#4D7C0F', // olive
  '#BE185D', // rose
  '#78716C', // gray fallback
] as const;

export const GRID_BASE = {
  top: 32,
  right: 16,
  bottom: 28,
  left: 56,
  containLabel: true,
} as const;

export const TOOLTIP_BASE = {
  trigger: 'axis' as const,
  backgroundColor: '#FFFFFF',
  borderColor: '#E4E2DE',
  borderWidth: 1,
  padding: [8, 12] as [number, number],
  textStyle: { color: '#201F1D', fontSize: 12 },
  axisPointer: {
    type: 'line' as const,
    lineStyle: { color: '#CFCCC6', type: 'dashed' as const },
  },
};

export const CHART_BASE: EChartsCoreOption = {
  color: [...CHART_COLORS],
  textStyle: {
    fontFamily: 'Inter, ui-sans-serif, system-ui, sans-serif',
    color: '#59554F',
    fontSize: 11,
  },
  grid: { ...GRID_BASE },
  legend: {
    right: 0,
    top: 0,
    icon: 'rect',
    itemWidth: 12,
    itemHeight: 3,
    itemGap: 16,
    textStyle: { color: '#59554F', fontSize: 11 },
  },
  tooltip: { ...TOOLTIP_BASE },
};

export const AXIS_BASE = {
  axisLine: { lineStyle: { color: '#E4E2DE' } },
  axisTick: { show: false },
  axisLabel: { color: '#8B857D', fontSize: 11 },
  splitLine: { lineStyle: { color: '#EFEEEB' } },
} as const;

/** y-axis fragment with a mandatory unit label per UX_SPEC. */
export function unitAxis(unit: string, name: string) {
  return {
    type: 'value' as const,
    name: `${name} (${unit})`,
    nameTextStyle: { color: '#8B857D', fontSize: 11, align: 'left' as const },
    ...AXIS_BASE,
  };
}

/** Accessible description required by WCAG/spec — ECharts aria component. */
export function aria(description: string) {
  return {
    aria: {
      enabled: true,
      decal: { show: true },
      label: { enabled: true, description },
    },
  };
}
