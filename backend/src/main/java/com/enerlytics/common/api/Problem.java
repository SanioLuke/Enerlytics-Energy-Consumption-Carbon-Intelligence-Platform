package com.enerlytics.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Map;

/**
 * RFC 9457 problem detail response with Enerlytics-specific extensions.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record Problem(
        String type,
        String title,
        int status,
        String detail,
        String instance,
        String code,
        String correlationId,
        List<Map<String, String>> errors) {
}
