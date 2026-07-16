package com.travelmemory.dto;

public class CompanionSummary {

    private Long id;
    private String name;
    private String avatarUrl;
    private Boolean isSelf;

    public CompanionSummary() {
    }

    public CompanionSummary(Long id, String name) {
        this.id = id;
        this.name = name;
    }

    public CompanionSummary(Long id, String name, String avatarUrl, Boolean isSelf) {
        this.id = id;
        this.name = name;
        this.avatarUrl = avatarUrl;
        this.isSelf = isSelf;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getAvatarUrl() { return avatarUrl; }
    public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }
    public Boolean getIsSelf() { return isSelf; }
    public void setIsSelf(Boolean isSelf) { this.isSelf = isSelf; }
}
