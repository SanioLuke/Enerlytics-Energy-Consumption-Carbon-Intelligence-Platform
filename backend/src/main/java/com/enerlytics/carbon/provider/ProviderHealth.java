package com.enerlytics.carbon.provider;

import java.time.Instant;

/**
 * @param status      current provider status
 * @param checkedAt   when the health check was last evaluated
 * @param message     human-readable detail, never credentials or tokens
 */
public record ProviderHealth(ProviderStatus status, Instant checkedAt, String message) {
}
