package com.travelmemory.dto;

import jakarta.validation.constraints.NotBlank;

public class PhotoMetadataRequest {

    @NotBlank(message = "Photo URL is required")
    private String photoUrl;

    public String getPhotoUrl() {
        return photoUrl;
    }

    public void setPhotoUrl(String photoUrl) {
        this.photoUrl = photoUrl;
    }
}
