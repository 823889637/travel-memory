package com.travelmemory.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableField;
import com.travelmemory.dto.CompanionSummary;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@TableName("travel_memory")
public class TravelMemory {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tripId;

    private String content;
    private String photoUrl;
    private String photoPath;
    @TableField(exist = false)
    private List<MemoryPhoto> photos;
    @TableField(exist = false)
    private Integer photoCount;
    @TableField(exist = false)
    private List<Long> companionIds;
    @TableField(exist = false)
    private List<CompanionSummary> companions;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private String locationName;

    @NotNull(message = "recordTime is required")
    private LocalDateTime recordTime;

    private Integer isFavorite;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getTripId() {
        return tripId;
    }

    public void setTripId(Long tripId) {
        this.tripId = tripId;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getPhotoUrl() {
        return photoUrl;
    }

    public void setPhotoUrl(String photoUrl) {
        this.photoUrl = photoUrl;
    }

    public String getPhotoPath() {
        return photoPath;
    }

    public void setPhotoPath(String photoPath) {
        this.photoPath = photoPath;
    }

    public List<MemoryPhoto> getPhotos() { return photos; }
    public void setPhotos(List<MemoryPhoto> photos) { this.photos = photos; }
    public Integer getPhotoCount() { return photoCount; }
    public void setPhotoCount(Integer photoCount) { this.photoCount = photoCount; }
    public List<Long> getCompanionIds() { return companionIds; }
    public void setCompanionIds(List<Long> companionIds) { this.companionIds = companionIds; }
    public List<CompanionSummary> getCompanions() { return companions; }
    public void setCompanions(List<CompanionSummary> companions) { this.companions = companions; }

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

    public String getLocationName() {
        return locationName;
    }

    public void setLocationName(String locationName) {
        this.locationName = locationName;
    }

    public LocalDateTime getRecordTime() {
        return recordTime;
    }

    public void setRecordTime(LocalDateTime recordTime) {
        this.recordTime = recordTime;
    }

    public Integer getIsFavorite() {
        return isFavorite;
    }

    public void setIsFavorite(Integer isFavorite) {
        this.isFavorite = isFavorite;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }

    public Integer getDeleted() {
        return deleted;
    }

    public void setDeleted(Integer deleted) {
        this.deleted = deleted;
    }
}
