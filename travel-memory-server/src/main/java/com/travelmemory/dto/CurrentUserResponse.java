package com.travelmemory.dto;
import com.travelmemory.security.UserPrincipal;
public record CurrentUserResponse(Long id, String username, String displayName, String avatarUrl, String role, boolean mustChangePassword) {
 public static CurrentUserResponse from(UserPrincipal user){return new CurrentUserResponse(user.getId(),user.getUsername(),user.getDisplayName(),user.getAvatarUrl(),user.getRole(),user.mustChangePassword());}}
