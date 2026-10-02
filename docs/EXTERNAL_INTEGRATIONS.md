# External Integrations

## Electricity Maps — Carbon Intensity

Enerlytics uses the [Electricity Maps](https://www.electricitymaps.com/) carbon
intensity API to translate energy consumption into grid-specific emissions.

### Current supported endpoint

Base URL: `https://api.electricitymap.org/v3`

Endpoints consumed:

| Operation | Path | Purpose |
|---|---|---|
| Latest | `GET /carbon-intensity/latest?zone={zone}` | Most recent grid carbon intensity for a zone |
| History | `GET /carbon-intensity/history?zone={zone}` | Last 24 hours of hourly intensity for a zone |
| Forecast | `GET /carbon-intensity/forecast?zone={zone}` | Forecasted intensity where the plan supports it |

Authentication is via an `auth-token` request header containing the API key
issued from the Electricity Maps portal.

### Provider abstraction

`com.enerlytics.carbon.provider.CarbonIntensityProvider` decouples the rest of
the system from the external API. Implementations are responsible only for
fetching validated observations; persistence is handled by the domain service.

Supported operations:

- `latest(zone)` — current intensity
- `history(zone, from, to)` — bounded historical observations
- `forecast(zone)` — plan-dependent forecast; empty if disabled
- `health()` — provider connectivity status

### Production implementation

`ElectricityMapsCarbonIntensityProvider` uses Spring `RestClient` and provides:

- configurable connect/read timeouts;
- per-zone minimum request interval to avoid provider rate-limit breaches;
- Resilience4j retry on transient `5xx`/network failures;
- Resilience4j circuit breaker for cascading failure protection;
- structured logs that never include the `auth-token` value;
- Micrometer request/result counters and latency timers;
- response validation with provider-specific error translation;
- `@Cacheable` latest values using Caffeine with a configurable TTL.

### Local development

When `ELECTRICITY_MAPS_API_KEY` is absent, a deterministic
`MockCarbonIntensityProvider` is registered instead. It returns simulated values
for any zone and labels every observation with `provider=MOCK` and
`estimated=true`. Mock data is safe for local development and automated tests but
must never be used for real carbon reporting.

### Configuration

| Environment variable | Default | Purpose |
|---|---:|---|
| `ELECTRICITY_MAPS_API_KEY` | empty | API key; empty selects the mock provider |
| `ELECTRICITY_MAPS_BASE_URL` | `https://api.electricitymap.org/v3` | Base URL |
| `ELECTRICITY_MAPS_CONNECT_TIMEOUT_MS` | `5000` | TCP connect timeout |
| `ELECTRICITY_MAPS_READ_TIMEOUT_MS` | `15000` | Response read timeout |
| `ELECTRICITY_MAPS_MIN_REQUEST_INTERVAL_MS` | `1000` | Minimum interval between requests for the same zone |
| `ELECTRICITY_MAPS_FORECAST_ENABLED` | `true` | Whether to attempt forecast retrieval |
| `CARBON_INTENSITY_CACHE_TTL` | `PT5M` | Latest-value cache TTL |

### Persisted observations

Each provider response is stored as a `carbon.intensity_observation` row with:

- `provider`
- `zone`
- `observed_at`
- `carbon_intensity_gco2eq_per_kwh`
- `estimated`
- `retrieved_at`

Unique key: `(provider, zone, observed_at)`.

### Error handling

| External behavior | Translated exception | Retry | Circuit impact |
|---|---|---|---|
| HTTP 401 / 403 | `ProviderAuthenticationException` | no | recorded |
| HTTP 429 | `ProviderRateLimitException` | no | recorded |
| Other 4xx | `ProviderClientException` | no | recorded |
| 5xx / network I/O | `ProviderServerException` | yes | counts toward open |
| Invalid body | `ProviderResponseException` | no | recorded |
| Circuit breaker open | `ProviderUnavailableException` | no | — |

### Operational notes

- Keep the API key in a secret manager or environment variable. Never commit it.
- Monitor `carbon.intensity.provider.requests` and `carbon.intensity.provider.latency`.
- Tune `ELECTRICITY_MAPS_MIN_REQUEST_INTERVAL_MS` against your Electricity Maps plan limits.
- Forecast availability depends on the Electricity Maps subscription.
