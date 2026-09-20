# Enerlytics Product Requirements

- Status: Functional specification
- Version: 1.0
- Date: 2026-09-20
- Product: Enerlytics Energy Consumption & Carbon Intelligence Platform

## 1. Purpose and Outcomes

Enerlytics enables organizations to understand electricity consumption, demand, cost, and Scope 2 carbon emissions across facilities; identify abnormal usage; track budgets and sustainability targets; and produce auditable reports. The product must turn interval telemetry into timely, trustworthy, tenant-isolated decisions without presenting estimated or incomplete data as authoritative.

Primary outcomes:

1. Reduce avoidable electricity consumption and peak demand.
2. Improve cost visibility and budget control.
3. Quantify location-based and, when evidence exists, market-based Scope 2 emissions.
4. Track renewable electricity and sustainability progress with provenance.
5. Detect operational issues early and support accountable resolution.
6. Preserve calculation reproducibility, data quality, and auditability.

## 2. Scope and Release Phases

Requirement labels are **MVP**, **P2**, and **Future**. MVP is the first production-capable release, not a prototype.

### 2.1 MVP

- Multi-tenant organization, site, building, zone, meter, user, and RBAC administration.
- Simulated electricity telemetry and idempotent interval ingestion.
- Live and historical electricity views with data-quality indicators.
- Peak demand, tariff-based cost, location-based Scope 2 emissions, renewable percentage, baselines, targets, budgets, alerts, reports, audit history, preferences, and operational status.
- Threshold and data-quality anomaly detection; deterministic short-horizon forecasting with confidence/data-coverage disclosure.
- CSV and PDF report exports, in-app/email notification channels, and administrator-visible integration health.

### 2.2 Phase 2

- External meter gateways and utility imports, grid carbon-intensity provider adapters, advanced tariffs, market-based Scope 2 evidence, weather/occupancy normalization, statistical anomaly detection, scenario forecasts, scheduled reports, webhook notifications, approval workflows, and richer portfolio benchmarking.

### 2.3 Future

- Additional energy commodities, Scope 1/3 workflows, automated demand response, optimization recommendations, machine-learning ensembles, verified certificate registries, supplier billing reconciliation, mobile-native workflows, and independently deployable services only when operational evidence justifies them.

### 2.4 Explicitly Out of Scope for MVP

- Utility billing or payment execution.
- Control of physical equipment or automatic load shedding.
- Carbon-credit trading or claims certification.
- Replacement of regulatory greenhouse-gas assurance.
- Arbitrary user-authored calculation code.

## 3. Personas and Access Model

| Role | Scope | Primary responsibilities |
|---|---|---|
| Platform Administrator | Platform-wide | Tenant lifecycle, platform support, provider configuration, operational oversight; no routine access to tenant business data |
| Organization Administrator | One or more assigned organizations | Organization settings, hierarchy, meters, tariffs, users, roles, rules, and reports |
| Facility Manager | Assigned sites/buildings | Live operations, meter health, alerts, acknowledgement, notes, and site-level analysis |
| Energy Analyst | Assigned organization/sites | Consumption, demand, tariff, baseline, anomaly, forecast, and data-quality analysis |
| Sustainability Manager | Assigned organization/sites | Carbon methods, renewable evidence, targets, progress, and sustainability reporting |
| Read-only Viewer | Explicitly assigned organization/sites | View permitted dashboards and reports; no mutations or sensitive administration |

A user may hold multiple scoped roles. Effective permissions are the union of active assignments, constrained by tenant and resource scope. Platform support access to tenant data must be exceptional, time-bound, justified, and audited. No role can grant itself a scope or privilege it does not possess.

### 3.1 Permission Families

- `platform.manage`, `platform.monitor`
- `organization.view`, `organization.manage`
- `site.view`, `site.manage`
- `meter.view`, `meter.manage`, `telemetry.ingest`
- `analytics.view`, `analytics.manage`
- `tariff.view`, `tariff.manage`
- `carbon.view`, `carbon.manage`
- `target.view`, `target.manage`
- `budget.view`, `budget.manage`
- `alert.view`, `alert.manage`, `alert.acknowledge`
- `report.view`, `report.generate`, `report.manage`
- `user.view`, `user.manage`, `role.manage`
- `audit.view`, `operations.view`

Every backend operation must authorize tenant, permission, and resource scope. UI visibility is convenience, not enforcement.

## 4. Domain Definitions and Calculation Policies

- **Organization:** tenant and top-level ownership boundary.
- **Site/facility:** physical operational location with address, IANA timezone, grid region, currency, and reporting metadata. “Site” and “facility” are synonymous in the product.
- **Building:** physical structure within a site.
- **Logical zone:** reporting grouping within a site or building; it must not imply electrical topology.
- **Meter:** electricity measurement device or logical aggregate with one or more channels.
- **Interval reading:** value for a defined UTC interval with source, quality, and ingestion provenance.
- **Consumption:** electrical energy, normally kWh, aggregated without binary floating-point arithmetic.
- **Demand:** average power over a demand interval, normally kW. Peak demand is the maximum eligible interval demand in a selected period, not instantaneous power unless explicitly identified.
- **Location-based Scope 2:** imported grid electricity multiplied by time/region-valid grid emission factors.
- **Market-based Scope 2:** electricity multiplied according to documented contractual instruments and residual-mix rules; unavailable evidence must never silently yield zero.
- **Renewable percentage:** eligible renewable electricity divided by eligible total electricity for the same boundary and period, with numerator source and coverage disclosed.
- **Baseline:** approved reference model/version for comparison, immutable once used except through supersession.
- **Data quality:** `VALID`, `ESTIMATED`, `MISSING`, `DUPLICATE_REJECTED`, `INVALID`, or `LATE`, with aggregate coverage disclosed.

All authoritative calculations retain algorithm version, source data range, units, rounding policy, factor/tariff/baseline version, calculation time, and quality/coverage. Site-local calendar periods must be resolved using the site's IANA timezone, including daylight-saving transitions, while timestamps remain stored in UTC.

## 5. Key User Journeys

### J1. Facility manager investigates abnormal load

1. User authenticates and selects an assigned facility.
2. Live dashboard shows latest demand, today’s consumption, data freshness, and active alerts.
3. User opens an abnormal-load alert and sees rule/anomaly evidence, expected range, actual value, affected meter, and data quality.
4. User inspects meter history and compares the interval with the baseline and neighboring periods.
5. User records a note and acknowledges the alert; the system records actor, timestamp, disposition, and correlation ID.
6. If data is stale or estimated, the UI labels it and prevents a misleading “resolved” inference.

Success: the alert is acknowledged only by an authorized scoped user, investigation context is retained, and other users see the updated state.

### J2. Sustainability manager assesses Scope 2 progress

1. User opens the carbon dashboard for a reporting period.
2. Portfolio totals show location-based emissions, renewable percentage, coverage, factor provenance, and prior-period comparison.
3. User compares facilities using consistent units and filters.
4. User drills into a facility to review factors, imported/exported energy treatment, renewable evidence, and target progress.
5. User generates a versioned report and exports it.

Success: totals reconcile with facility detail within rounding tolerance, incomplete factors/evidence are visible, and the export is reproducible from its calculation snapshot.

### J3. Organization administrator onboards a facility

1. User creates or selects an organization.
2. User creates a facility with timezone, grid region, currency, and reporting attributes, then optionally adds buildings/zones.
3. User registers meters, channels, interval length, units, assignment, and activation time.
4. User configures a tariff and effective period.
5. User invites users and assigns least-privilege scoped roles.
6. User creates alert rules and starts simulated telemetry.
7. System validates configuration, records audit events, and exposes readiness/data-flow status.

Success: no telemetry is attributed ambiguously, overlapping tariff rules are rejected, invitations do not over-grant access, and first data is visible with freshness status.

### J4. Energy analyst reviews cost and peak demand

1. User chooses sites and a period.
2. Consumption, peak demand, cost, coverage, and comparison period load together.
3. User drills into the peak interval and tariff line items.
4. User changes only analytical filters; authoritative historical tariff versions remain unchanged.
5. User exports the analysis or saves a report definition.

Success: peak interval and cost line items trace to eligible readings and tariff versions; missing data is not silently interpolated.

### J5. Platform administrator handles telemetry degradation

1. Administrator opens operational monitoring.
2. Dashboard identifies stale sources, ingestion lag, rejected readings, failed jobs, and provider outages without exposing unnecessary tenant data.
3. Administrator filters by tenant/source, inspects sanitized diagnostics and correlation IDs, and initiates an authorized retry where safe.
4. Recovery and retry outcomes are audited.

Success: platform health is restored or clearly escalated without duplicate readings or cross-tenant disclosure.

### J6. Viewer consumes a shared report

1. Viewer signs in and sees only assigned organizations/sites.
2. Viewer opens an authorized completed report and its coverage/provenance summary.
3. Viewer downloads only if `report.view` includes export access under organization policy.
4. Attempts to alter filters, acknowledge alerts, or access another tenant are denied and audited as appropriate.

## 6. Module Requirements

Each module below defines actors, user stories, requirements, validation, permissions, edge cases, failure cases, and acceptance criteria.

### 6.1 Organization Management

**Actors:** Platform Administrator; Organization Administrator; scoped viewers for read access.

**User stories:** A platform administrator creates/suspends tenants. An organization administrator maintains profile, reporting currency, fiscal year, and defaults. A user switches safely among assigned organizations.

**Functional requirements:**
- **MVP:** Create, view, update, suspend/reactivate organizations; assign stable ID, unique display name within policy, status, default currency, fiscal-year start, locale, and ownership contacts; prevent suspended-tenant mutations and ingestion while preserving authorized read/export access according to policy.
- **P2:** Organization groups, delegated administration, configurable retention, branding, and approval workflow.
- **Future:** Legal-entity hierarchy and cross-organization consolidated reporting with explicit grants.

**Validation:** Required trimmed name; supported ISO 4217 currency; valid fiscal month; valid contact format; unique normalized organization key; status transitions must be legal.

**Permissions:** Platform Administrator manages lifecycle. Organization Administrator updates assigned active organizations but cannot suspend/delete tenant or change platform controls. Others need `organization.view`.

**Edge cases:** Same user in multiple tenants; rename without changing identifier; suspension during report generation; tenant with historical data but no active sites.

**Failure cases:** Duplicate key, stale concurrent update, identity-provider outage, unauthorized cross-tenant lookup. Return consistent errors and preserve prior state.

**Acceptance criteria:** Authorized creation produces an active isolated tenant and audit event; duplicate keys are rejected; suspended tenants cannot ingest or mutate; users cannot enumerate unassigned organizations.

### 6.2 Sites / Facilities

**Actors:** Organization Administrator; Facility Manager; Energy Analyst; Sustainability Manager; Viewer.

**User stories:** Administrators model facilities; managers select a site and understand local operational periods; analysts compare sites.

**Functional requirements:**
- **MVP:** CRUD/archive sites with organization, name/code, address, IANA timezone, grid region, currency override, floor area and unit, operational status, tags, and coordinates when supplied; list/filter/sort/paginate; prohibit hard deletion after dependent data.
- **P2:** Bulk import, custom attributes, occupancy/production drivers, site groups.
- **Future:** Geospatial portfolio views and complex campus hierarchy.

**Validation:** Unique site code per organization; valid IANA timezone, region mapping, coordinates, positive floor area, supported units/currency; archive reason required when data exists.

**Permissions:** `site.manage` for organization admins; facility managers may edit approved operational metadata only within scope; others `site.view`.

**Edge cases:** Timezone or region changes after history exists; facility crosses reporting boundaries; archived site in historical report; absent floor area.

**Failure cases:** Invalid timezone, dependent active meter on archive policy violation, concurrent edits, region provider unavailable. Existing data remains unchanged.

**Acceptance criteria:** Local-day views honor DST; historical calculations retain the effective timezone/region context; scoped users see only assigned sites; archived sites are excluded by default but remain reportable.

### 6.3 Buildings and Logical Zones

**Actors:** Organization Administrator; Facility Manager; analysts/viewers.

**User stories:** Managers organize meters by building and operational zone; analysts filter and compare defined boundaries.

**Functional requirements:**
- **MVP:** Create/update/archive buildings under a site and logical zones under a site or building; assign meters to one physical location and zero or more reporting zones with effective dates; expose hierarchy and allocation warnings.
- **P2:** Weighted allocation of shared meters, bulk hierarchy changes, effective-dated floor area/occupancy.
- **Future:** Digital-twin topology and spatial plans.

**Validation:** Names/codes unique within parent; no hierarchy cycle; zone remains within one site; allocation weights, when enabled, total 100% for each effective interval.

**Permissions:** Organization Administrator and scoped Facility Manager with `site.manage`; read access follows site scope.

**Edge cases:** Meter serving multiple buildings; hierarchy changes mid-period; archived parent; zone without meters.

**Failure cases:** Cyclic move, cross-site assignment, overlapping effective assignments, stale version. Reject atomically.

**Acceptance criteria:** Hierarchy paths are deterministic; site totals do not double count zone assignments; historical reports use effective assignments; unauthorized moves are denied.

### 6.4 Electricity Meters

**Actors:** Organization Administrator; Facility Manager; Energy Analyst; ingestion service identity.

**User stories:** Administrators register meters and channels; managers inspect health; analysts understand source and quality.

**Functional requirements:**
- **MVP:** Register/update/deactivate meters with site, type (`IMPORT`, `EXPORT`, `GENERATION`, `SUBMETER`, `VIRTUAL`), serial/source ID, interval, timezone/source semantics, channels, canonical units, multiplier, activation range, location, parent relationship, and status; expose last-reading and health summary.
- **P2:** Gateway credentials through secret references, meter replacement lineage, virtual formulas, calibration records.
- **Future:** Bi-directional device commands subject to a separate safety architecture.

**Validation:** Unique source ID per tenant/provider; supported channel/unit; positive multiplier and interval; no parent cycle; activation periods valid; channel semantics match meter type.

**Permissions:** `meter.manage` to administer; `meter.view` to inspect; only service identities with `telemetry.ingest` submit readings.

**Edge cases:** Meter replacement; multiplier correction; late readings after deactivation; import/export channels; reset cumulative register.

**Failure cases:** Duplicate registration, invalid hierarchy, secret/provider unavailable, optimistic-lock conflict. No partial channel creation.

**Acceptance criteria:** Registered meter is attributable to one tenant/site; invalid units are rejected; health reflects freshness policy; edits that affect calculations are effective-dated and audited.

### 6.5 Simulated Smart Meter Telemetry

**Actors:** Organization Administrator; developer/test operator; Platform Administrator for global limits.

**User stories:** An administrator starts realistic simulated data for a configured meter to evaluate the platform without hardware.

**Functional requirements:**
- **MVP:** Configure deterministic simulation by meter, seed, start/end or continuous mode, interval, base load, daily/weekly profile, noise bounds, and optional spikes/dropouts; start/pause/resume/stop; publish through the same validated ingestion path as external telemetry; label source as simulated.
- **P2:** Scenario templates for weather, occupancy, solar generation, outages, and coordinated site loads.
- **Future:** Hardware-in-the-loop simulation.

**Validation:** Meter must be active and simulation-enabled; interval matches channel policy; finite nonnegative consumption except permitted export; bounded rate/duration; seed required for reproducibility.

**Permissions:** Organization Administrator with `meter.manage`; Platform Administrator sets quotas; viewers cannot control simulation.

**Edge cases:** Restart with same seed; pause over DST boundary; late generated interval; simultaneous real and simulated source.

**Failure cases:** Broker unavailable, quota exceeded, meter deactivated, duplicate event. Retry safely; stable source keys prevent duplicate persistence.

**Acceptance criteria:** Same configuration/seed/time range yields identical readings; simulated data is visibly labeled; stop prevents future generation; redelivery does not duplicate readings.

### 6.6 Real-Time Consumption

**Actors:** Facility Manager; Energy Analyst; Sustainability Manager; Viewer.

**User stories:** Users monitor current demand, recent consumption, trend, freshness, and active issues without manual refresh.

**Functional requirements:**
- **MVP:** Site/building/meter live view with latest eligible reading, current demand, today consumption, recent trend, last-updated time, quality, coverage, and connection state; stream authorized updates with bounded refresh fallback; permit unit/time-window selection.
- **P2:** Configurable operations wallboards and richer load disaggregation.
- **Future:** Predictive control recommendations.

**Validation:** Scope and filters authorized; supported units/windows; aggregates exclude or separately label invalid readings; freshness threshold is meter-aware.

**Permissions:** `analytics.view` plus resource scope; no mutation.

**Edge cases:** No reading yet; stale source; out-of-order update; DST day; meter/unit mix; browser reconnect.

**Failure cases:** Stream disconnect, aggregation delay, cache outage. Show last known timestamp and degraded/stale state; never display stale data as live.

**Acceptance criteria:** New valid readings appear within the declared service objective; out-of-scope updates never reach clients; stale data is labeled; site totals reconcile to eligible non-overlapping meters.

### 6.7 Historical Consumption

**Actors:** Facility Manager; Energy Analyst; Sustainability Manager; Viewer.

**User stories:** Users explore consumption over time, compare periods, and export governed data.

**Functional requirements:**
- **MVP:** Query and chart consumption by permitted organization/site/building/zone/meter, interval granularity, and date range; compare prior period/year; show totals, average, quality, coverage, and explicit unit; paginate raw readings and export bounded CSV.
- **P2:** Weather/occupancy normalization, saved analyses, portfolio benchmarking.
- **Future:** Cross-customer anonymized benchmarks with governance.

**Validation:** Start before end; maximum range by granularity; site-local boundaries; allowlisted sort/filter; no unsupported unit conversion.

**Permissions:** `analytics.view`; raw exports may require `report.generate` under organization policy.

**Edge cases:** Missing/estimated intervals; partial first/last interval; DST; archived meters; corrected readings; mixed intervals.

**Failure cases:** Query timeout, export limit, aggregate lag, unavailable archive tier. Return actionable bounded-query guidance or asynchronous operation, not partial unlabeled totals.

**Acceptance criteria:** Totals are deterministic and disclose coverage; comparisons use equivalent local periods; corrections supersede rather than erase provenance; exported rows match applied filters.

### 6.8 Peak-Demand Analytics

**Actors:** Facility Manager; Energy Analyst; Organization Administrator; Viewer.

**User stories:** Analysts identify when and where maximum demand occurs and its tariff impact.

**Functional requirements:**
- **MVP:** Calculate maximum eligible kW demand for selected resource/period/demand interval; show timestamp, duration, contributing meters, comparison peak, and coverage; chart load profile and top-N peaks with configurable separation rule.
- **P2:** Coincident/non-coincident demand, tariff ratchets, demand-window normalization, peak forecast.
- **Future:** Demand-response optimization.

**Validation:** Supported demand interval; no mixing incompatible measurements; minimum coverage policy; top-N and range bounds.

**Permissions:** `analytics.view`; policy configuration requires `analytics.manage` or `tariff.manage`.

**Edge cases:** Tied peaks; DST; missing interval at apparent peak; export generation; submeter double counting.

**Failure cases:** Insufficient coverage, incompatible intervals, stale aggregate. Mark result incomplete or unavailable rather than infer silently.

**Acceptance criteria:** Peak traces to source intervals; ties follow documented deterministic ordering; incomplete coverage is prominent; tariff demand line item references the same qualifying peak policy.

### 6.9 Cost Calculations

**Actors:** Energy Analyst; Organization Administrator; Facility Manager; Viewer.

**User stories:** Users estimate and explain electricity cost from consumption, demand, and effective tariff rules.

**Functional requirements:**
- **MVP:** Calculate energy charges, demand charges, fixed charges, taxes/surcharges configured in tariff, subtotal/total, currency, coverage, and tariff version; support recalculation snapshots and site/period breakdown.
- **P2:** Tiered/seasonal/time-of-use tariffs, ratchets, credits, taxes by jurisdiction, invoice comparison.
- **Future:** Procurement scenarios and settlement workflows.

**Validation:** Decimal arithmetic and explicit rounding; one applicable tariff policy per charge; valid effective periods/currency; no silent currency conversion; taxes cannot create invalid totals under configured policy.

**Permissions:** `tariff.view` and `analytics.view` to view; `tariff.manage` to alter inputs; report permissions for export.

**Edge cases:** Mid-period tariff change; missing intervals; negative export; billing period differs from calendar month; tariff absent.

**Failure cases:** Ambiguous tariff, unsupported rule, missing exchange rate when explicitly requested, calculation job failure. Return no authoritative total and identify blockers.

**Acceptance criteria:** Every line item traces to readings and tariff version; totals reconcile within documented rounding; missing coverage is shown; recalculation does not rewrite prior report snapshots.

### 6.10 Electricity Tariff Configuration

**Actors:** Organization Administrator; Energy Analyst with delegated management; Viewer for read access.

**User stories:** Administrators configure effective-dated tariffs; analysts validate charge structure before activation.

**Functional requirements:**
- **MVP:** Draft, validate, activate, supersede, and archive tariffs with name, site/meter applicability, currency, billing timezone/period, effective range, energy rate, demand rate, fixed charge, and tax/surcharge rules; preview against sample consumption.
- **P2:** Time-of-use calendars, tiers, holidays, ratchets, minimum bills, export compensation, reusable templates, dual approval.
- **Future:** Utility tariff catalog ingestion.

**Validation:** Nonnegative rates unless rule permits credit; valid decimal scale; complete, non-overlapping applicability/effective ranges; valid local-time schedules; activated versions immutable.

**Permissions:** `tariff.manage`; viewers need `tariff.view`; activation may require Organization Administrator policy.

**Edge cases:** DST schedule; tariff changes mid-billing cycle; overlapping meter/site tariff; archived site; retrospective correction.

**Failure cases:** Overlap, malformed rule, stale draft, preview data unavailable. Activation is atomic and blocked on error.

**Acceptance criteria:** Exactly one deterministic applicable rule set per charge context; activated version cannot be edited; supersession preserves history; preview discloses assumptions and does not alter records.

### 6.11 Carbon Emissions Calculations

**Actors:** Sustainability Manager; Energy Analyst; Organization Administrator; Viewer.

**User stories:** Sustainability teams calculate and compare Scope 2 emissions with transparent methods and factors.

**Functional requirements:**
- **MVP:** Calculate location-based Scope 2 CO2e from imported electricity and effective grid factor; show tCO2e/kgCO2e, factor source/version/region, electricity coverage, factor coverage, and methodology; aggregate without double counting; export auditable results.
- **P2:** Market-based Scope 2 using supplier factors, energy attribute certificates, residual mix, evidence validity, and dual reporting; recalculation workflow.
- **Future:** Scope 1/3 and assurance workflow.

**Validation:** Compatible energy/factor units; effective region/time; decimal precision; no negative imported emissions unless approved avoided-emissions metric is separately modeled; market claims require evidence.

**Permissions:** `carbon.view`; factor/method configuration uses `carbon.manage`; exports require report permission.

**Edge cases:** Missing factor interval; factor revisions; exported energy; onsite renewable generation; partial certificates; site region changes.

**Failure cases:** Provider/factor unavailable, incompatible units, incomplete evidence, calculation failure. Mark affected periods incomplete; never substitute zero silently.

**Acceptance criteria:** Result equals eligible energy × effective factor within rounding policy; factor and algorithm versions are retained; location and market methods are not conflated; incomplete coverage is visible.

### 6.12 Grid Carbon-Intensity Integration

**Actors:** Platform Administrator; Sustainability Manager; integration service identity.

**User stories:** Administrators configure trusted providers; sustainability managers know factor freshness and provenance.

**Functional requirements:**
- **MVP:** Support managed/manual effective-dated regional factors with source, methodology, unit, publication timestamp, validity, and revision; expose connector health and fallback state.
- **P2:** Provider adapters for historical and near-real-time intensity, credential references, scheduled retrieval, mapping, retry, reconciliation, and approved fallback hierarchy.
- **Future:** Marginal emissions and multi-provider confidence models.

**Validation:** Provider payload schema, region mapping, supported units, valid ranges/timestamps, uniqueness/versioning, and sanity thresholds with quarantine rather than silent discard.

**Permissions:** Platform Administrator configures global adapters; Organization/Sustainability administrators map sites only where delegated; `carbon.view` sees provenance.

**Edge cases:** Provider revises history; daylight-saving timestamps; multiple candidate regions; stale near-real-time factor; provider granularity differs from readings.

**Failure cases:** Timeout, authentication failure, rate limit, malformed payload, mapping gap. Retry with bounds, retain last valid factor only under explicit labeled policy, and alert operators.

**Acceptance criteria:** Every used factor has provenance; invalid payloads are quarantined; outages do not corrupt prior factors; fallback usage and freshness are visible and auditable.

### 6.13 Renewable-Energy Percentage

**Actors:** Sustainability Manager; Energy Analyst; Viewer.

**User stories:** Users quantify renewable electricity and understand whether it comes from onsite generation, contracts, or certificates.

**Functional requirements:**
- **MVP:** Calculate renewable percentage for site/portfolio/period from eligible onsite renewable consumption and configured renewable supply evidence; show numerator, denominator, source categories, coverage, and exclusions; prevent double counting.
- **P2:** Certificate inventory/allocation, supplier-product evidence, residual mix, vintage/geography rules, approval and expiry.
- **Future:** Registry verification and hourly matching.

**Validation:** Same boundary/period/unit; percentage normally 0–100 unless an explicit over-procurement metric is separate; evidence validity and allocation cannot exceed available quantity.

**Permissions:** `carbon.view` to view; `carbon.manage` to manage evidence/method; viewers cannot modify claims.

**Edge cases:** Exported onsite generation; certificates exceed load; partial period; storage charging source; missing denominator.

**Failure cases:** Conflicting allocation, expired evidence, incomplete consumption. Do not claim unsupported renewable percentage; show unverified/incomplete state.

**Acceptance criteria:** Numerator components reconcile and are not double counted; denominator matches eligible electricity; evidence/provenance and coverage appear in UI/export; zero and unknown are distinct.

### 6.14 Energy Baselines

**Actors:** Energy Analyst; Sustainability Manager; Organization Administrator; Facility Manager.

**User stories:** Analysts define an approved reference against which savings and anomalies are measured.

**Functional requirements:**
- **MVP:** Create baseline for resource, metric, reference period, aggregation, exclusions, and simple method (historical average or matched prior period); preview coverage; approve/activate; version/supersede; calculate variance absolute and percent.
- **P2:** Weather/occupancy/production normalized regression baselines, model diagnostics, approval workflow.
- **Future:** Portfolio model marketplace and continuous model selection.

**Validation:** Sufficient valid coverage; reference period precedes or is explicitly independent from evaluation; no overlapping active baseline for same purpose unless priority is explicit; divide-by-zero handling.

**Permissions:** `analytics.manage` creates/approves; `analytics.view` reads; organizations may separate author and approver in P2.

**Edge cases:** Site renovation; meter replacement; missing reference intervals; baseline equals zero; timezone change.

**Failure cases:** Insufficient data, invalid exclusions, model calculation error, stale approval. Keep draft and explain failure.

**Acceptance criteria:** Active baseline is immutable/versioned; variance traces to baseline and actual data; insufficient coverage blocks approval; reports retain baseline version.

### 6.15 Sustainability Targets

**Actors:** Sustainability Manager; Organization Administrator; Viewer.

**User stories:** Managers define energy, carbon, peak, or renewable targets and track progress.

**Functional requirements:**
- **MVP:** Create target with owner, scope, metric, baseline, target direction/value, start/end dates, milestones, status, and notes; display actual, expected trajectory, variance, coverage, and on/off-track state.
- **P2:** Approval, initiatives/action plans, forecasted attainment, reminders, portfolio rollups.
- **Future:** Science-based target templates and external disclosure integrations.

**Validation:** Supported metric/unit; target period and milestones ordered; baseline compatible with scope/method; renewable targets 0–100; owner active.

**Permissions:** `target.manage`; `target.view`; only authorized manager closes/cancels target with reason.

**Edge cases:** Baseline superseded; organization boundary changes; missing actuals; target achieved early; conflicting targets.

**Failure cases:** Calculation unavailable, incompatible baseline, stale update. Status becomes data-incomplete where appropriate, not falsely off-track.

**Acceptance criteria:** Progress uses versioned baseline/method; target changes are audited; incomplete data is visible; actual and trajectory calculations reconcile.

### 6.16 Budget Tracking

**Actors:** Organization Administrator; Energy Analyst; Facility Manager; Viewer with financial access.

**User stories:** Managers set electricity budgets and monitor spend/consumption against them.

**Functional requirements:**
- **MVP:** Define site/organization budget by period and currency for cost and optionally kWh; allocate monthly amounts; show actual, committed/forecast where available, variance, percent consumed, and coverage; alert thresholds.
- **P2:** Approval, versioned reforecast, department allocation, multi-currency consolidation with governed rates.
- **Future:** ERP integration and procurement commitments.

**Validation:** Nonnegative decimal values; allocations reconcile to total within rounding; non-overlapping active budget versions; explicit currency; authorized scope.

**Permissions:** `budget.manage`; `budget.view`; financial visibility may be narrower than energy visibility.

**Edge cases:** Fiscal year; tariff change; partial month; site opened mid-year; missing cost calculation; currency change.

**Failure cases:** Cost unavailable, allocation mismatch, stale edit. Show budget but mark actual incomplete; reject invalid activation atomically.

**Acceptance criteria:** Period totals reconcile; only financial-authorized users see amounts; variance uses the effective budget version; threshold breach can create one deduplicated alert per policy.

### 6.17 Alerts

**Actors:** Organization Administrator; Facility Manager; Energy Analyst; Sustainability Manager; Viewer for permitted read access.

**User stories:** Administrators define rules; managers receive, investigate, acknowledge, assign, and resolve alerts.

**Functional requirements:**
- **MVP:** Rules for consumption/demand thresholds, baseline variance, budget threshold, stale/missing telemetry, meter offline, and calculation/integration data quality; severity, scope, schedule, debounce, cooldown, recipients; alert lifecycle `OPEN`, `ACKNOWLEDGED`, `RESOLVED`, `CLOSED`; notes, assignment, deduplication, and audit.
- **P2:** Escalation policies, maintenance windows, composite rules, webhook/ticket integrations.
- **Future:** Prescriptive remediation and automated response with separate safety controls.

**Validation:** Compatible metric/operator/unit; valid threshold/schedule/timezone; recipient has scope; cooldown/debounce bounded; rule cannot target archived resource.

**Permissions:** `alert.manage` rules/close policy; `alert.acknowledge` lifecycle actions within scope; `alert.view` read.

**Edge cases:** Repeated breach; flapping; late readings; rule edited while alert open; user loses scope; site maintenance.

**Failure cases:** Evaluation lag, notification failure, unavailable baseline, duplicate event. Preserve alert, expose delivery/evaluation state, retry idempotently.

**Acceptance criteria:** One breach does not create duplicates inside dedupe window; lifecycle transitions identify actor/time/reason; unauthorized acknowledgement fails; resolution and recurrence follow documented rule semantics.

### 6.18 Anomaly Detection

**Actors:** Facility Manager; Energy Analyst; Organization Administrator.

**User stories:** Analysts identify unexpected load while understanding evidence and confidence.

**Functional requirements:**
- **MVP:** Deterministic anomalies for static threshold, rate-of-change, missing data, persistent out-of-hours load, and baseline deviation; show actual, expected/threshold, magnitude, duration, method/version, quality, and related alert.
- **P2:** Seasonal statistical models, peer groups, configurable sensitivity, feedback labels, drift monitoring.
- **Future:** Explainable ML ensembles and root-cause recommendations.

**Validation:** Minimum valid history/coverage; compatible units/granularity; bounded sensitivity; excluded maintenance windows; no model activation without diagnostics.

**Permissions:** `analytics.view` results; `analytics.manage` methods; feedback/alert acknowledgement according to alert permissions.

**Edge cases:** Holidays, DST, startup spikes, meter replacement, intentional shutdown, baseline drift.

**Failure cases:** Insufficient history, stale model, model execution error, delayed data. Mark unavailable/degraded; do not emit low-evidence anomaly as confirmed fact.

**Acceptance criteria:** Every anomaly has reproducible method/evidence; minimum-duration/debounce rules work; known missing data is classified separately; user can distinguish anomaly score from measured value.

### 6.19 Forecasting

**Actors:** Energy Analyst; Sustainability Manager; Facility Manager; Organization Administrator.

**User stories:** Users forecast consumption, demand, cost, carbon, and target/budget trajectory with disclosed uncertainty.

**Functional requirements:**
- **MVP:** Deterministic short-horizon forecast using seasonal historical profile for energy/demand, with derived cost/carbon when valid tariff/factors exist; show horizon, generated time, training window, coverage, method/version, point forecast, and error/uncertainty summary from backtesting where sufficient.
- **P2:** Weather/occupancy drivers, scenario inputs, probabilistic intervals, scheduled retraining and model comparison.
- **Future:** ML ensembles and optimization scenarios.

**Validation:** Minimum history/quality; bounded horizon and scenario values; method compatible with metric/granularity; derived forecasts require effective assumptions.

**Permissions:** `analytics.view`; scenario/model management uses `analytics.manage`; carbon/cost visibility remains separately constrained.

**Edge cases:** New site; structural break; holiday; DST; future tariff/factor unknown; negative export forecast.

**Failure cases:** Insufficient history, failed training, stale inputs, missing future assumptions. Show unavailable or partial components, not fabricated values.

**Acceptance criteria:** Forecast is clearly separated from actual; method/training/as-of time are shown; backtest metric is reproducible; cost/carbon forecast discloses assumed tariff/factor.

### 6.20 Reports

**Actors:** Organization Administrator; Facility Manager; Energy Analyst; Sustainability Manager; Viewer.

**User stories:** Users generate consistent operational, energy, cost, carbon, target, and budget reports and securely export them.

**Functional requirements:**
- **MVP:** Parameterized report types for consumption/demand, cost, Scope 2/renewable, alerts/data quality, target, and budget; asynchronous generation; status; CSV/PDF export; immutable snapshot metadata, filters, units, coverage, calculation versions, creator, and expiry; authorized download.
- **P2:** Scheduled delivery, templates, branding, XLSX, approval/sign-off, large dataset partitioning.
- **Future:** Regulatory disclosure connectors and narrative assistance with human approval.

**Validation:** Authorized scope/fields; bounded period/size; supported format; safe file names; spreadsheet-injection protection; no sensitive fields outside role.

**Permissions:** `report.generate`, `report.view`, `report.manage`; viewers can access only explicitly authorized reports/resources.

**Edge cases:** Data corrected after report; user loses access; generation spans tariff/factor versions; expired artifact; partial coverage.

**Failure cases:** Job failure, storage unavailable, timeout, renderer error. Preserve operation status and retry safely; never expose another tenant’s artifact.

**Acceptance criteria:** Export matches parameters and snapshot; report declares coverage/provenance; revoked access blocks download immediately; repeated request with same idempotency key does not duplicate jobs.

### 6.21 User Management

**Actors:** Platform Administrator; Organization Administrator; invited user.

**User stories:** Administrators invite and deactivate users; users accept invitations and manage their profile/preferences.

**Functional requirements:**
- **MVP:** Invite by normalized email, assign organization/resource roles, list/filter users, resend/revoke expiring invitation, activate on verified identity match, suspend membership, and remove future access without erasing audit history; display last sign-in metadata according to policy.
- **P2:** SCIM provisioning, groups, delegated site admins, access reviews.
- **Future:** Just-in-time provisioning and advanced identity governance.

**Validation:** Valid normalized email; identity-provider constraints; invitation expiry; inviter may grant only possessed/delegable scopes; organization must be active; prevent removal of last required administrator without recovery path.

**Permissions:** `user.manage`; Platform Administrators manage platform roles separately; users view own profile.

**Edge cases:** Existing identity invited to another tenant; email case; invitation resent; suspended user owns targets/reports; last admin.

**Failure cases:** Email/IdP unavailable, duplicate active membership, expired token, race during acceptance. Preserve invitation state and avoid duplicate membership.

**Acceptance criteria:** Invitation grants exactly selected scope after verified acceptance; revoked/expired invitation cannot be used; suspension blocks access promptly; all lifecycle actions are audited.

### 6.22 RBAC

**Actors:** Platform Administrator; Organization Administrator; security auditor.

**User stories:** Administrators assign least-privilege roles at organization/site scope; auditors verify effective access.

**Functional requirements:**
- **MVP:** Fixed system roles mapped to permission families; multiple scoped assignments; effective-permission view; deny-by-default backend checks; immediate revocation; separation of platform and tenant administration.
- **P2:** Custom roles, group assignments, approval, temporary access, periodic certification.
- **Future:** Attribute/policy-based controls for complex enterprises.

**Validation:** Valid role/scope combination; no cross-tenant scope; actor cannot escalate beyond delegable permissions; platform roles require platform authority; prevent orphaned administration.

**Permissions:** `role.manage`; users may view their own effective access; auditors receive read-only access explicitly.

**Edge cases:** Conflicting/multiple roles; scope moved/archived; cached authorization; user active in multiple tenants.

**Failure cases:** Stale token/cache, IdP claim mismatch, policy service degradation. Server-side authoritative membership wins; fail closed and invalidate access caches on change.

**Acceptance criteria:** Permission matrix tests cover every protected operation; revocation takes effect within declared security objective; cross-tenant identifiers do not bypass scope; role changes are audited with before/after state.

### 6.23 Audit History

**Actors:** Organization Administrator; Platform Administrator; authorized auditor/read-only compliance user.

**User stories:** Authorized users trace configuration, access, calculation, alert, report, and administrative changes.

**Functional requirements:**
- **MVP:** Append-only audit events with tenant, UTC time, actor/service, action, resource type/ID, outcome, sanitized before/after or changed fields, reason when required, source/correlation ID; filter/paginate/export within retention policy; tenant and platform streams separated.
- **P2:** Integrity sealing, external SIEM export, approval-event linkage, access-review reports.
- **Future:** Independently verifiable audit ledger where regulation requires it.

**Validation:** Required identity/action/outcome; immutable event; sensitive values redacted; export range bounded; timestamps server-controlled.

**Permissions:** `audit.view` scoped to tenant; Platform Administrator sees platform events and tenant operational metadata only under support policy; no user edits audit records.

**Edge cases:** Deleted/suspended actor; service events; failed actions; bulk job; corrected personal data; retention expiry.

**Failure cases:** Audit persistence unavailable during material mutation. Security/configuration mutations must fail closed or use a durable atomic outbox; never silently proceed unaudited.

**Acceptance criteria:** Material actions produce one correlated audit record; records cannot be changed via product APIs; filters cannot cross tenant; secrets/tokens never appear in event payloads.

### 6.24 Notification Preferences

**Actors:** All users; Organization Administrator for mandatory policy channels.

**User stories:** Users choose which permitted alerts/reports they receive, by channel, severity, scope, and quiet hours.

**Functional requirements:**
- **MVP:** In-app and email preferences by event category/severity; scoped subscriptions; site-timezone-aware quiet hours; immediate versus digest where supported; verify destination inherited from identity; mandatory security notifications cannot be disabled.
- **P2:** SMS/push/webhook channels, escalation overrides, digest scheduling, alternate verified destinations.
- **Future:** Intelligent notification grouping.

**Validation:** Valid timezone/schedule; user has access to subscribed resource; verified destination; at least one mandatory channel where policy requires; frequency bounds.

**Permissions:** Users manage own preferences; Organization Administrator sets policy defaults/mandatory classes but cannot silently subscribe users to inaccessible resources.

**Edge cases:** User loses site access; DST quiet hours; multiple alerts grouped; disabled email; role change.

**Failure cases:** Provider outage, bounce, rate limit, invalid destination. Alert remains authoritative in-app; delivery attempts/status are recorded and retried by policy.

**Acceptance criteria:** Preferences affect only future deliveries; mandatory notices remain enabled; revoked resource access removes subscription; delivery failure does not lose the underlying alert.

### 6.25 Operational Monitoring

**Actors:** Platform Administrator; authorized Organization Administrator for own connectors; support operator.

**User stories:** Operators assess application and data-pipeline health and diagnose failures without exposing tenant data.

**Functional requirements:**
- **MVP:** Dashboard for service readiness, telemetry freshness/lag, rejected/duplicate readings, Kafka consumer lag, dead-letter count, batch/report status, provider health, calculation backlog/failure, notification delivery, and last successful processing by source; filter by authorized tenant/source and link sanitized correlation details; safe retry for explicitly retryable jobs.
- **P2:** SLO/error-budget views, maintenance controls, runbook links, automated incident routing, tenant-visible status.
- **Future:** Automated remediation with bounded policies.

**Validation:** Time ranges/filter bounds; diagnostics redacted; retry idempotency and allowed job state; operational metrics cannot be used to infer unauthorized tenant content.

**Permissions:** `platform.monitor` platform-wide; organization operators get `operations.view` only for their tenant; retry requires stronger permission and audit.

**Edge cases:** Metrics pipeline itself stale; partial regional outage; high-cardinality source; recovered job with stale alert; clock skew.

**Failure cases:** Monitoring backend unavailable, retry rejected, dependency timeout. Display monitoring freshness and fail safely; do not report green from absent data.

**Acceptance criteria:** Operators can identify stale meter/source and failed job from dashboard; every manual retry is idempotent/audited; tenant operators cannot see other tenants; stale monitoring data is visibly marked.

## 7. Cross-Cutting Functional Requirements

### 7.1 Data Quality and Reconciliation

- Every aggregate displays coverage and quality sufficient to interpret it.
- Duplicate telemetry is rejected idempotently and counted operationally.
- Corrections preserve original provenance and calculation/report snapshots.
- Portfolio totals define inclusion rules and prevent parent/submeter or zone double counting.
- Unknown, zero, estimated, and not-applicable are distinct states.

### 7.2 Search, Filtering, Pagination, and Exports

Potentially large collections support scoped filters, allowlisted sorting, and pagination. UI state is shareable only where it cannot leak sensitive identifiers. Exports use asynchronous jobs above synchronous thresholds and apply the same authorization as interactive access.

### 7.3 Accessibility and Responsive Experience

All critical workflows must be keyboard operable, use visible focus, semantic headings/landmarks, accessible names and errors, non-color-only status, and chart text/table alternatives. Interfaces support desktop, tablet, and mobile; dense analytical tables may use accessible horizontal scrolling rather than removing data.

### 7.4 Localization and Time

MVP UI is English but formats locale-aware numbers/dates and explicit currencies/units. UTC is stored; site-local time is used for operational periods and shown with timezone. Ambiguous/repeated DST intervals remain distinguishable.

### 7.5 Auditability and Explainability

Every cost, carbon, baseline, anomaly, and forecast result exposes method/version, as-of time, input period, quality/coverage, and relevant provenance. Material configuration changes and lifecycle actions are audited.

### 7.6 Retention and Deletion

Retention is configurable only after policy requirements are approved. MVP archives business entities rather than hard-deleting referenced records. Personal-data requests must preserve legally necessary audit integrity while minimizing or pseudonymizing data according to approved policy.

## 8. Non-Functional Product Requirements

Exact production SLOs require expected scale and business criticality; the following are product requirements to finalize before implementation acceptance:

- Availability, latency, telemetry-to-dashboard freshness, alert-detection latency, report completion, recovery objectives, and support hours must have measurable service levels.
- All requests/messages use correlation and trace identifiers; logs are structured and sanitized.
- Mutations and ingestion are idempotent where clients or infrastructure may retry.
- Calculations are deterministic, decimal-safe, versioned, and reproducible.
- Tenant isolation and RBAC are verified through automated negative tests.
- The platform degrades explicitly: stale, incomplete, estimated, delayed, and unavailable states are user-visible.
- Backups and restore are tested; report artifacts and authoritative records have defined retention.
- External providers sit behind replaceable adapters with timeout, bounded retry, circuit breaking, and health visibility.
- Security follows `docs/SECURITY.md`; API behavior follows `docs/API_CONVENTIONS.md`; testing follows `docs/TEST_STRATEGY.md`.

## 9. MVP Release Acceptance

MVP is acceptable only when:

1. All MVP module acceptance criteria are implemented and tested against authorized and unauthorized roles.
2. A complete onboarding journey reaches visible simulated telemetry without manual database intervention.
3. Live/historical consumption, demand, cost, carbon, renewable, baseline, target, and budget values reconcile against approved golden datasets.
4. Missing, estimated, stale, invalid, and duplicate readings are represented correctly.
5. Alerts can be triggered, delivered, acknowledged, resolved, and audited idempotently.
6. Reports reproduce their calculation snapshot and remain tenant-isolated.
7. Operational monitoring detects representative ingestion, provider, calculation, notification, and reporting failures.
8. Accessibility, responsive behavior, performance, security, recovery, and observability gates are met.
9. No unresolved critical/high security defects or data-integrity defects remain.
10. Product owners approve documented calculation methods, factor/tariff provenance, permission matrix, and reporting terminology.

## 10. Product Decisions Required Before Implementation

1. Expected tenant/site/meter counts, telemetry interval, event throughput, and retention.
2. Initial countries, currencies, grid regions, tariff complexity, and carbon-factor providers.
3. OIDC identity provider, invitation/provisioning model, session policy, and support-access workflow.
4. Exact role-permission matrix and whether financial access is independently restricted.
5. MVP report templates, branding, maximum export sizes, and artifact retention.
6. Treatment of export, storage, onsite generation, certificates, residual mix, and market-based claims.
7. Baseline and forecast minimum data coverage and approved algorithms.
8. Alert latency/severity/escalation policy and notification providers.
9. Data correction, approval, retention, privacy, residency, and audit requirements.
10. Availability, freshness, performance, RPO/RTO, and disaster-recovery objectives.

Until resolved, these items must remain configurable boundaries or explicit blocked decisions—not hidden assumptions.
