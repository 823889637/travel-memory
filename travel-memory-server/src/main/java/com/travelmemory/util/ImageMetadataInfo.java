package com.travelmemory.util;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class ImageMetadataInfo {

    private LocalDateTime photoTakenTime;
    private BigDecimal latitude;
    private BigDecimal longitude;

    public LocalDateTime getPhotoTakenTime() {
        return photoTakenTime;
    }

    public void setPhotoTakenTime(LocalDateTime photoTakenTime) {
        this.photoTakenTime = photoTakenTime;
    }

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

    public boolean hasTime() {
        return photoTakenTime != null;
    }

    public boolean hasLocation() {
        return latitude != null && longitude != null;
    }
}
