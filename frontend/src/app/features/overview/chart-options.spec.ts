import type {
  CarbonEmissionResponse,
  CostBucketResponse,
} from '../../core/api/contracts';
import type { SiteEnergySlice, TimeSeriesPoint } from './dashboard.models';
import {
  carbonOption,
  consumptionOption,
  costOption,
  demandOption,
  siteEnergyOption,
} from './chart-options';

interface LooseSeries {
  name?: string;
  data?: unknown[];
  type?: string;
}
interface LooseOption {
  series?: LooseSeries[];
  xAxis?: { data?: string[] };
  yAxis?: { name?: string } | { name?: string }[];
  aria?: { enabled?: boolean; label?: { description?: string } };
}

const asOption = (o: unknown): LooseOption => o as LooseOption;

const points = (values: (number | null)[]): TimeSeriesPoint[] =>
  values.map((v, i) => ({
    bucketStart: `2026-10-04T${String(i).padStart(2, '0')}:00:00Z`,
    value: v,
  }));

describe('consumptionOption', () => {
  it('maps points to a single kWh series with hourly axis labels', () => {
    const option = asOption(
      consumptionOption(points([10, 20, null]), null, 'HOUR', 'UTC'),
    );
    expect(option.series).toHaveLength(1);
    expect(option.series?.[0].data).toEqual([10, 20, null]);
    expect(option.series?.[0].name).toContain('kWh');
    expect(option.xAxis?.data).toHaveLength(3);
    expect(option.aria?.enabled).toBe(true);
  });

  it('adds a dashed previous-period overlay when comparison data exists', () => {
    const option = asOption(
      consumptionOption(
        points([10, 20]),
        points([8, 16]),
        'HOUR',
        'UTC',
      ),
    );
    expect(option.series).toHaveLength(2);
    expect(option.series?.[1].name).toBe('Previous period');
    expect(option.series?.[1].data).toEqual([8, 16]);
  });

  it('labels buckets in the site timezone, not UTC', () => {
    const option = asOption(
      consumptionOption(
        [{ bucketStart: '2026-01-15T02:30:00Z', value: 5 }],
        null,
        'HOUR',
        'America/New_York',
      ),
    );
    // 02:30 UTC → 21:30 previous day in New York
    expect(option.xAxis?.data?.[0]).toBe('21:30');
  });
});

describe('demandOption', () => {
  it('renders average and peak kW series, preserving nulls as gaps', () => {
    const option = asOption(
      demandOption(
        [
          { bucketStart: '2026-10-04T09:00:00Z', value: 55, secondary: 78 },
          { bucketStart: '2026-10-04T10:00:00Z', value: null, secondary: null },
        ],
        'UTC',
      ),
    );
    expect(option.series).toHaveLength(2);
    expect(option.series?.[0].name).toContain('kW');
    expect(option.series?.[0].data).toEqual([55, null]);
    expect(option.series?.[1].data).toEqual([78, null]);
  });
});

describe('siteEnergyOption', () => {
  const slices: SiteEnergySlice[] = [
    { siteId: 'a', siteName: 'HQ', energyKwh: 120 },
    { siteId: 'b', siteName: 'Plant', energyKwh: 80 },
    { siteId: 'c', siteName: 'Depot', energyKwh: null },
  ];

  it('uses site names on the category axis and kWh values as bars', () => {
    const option = asOption(siteEnergyOption(slices));
    expect(
      (option.yAxis as { data?: string[] })?.data,
    ).toEqual(['HQ', 'Plant', 'Depot']);
    expect(option.series?.[0].data).toEqual([120, 80, null]);
    expect(option.series?.[0].type).toBe('bar');
  });
});

describe('carbonOption', () => {
  const carbonBucket = (
    overrides: Partial<CarbonEmissionResponse>,
  ): CarbonEmissionResponse => ({
    organizationId: 'org-1',
    dimension: 'ORGANIZATION',
    dimensionId: 'org-1',
    granularity: 'DAY',
    bucketStart: '2026-10-03T00:00:00Z',
    bucketEnd: '2026-10-04T00:00:00Z',
    estimated: false,
    ...overrides,
  });
  const buckets: CarbonEmissionResponse[] = [
    carbonBucket({ emissionsKgCo2Eq: 410, qualityStatus: 'VALID' }),
    carbonBucket({
      bucketStart: '2026-10-04T00:00:00Z',
      bucketEnd: '2026-10-05T00:00:00Z',
      qualityStatus: 'UNAVAILABLE',
    }),
  ];

  it('keeps UNAVAILABLE buckets as null gaps, never zero', () => {
    const option = asOption(carbonOption(buckets, null, 'DAY', 'UTC'));
    expect(option.series?.[0].data).toEqual([410, null]);
    expect(option.series?.[0].name).toContain('kgCO₂e');
  });
});

describe('costOption', () => {
  it('labels the series and axis with the resolved currency', () => {
    const buckets: CostBucketResponse[] = [
      {
        organizationId: 'org-1',
        dimension: 'ORGANIZATION',
        dimensionId: 'org-1',
        granularity: 'DAY',
        bucketStart: '2026-10-04T00:00:00Z',
        bucketEnd: '2026-10-05T00:00:00Z',
        totalCost: 31.5,
        currency: 'EUR',
        qualityStatus: 'VALID',
        missingRateHours: 0,
      },
    ];
    const option = asOption(costOption(buckets, null, 'EUR', 'DAY', 'UTC'));
    expect(option.series?.[0].name).toBe('Cost (EUR)');
    expect((option.yAxis as { name?: string }).name).toContain('EUR');
    expect(option.series?.[0].data).toEqual([31.5]);
  });
});
