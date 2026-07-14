package com.travelmemory.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("memory_draft")
public class MemoryDraft {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long tripId;
    private Long memoryId;
    private String draftKey;
    private String content;
    private String locationName;
    private LocalDateTime recordTime;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private String photoUrls;
    private String companionIds;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getTripId() { return tripId; }
    public void setTripId(Long tripId) { this.tripId = tripId; }
    public Long getMemoryId() { return memoryId; }
    public void setMemoryId(Long memoryId) { this.memoryId = memoryId; }
    public String getDraftKey() { return draftKey; }
    public void setDraftKey(String draftKey) { this.draftKey = draftKey; }
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
    public String getPhotoUrls() { return photoUrls; }
    public void setPhotoUrls(String photoUrls) { this.photoUrls = photoUrls; }
    public String getCompanionIds() { return companionIds; }
    public void setCompanionIds(String companionIds) { this.companionIds = companionIds; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
}
