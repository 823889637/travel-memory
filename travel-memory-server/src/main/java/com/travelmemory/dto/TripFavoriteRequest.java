package com.travelmemory.dto;

import jakarta.validation.constraints.NotNull;

public class TripFavoriteRequest {
    @NotNull(message = "favorite is required")
    private Boolean favorite;

    public Boolean getFavorite() { return favorite; }
    public void setFavorite(Boolean favorite) { this.favorite = favorite; }
}
