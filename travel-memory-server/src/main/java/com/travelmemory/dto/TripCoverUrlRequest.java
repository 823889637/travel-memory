package com.travelmemory.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class TripCoverUrlRequest {
    @NotBlank(message = "photoUrl is required")
    @Size(max = 255, message = "photoUrl must not exceed 255 characters")
    private String photoUrl;

    public String getPhotoUrl() { return photoUrl; }
    public void setPhotoUrl(String photoUrl) { this.photoUrl = photoUrl; }
}
