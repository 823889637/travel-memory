package com.travelmemory.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class CompanionSaveRequest {

    @NotBlank(message = "name is required")
    @Size(max = 50, message = "name cannot exceed 50 characters")
    private String name;
    @Size(max = 255, message = "avatarUrl cannot exceed 255 characters")
    private String avatarUrl;
    private Boolean isSelf;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getAvatarUrl() { return avatarUrl; }
    public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }
    public Boolean getIsSelf() { return isSelf; }
    public void setIsSelf(Boolean isSelf) { this.isSelf = isSelf; }
}
