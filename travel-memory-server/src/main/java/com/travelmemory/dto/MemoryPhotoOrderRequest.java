package com.travelmemory.dto;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public class MemoryPhotoOrderRequest {

    @NotEmpty(message = "photoIds is required")
    private List<Long> photoIds;

    public List<Long> getPhotoIds() { return photoIds; }
    public void setPhotoIds(List<Long> photoIds) { this.photoIds = photoIds; }
}
