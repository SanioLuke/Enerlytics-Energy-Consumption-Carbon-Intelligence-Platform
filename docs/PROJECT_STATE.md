# Enerlytics Project State

## Current Phase

Phase 5 — deterministic energy and carbon calculation specification complete. No application code, database migrations, or executable calculation engine has been implemented.

## Completed Work

### Phase 0 — Documentation Foundation

- Established documentation, engineering rules, technology baseline, ADR process, repository structure, and initial conventions.

### Phase 1 — Product Architecture and Domain Analysis

- Defined the functional specification for six personas and 25 capabilities, including phased scope, permissions, journeys, validation, and acceptance criteria.

### Phase 2 — Technical Architecture Design

- Defined modular runtime, frontend/backend boundaries, telemetry worker, Kafka, SSE, observability, scaling, and extraction strategy.

### Phase 3 — PostgreSQL Data Model

- Designed logical/physical PostgreSQL schemas, tenant-safe keys, decimal precision, partitioning, indexes, rollups, retention, auditability, and migration strategy.

### Phase 4 — Event-Driven Telemetry Architecture

- Defined event contracts, topics, keys, ordering, idempotency, retries/DLT, outbox/inbox boundaries, late-data handling, replay, scaling, and observability.

### Phase 5 — Deterministic Calculation Specification

- Defined canonical units and exact conversion constants for energy, power, area, financial values, carbon emissions, and carbon intensity.
- Defined a 34-significant-digit intermediate decimal policy, persisted scales, `HALF_EVEN` rounding, no-early-rounding rules, line-item/currency handling, and provenance requirements.
- Defined reading eligibility, duration-weighted coverage, valid/estimated/missing contributions, and complete/partial/estimated/unavailable/invalid statuses.
- Defined site-local half-open period boundaries, UTC persistence, DST handling, and comparable-period rules.
- Specified all 16 required deterministic calculations with inputs, output, unit, precision, rounding, failure behavior, and worked examples.
- Defined interval energy and power integration rules, instantaneous single/three-phase power, billing demand, time-weighted average demand, and cumulative register reset/rollover behavior.
- Defined flat/demand/fixed/tax cost calculations and deterministic time-of-use interval splitting.
- Defined location-based Scope 2 factor selection, rigorous g/kg/tonne conversion, energy-weighted carbon intensity, stale/missing factor behavior, and future market-based separation.
- Defined renewable share with evidence overlap/over-procurement prevention.
- Defined energy/carbon floor-area intensities and effective area requirements.
- Defined baseline, target direction, month-over-month, and year-over-year variance semantics, including zero denominators and partial-period matching.
- Defined missing, duplicate, negative, estimated, reset, late, corrected, and stale-input policies.
- Defined calculation dependency/revision flow, required provenance, stable failure categories, and golden verification scenarios.

## Changed Files

### Phase 5

- `docs/CALCULATION_SPEC.md` — created the deterministic energy, demand, tariff, carbon, intensity, variance, and comparison specification.
- `docs/PROJECT_STATE.md` — recorded Phase 5 decisions, validation, open issues, and next tasks.

### Existing Documentation

- `docs/PRODUCT_REQUIREMENTS.md`
- `docs/ARCHITECTURE.md`
- `docs/DATA_MODEL.md`
- `docs/EVENT_ARCHITECTURE.md`
- `docs/ADR/README.md`
- `docs/ADR/0001-modular-monolith.md`
- `docs/ADR/0002-deployment-topology-and-service-boundaries.md`
- `docs/ADR/0003-kafka-outbox-and-telemetry-guarantees.md`
- `docs/ADR/0004-sse-first-realtime-delivery.md`
- `docs/API_CONVENTIONS.md`
- `docs/SECURITY.md`
- `docs/TEST_STRATEGY.md`
- `docs/DEPLOYMENT.md`
- `docs/CHANGELOG.md`

## Calculation Decisions

- Canonical electrical energy is kWh and demand/power is kW.
- Canonical emissions are kgCO2e; canonical intensity is kgCO2e/kWh. tCO2e is derived after summation for presentation/reporting.
- Canonical floor area is m²; ft² converts by exact decimal factor `0.09290304`.
- Import, export, and generation are separate nonnegative quantities. Negative net import is allowed only as a derived labeled metric.
- Authoritative arithmetic uses arbitrary-precision decimals with at least 34 significant intermediate digits; binary floating point is prohibited.
- Default rounding is `HALF_EVEN`; final energy/emission/intensity scale is 9, demand 6, percentages 6, financial calculations 6, and rates 9.
- No early per-reading rounding is allowed. Cost and carbon totals sum high-precision values before final output conversion.
- Missing input is not zero. Coverage is duration-based and valid/estimated/missing contributions are separately visible.
- Instantaneous power and billing/interval demand are distinct concepts.
- Peak demand is the maximum eligible average demand over a declared demand interval, with earliest UTC interval as deterministic tie-breaker.
- Cumulative-register negative deltas require confirmed reset/rollover evidence; absolute-value or zero-clamping is forbidden.
- Time-of-use tariffs use tariff-local timezone and actual elapsed overlap. Boundary splitting is permitted only under an explicit uniform-within-interval policy.
- Location-based Scope 2 is the initial method. Missing/stale factors never silently become zero; approved fallback factors remain labeled/versioned.
- Future market-based Scope 2 is a separate method/result series with instruments, allocations, residual mix, and dual reporting.
- Renewable percentage is capped at 100% for applied renewable load; over-procurement remains a separate quantity.
- Baseline raw variance is actual minus baseline. Target reporting includes raw variance plus a direction-aware favorable gap/on-track rule.
- Month/year comparisons require equivalent site-local periods and matched completeness; MTD/YTD comparisons must be labeled.
- Late/corrected inputs create new downstream revisions and never mutate completed report artifacts.

## Formula Coverage

| # | Formula | Canonical output |
|---:|---|---|
| 1 | Energy consumption | kWh |
| 2 | Instantaneous demand | kW |
| 3 | Peak demand | kW plus qualifying interval |
| 4 | Average demand | kW |
| 5 | Consumption delta | kWh |
| 6 | Cost | ISO currency major unit |
| 7 | Time-of-use tariff allocation | kWh by band and currency charge |
| 8 | Location-based Scope 2 emissions | kgCO2e; derived tCO2e |
| 9 | Realized carbon intensity | kgCO2e/kWh; optional gCO2e/kWh |
| 10 | Renewable-energy share | percentage points |
| 11 | Energy intensity per area | kWh/m² |
| 12 | Carbon intensity per area | kgCO2e/m² |
| 13 | Baseline variance | metric unit and percentage points |
| 14 | Target variance | metric unit, percentage, favorable gap, on-track state |
| 15 | Month-over-month change | metric unit and percentage points |
| 16 | Year-over-year change | metric unit and percentage points |

## Database Changes

None implemented. The specification maps to previously designed reading, aggregation, tariff, cost, carbon factor/emission, baseline, target, and calculation-run concepts, but no DDL or migration was created.

## APIs, Events, and Code Created

None. No Java classes, calculation services, endpoints, event schemas, Kafka resources, test fixtures, or executable formulas were created.

## Tests Executed

Documentation and dimensional validation only:

- Confirmed 16 numbered formula specifications exist.
- Confirmed each formula states inputs, formula/output, unit, precision/rounding, failure behavior, and at least one worked example.
- Confirmed energy, demand, area, currency, percentage, emissions, and carbon-intensity units and conversion constants are explicit.
- Confirmed no FLOAT/DOUBLE or binary floating-point path is permitted for authoritative values.
- Confirmed carbon examples reconcile from kWh × gCO2e/kWh through kgCO2e and tCO2e.
- Confirmed edge-case policies cover missing readings, resets/rollovers, duplicates, negative readings, estimated readings, stale/missing carbon factors, timezone/DST, tariff changes, and late telemetry.
- Confirmed location-based Scope 2 is initial and market-based accounting remains a separate future method.
- Confirmed a calculation dependency diagram, provenance contract, failure codes, and golden-test scenarios are present.
- Checked changed documentation for obvious unfinished markers.
- No executable unit/integration tests were applicable because implementation was explicitly out of scope.

## Unresolved Issues

### Coverage and Meter Semantics

- Minimum complete-coverage threshold and estimated-materiality threshold per metric/report.
- Supported channel semantics, intervals, multipliers, cumulative register modulus/reset evidence, and physical sanity ranges.
- Approved interpolation/resampling methods, if any, for instantaneous power and mixed intervals.
- Demand interval, billing-window, coincident/non-coincident, and peak qualification rules for initial markets.

### Tariffs and Finance

- Initial tariff markets, supported time-of-use calendars, tiers, holidays, demand charges, credits, taxes, and fixed-charge proration.
- Jurisdiction/provider-specific tax line sequencing and rounding exceptions.
- Currency presentation and whether foreign-exchange calculations are in initial scope.

### Carbon and Renewable Accounting

- Initial grid regions/providers, factor granularity, freshness thresholds, fallback hierarchy, revisions, and recalculation policy.
- Renewable evidence types permitted in MVP and treatment of onsite generation, export, storage, supplier products, and certificates.
- Market-based evidence/allocation/residual-mix methodology for Phase 2.

### Baselines, Targets, and Comparisons

- Approved baseline methods and minimum reference coverage.
- Target trajectory methods and direction semantics by metric.
- Leap-day, partial-period, boundary-change, and site-timezone-change comparison policies.
- Effective floor-area history and mixed-use allocation.

### Recalculation and Operations

- Allowed lateness, correction window, finalization, report regeneration, and recalculation service objectives.
- Exact algorithm/version governance and approval ownership.
- Quantitative SLOs and scale inputs from earlier phases.

## Technical Debt

- No implementation technical debt exists because no code or schema was created.
- Several policy thresholds remain blocked decisions and must not become hidden defaults.
- Worked examples are specification examples; machine-readable golden datasets still need to be authored and independently reviewed.
- Detailed country/provider tariff and carbon methodologies require market-specific specifications before production use.
- `docs/API_CONVENTIONS.md`, `docs/TEST_STRATEGY.md`, `docs/SECURITY.md`, and `docs/DEPLOYMENT.md` still require reconciliation before implementation.

## Next Recommended Task

Continue contract and verification design before coding:

1. Approve calculation coverage, rounding, demand, tariff, carbon, renewable, baseline, target, comparison, and late-correction policies.
2. Convert the worked examples and edge cases into independently reviewed machine-readable golden datasets with expected values and provenance.
3. Produce OpenAPI and AsyncAPI contracts using canonical decimal-string/unit semantics.
4. Create ADRs for consequential market-specific accounting or rounding decisions.
5. Reconcile test strategy with property-based, golden-data, DST, idempotency, and recalculation requirements.
6. Only then bootstrap backend/frontend/infrastructure and implement formulas test-first.

Do not implement calculation code or Java entities until these policies and golden datasets are approved.
