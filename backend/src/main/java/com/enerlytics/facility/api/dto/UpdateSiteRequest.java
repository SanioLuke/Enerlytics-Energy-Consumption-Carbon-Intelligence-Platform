package com.enerlytics.facility.api.dto;

import com.enerlytics.facility.domain.FloorAreaUnit;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;

public record UpdateSiteRequest(
        @Size(max = 200) String name,
        @Size(max = 2000) String description,
        @Size(max = 255) String address,
        @Size(min = 2, max = 2) String country,
        @Size(max = 100) String state,
        @Size(max = 100) String city,
        @Size(max = 20) String postalCode,
        @DecimalMin(value = "-90", inclusive = true)
        @DecimalMax(value = "90", inclusive = true)
        @Digits(integer = 3, fraction = 6)
        BigDecimal latitude,
        @DecimalMin(value = "-180", inclusive = true)
        @DecimalMax(value = "180", inclusive = true)
        @Digits(integer = 3, fraction = 6)
        BigDecimal longitude,
        @Size(max = 100) String timezone,
        @Size(max = 100) String gridRegionCode,
        @Size(min = 3, max = 3) String currency,
        @DecimalMin(value = "0.0001", inclusive = true)
        @Digits(integer = 14, fraction = 4)
        BigDecimal floorArea,
        FloorAreaUnit floorAreaUnit,
        LocalDate openedOn,
        LocalDate closedOn) {
}
