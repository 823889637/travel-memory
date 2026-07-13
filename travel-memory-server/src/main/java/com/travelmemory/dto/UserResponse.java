package com.travelmemory.dto;
import com.travelmemory.entity.AppUser;
import java.time.LocalDateTime;
public record UserResponse(Long id,String username,String displayName,String role,boolean enabled,boolean mustChangePassword,LocalDateTime lockedUntil,LocalDateTime lastLoginTime){
 public static UserResponse from(AppUser u){return new UserResponse(u.getId(),u.getUsername(),u.getDisplayName(),u.getRole(),Boolean.TRUE.equals(u.getEnabled()),Boolean.TRUE.equals(u.getMustChangePassword()),u.getLockedUntil(),u.getLastLoginTime());}}
