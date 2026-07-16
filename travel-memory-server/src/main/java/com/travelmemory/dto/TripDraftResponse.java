package com.travelmemory.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record TripDraftResponse(
        Long id,
        String title,
        String destination,
        LocalDate startDate,
        LocalDate endDate,
        String description,
        String notes,
        String coverPhotoUrl,
        LocalDateTime updateTime
) {
}
