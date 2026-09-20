package com.enerlytics.facility.api.dto;

import com.enerlytics.facility.domain.FloorAreaUnit;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;

public record UpdateBuildingRequest(
        @Size(max = 200) String name,
        @Size(max = 2000) String description,
        @DecimalMin(value = "0.0001", inclusive = true)
        @Digits(integer = 14, fraction = 4)
        BigDecimal floorArea,
        FloorAreaUnit floorAreaUnit,
        @Size(max = 64) String buildingType,
        LocalDate commissionedDate) {
}
