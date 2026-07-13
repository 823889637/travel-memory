package com.travelmemory.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public class CoordinateNormalizeRequest {

    @NotNull(message = "latitude is required")
    private BigDecimal latitude;

    @NotNull(message = "longitude is required")
    private BigDecimal longitude;

    @NotBlank(message = "coordinateSystem is required")
    private String coordinateSystem;

    public BigDecimal getLatitude() {
        return latitude;
    }

    public void setLatitude(BigDecimal latitude) {
        this.latitude = latitude;
    }

    public BigDecimal getLongitude() {
        return longitude;
    }

    public void setLongitude(BigDecimal longitude) {
        this.longitude = longitude;
    }

    public String getCoordinateSystem() {
        return coordinateSystem;
    }

    public void setCoordinateSystem(String coordinateSystem) {
        this.coordinateSystem = coordinateSystem;
    }
}
