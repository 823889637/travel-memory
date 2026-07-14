package com.travelmemory.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public class MemoryUpdateRequest {

    @Size(max = 300, message = "content must not exceed 300 characters")
    private String content;

    @NotNull(message = "recordTime is required")
    private LocalDateTime recordTime;

    @Size(max = 255, message = "locationName must not exceed 255 characters")
    private String locationName;

    @DecimalMin(value = "-90", message = "latitude must be between -90 and 90")
    @DecimalMax(value = "90", message = "latitude must be between -90 and 90")
    private BigDecimal latitude;

    @DecimalMin(value = "-180", message = "longitude must be between -180 and 180")
    @DecimalMax(value = "180", message = "longitude must be between -180 and 180")
    private BigDecimal longitude;

    private List<Long> companionIds;

    @Valid
    private List<MemoryPhotoReferenceRequest> photos;

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public LocalDateTime getRecordTime() { return recordTime; }
    public void setRecordTime(LocalDateTime recordTime) { this.recordTime = recordTime; }
    public String getLocationName() { return locationName; }
    public void setLocationName(String locationName) { this.locationName = locationName; }
    public BigDecimal getLatitude() { return latitude; }
    public void setLatitude(BigDecimal latitude) { this.latitude = latitude; }
    public BigDecimal getLongitude() { return longitude; }
    public void setLongitude(BigDecimal longitude) { this.longitude = longitude; }
    public List<Long> getCompanionIds() { return companionIds; }
    public void setCompanionIds(List<Long> companionIds) { this.companionIds = companionIds; }
    public List<MemoryPhotoReferenceRequest> getPhotos() { return photos; }
    public void setPhotos(List<MemoryPhotoReferenceRequest> photos) { this.photos = photos; }

    @AssertTrue(message = "latitude and longitude must be provided together")
    public boolean isCoordinatePairValid() {
        return (latitude == null) == (longitude == null);
    }
}
