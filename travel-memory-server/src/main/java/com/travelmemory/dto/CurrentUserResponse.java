package com.travelmemory.dto;
import com.travelmemory.security.UserPrincipal;
public record CurrentUserResponse(Long id, String username, String displayName, String role, boolean mustChangePassword) {
 public static CurrentUserResponse from(UserPrincipal user){return new CurrentUserResponse(user.getId(),user.getUsername(),user.getDisplayName(),user.getRole(),user.mustChangePassword());}}
