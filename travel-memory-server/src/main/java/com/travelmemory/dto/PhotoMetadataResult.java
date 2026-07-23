package com.travelmemory.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class PhotoMetadataResult {

    private LocalDateTime photoTakenTime;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private boolean hasExifTime;
    private boolean hasExifLocation;

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

    public boolean isHasExifTime() {
        return hasExifTime;
    }

    public void setHasExifTime(boolean hasExifTime) {
        this.hasExifTime = hasExifTime;
    }

    public boolean isHasExifLocation() {
        return hasExifLocation;
    }

    public void setHasExifLocation(boolean hasExifLocation) {
        this.hasExifLocation = hasExifLocation;
    }
}
