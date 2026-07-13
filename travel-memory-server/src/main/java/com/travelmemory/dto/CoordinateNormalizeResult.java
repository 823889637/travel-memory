package com.travelmemory.dto;

import java.math.BigDecimal;

public record CoordinateNormalizeResult(
        BigDecimal latitude,
        BigDecimal longitude,
        String coordinateSystem
) {
}
