package com.travelmemory.dto;

import jakarta.validation.constraints.NotNull;

public class TripCoverRequest {

    @NotNull(message = "memoryId is required")
    private Long memoryId;

    public Long getMemoryId() {
        return memoryId;
    }

    public void setMemoryId(Long memoryId) {
        this.memoryId = memoryId;
    }
}
