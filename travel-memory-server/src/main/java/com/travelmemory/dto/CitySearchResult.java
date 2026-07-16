package com.travelmemory.dto;

import java.math.BigDecimal;

public record CitySearchResult(
        String id,
        String name,
        String level,
        String countryName,
        String provinceName,
        String cityName,
        String districtName,
        BigDecimal latitude,
        BigDecimal longitude
) {
}
