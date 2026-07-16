package com.travelmemory.dto;

import java.math.BigDecimal;

public record CitySearchResult(
        String id,
        String name,
        String level,
        BigDecimal latitude,
        BigDecimal longitude
) {
}
