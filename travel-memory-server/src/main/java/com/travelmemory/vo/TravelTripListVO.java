package com.travelmemory.vo;

import java.time.LocalDate;

public class TravelTripListVO {

    private Long id;
    private String title;
    private String description;
    private String destination;
    private LocalDate startDate;
    private LocalDate endDate;
    private String coverPhotoUrl;
    private String effectiveCoverPhotoUrl;
    private Long memoryCount;
    private Long photoCount;
    private Long locationCount;
    private Boolean isFavorite;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getDestination() {
        return destination;
    }

    public void setDestination(String destination) {
        this.destination = destination;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }

    public String getCoverPhotoUrl() {
        return coverPhotoUrl;
    }

    public void setCoverPhotoUrl(String coverPhotoUrl) {
        this.coverPhotoUrl = coverPhotoUrl;
    }

    public String getEffectiveCoverPhotoUrl() {
        return effectiveCoverPhotoUrl;
    }

    public void setEffectiveCoverPhotoUrl(String effectiveCoverPhotoUrl) {
        this.effectiveCoverPhotoUrl = effectiveCoverPhotoUrl;
    }

    public Long getMemoryCount() {
        return memoryCount;
    }

    public void setMemoryCount(Long memoryCount) {
        this.memoryCount = memoryCount;
    }

    public Long getPhotoCount() {
        return photoCount;
    }

    public void setPhotoCount(Long photoCount) {
        this.photoCount = photoCount;
    }

    public Long getLocationCount() {
        return locationCount;
    }

    public void setLocationCount(Long locationCount) {
        this.locationCount = locationCount;
    }

    public Boolean getIsFavorite() { return isFavorite; }
    public void setIsFavorite(Boolean isFavorite) { this.isFavorite = isFavorite; }
}
