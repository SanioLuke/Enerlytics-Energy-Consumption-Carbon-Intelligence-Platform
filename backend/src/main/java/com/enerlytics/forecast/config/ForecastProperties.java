package com.enerlytics.forecast.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

@ConfigurationProperties(prefix = "enerlytics.forecast")
public class ForecastProperties {

    /** Days of history loaded before the forecast start for baselines/trend. */
    private int historyLookbackDays = 28;

    /** Minimum hourly history samples required for NEXT_24_HOURS forecasts. */
    private int minHistorySamplesHour = 336;

    /** Minimum daily history samples required for NEXT_7_DAYS forecasts. */
    private int minHistorySamplesDay = 14;

    /** Completeness below which a history bucket is ignored (missing telemetry). */
    private BigDecimal minCompletenessPct = new BigDecimal("50");

    /** Minimum samples per seasonal position before the overall mean is used. */
    private int minSeasonalSamples = 2;

    /** Width of the prediction interval: bounds = predicted ± z × sigma. */
    private BigDecimal intervalZ = new BigDecimal("1.96");

    /** Fallback interval width (% of prediction) when sigma cannot be computed. */
    private BigDecimal fallbackMarginPct = new BigDecimal("15");

    /** Actuals below this floor are excluded from MAPE (division is meaningless). */
    private BigDecimal mapeMinActualKwh = new BigDecimal("0.001");

    /** Days of history used by the trend estimator (tail of the loaded window). */
    private int trendLookbackDays = 14;

    public int getHistoryLookbackDays() { return historyLookbackDays; }
    public void setHistoryLookbackDays(int v) { this.historyLookbackDays = v; }
    public int getMinHistorySamplesHour() { return minHistorySamplesHour; }
    public void setMinHistorySamplesHour(int v) { this.minHistorySamplesHour = v; }
    public int getMinHistorySamplesDay() { return minHistorySamplesDay; }
    public void setMinHistorySamplesDay(int v) { this.minHistorySamplesDay = v; }
    public BigDecimal getMinCompletenessPct() { return minCompletenessPct; }
    public void setMinCompletenessPct(BigDecimal v) { this.minCompletenessPct = v; }
    public int getMinSeasonalSamples() { return minSeasonalSamples; }
    public void setMinSeasonalSamples(int v) { this.minSeasonalSamples = v; }
    public BigDecimal getIntervalZ() { return intervalZ; }
    public void setIntervalZ(BigDecimal v) { this.intervalZ = v; }
    public BigDecimal getFallbackMarginPct() { return fallbackMarginPct; }
    public void setFallbackMarginPct(BigDecimal v) { this.fallbackMarginPct = v; }
    public BigDecimal getMapeMinActualKwh() { return mapeMinActualKwh; }
    public void setMapeMinActualKwh(BigDecimal v) { this.mapeMinActualKwh = v; }
    public int getTrendLookbackDays() { return trendLookbackDays; }
    public void setTrendLookbackDays(int v) { this.trendLookbackDays = v; }
}
