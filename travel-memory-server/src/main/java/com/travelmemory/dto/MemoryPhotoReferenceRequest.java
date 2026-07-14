package com.travelmemory.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Size;

public class MemoryPhotoReferenceRequest {

    private Long id;

    @Size(max = 255, message = "photoUrl must not exceed 255 characters")
    private String photoUrl;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getPhotoUrl() {
        return photoUrl;
    }

    public void setPhotoUrl(String photoUrl) {
        this.photoUrl = photoUrl;
    }

    @AssertTrue(message = "Each photo must reference either an existing photo id or an uploaded photoUrl")
    public boolean isValidReference() {
        return (id != null) != (photoUrl != null && !photoUrl.trim().isEmpty());
    }
}
