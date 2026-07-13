package com.travelmemory.dto;

import java.math.BigDecimal;

public record LocationSearchResult(
        String id,
        String name,
        String address,
        String district,
        BigDecimal latitude,
        BigDecimal longitude,
        Integer distance
) {
}
