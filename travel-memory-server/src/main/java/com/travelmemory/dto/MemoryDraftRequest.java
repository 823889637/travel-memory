package com.travelmemory.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public class MemoryDraftRequest {

    @NotNull(message = "tripId is required")
    private Long tripId;
    private Long memoryId;

    @Size(max = 300, message = "content must not exceed 300 characters")
    private String content;

    @Size(max = 255, message = "locationName must not exceed 255 characters")
    private String locationName;

    private LocalDateTime recordTime;

    @DecimalMin(value = "-90", message = "latitude must be between -90 and 90")
    @DecimalMax(value = "90", message = "latitude must be between -90 and 90")
    private BigDecimal latitude;

    @DecimalMin(value = "-180", message = "longitude must be between -180 and 180")
    @DecimalMax(value = "180", message = "longitude must be between -180 and 180")
    private BigDecimal longitude;

    @Size(max = 6, message = "a draft can contain at most 6 photos")
    private List<String> photoUrls;
    private List<Long> companionIds;

    @AssertTrue(message = "latitude and longitude must be provided together")
    public boolean isCoordinatePairValid() { return (latitude == null) == (longitude == null); }

    public Long getTripId() { return tripId; }
    public void setTripId(Long tripId) { this.tripId = tripId; }
    public Long getMemoryId() { return memoryId; }
    public void setMemoryId(Long memoryId) { this.memoryId = memoryId; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getLocationName() { return locationName; }
    public void setLocationName(String locationName) { this.locationName = locationName; }
    public LocalDateTime getRecordTime() { return recordTime; }
    public void setRecordTime(LocalDateTime recordTime) { this.recordTime = recordTime; }
    public BigDecimal getLatitude() { return latitude; }
    public void setLatitude(BigDecimal latitude) { this.latitude = latitude; }
    public BigDecimal getLongitude() { return longitude; }
    public void setLongitude(BigDecimal longitude) { this.longitude = longitude; }
    public List<String> getPhotoUrls() { return photoUrls; }
    public void setPhotoUrls(List<String> photoUrls) { this.photoUrls = photoUrls; }
    public List<Long> getCompanionIds() { return companionIds; }
    public void setCompanionIds(List<Long> companionIds) { this.companionIds = companionIds; }
}
