package com.travelmemory.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public class MemoryDraftResponse {
    private Long id;
    private Long tripId;
    private Long memoryId;
    private String content;
    private String locationName;
    private LocalDateTime recordTime;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private List<String> photoUrls;
    private List<Long> companionIds;
    private LocalDateTime updateTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
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
    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
}
