package com.travelmemory.controller;

import com.travelmemory.common.Result;
import com.travelmemory.common.StoredFile;
import com.travelmemory.dto.CurrentUserResponse;
import com.travelmemory.exception.BusinessException;
import com.travelmemory.security.CurrentUser;
import com.travelmemory.security.UserPrincipal;
import com.travelmemory.service.AppUserService;
import com.travelmemory.service.FileStorageService;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/profile/avatar")
public class ProfileController {
    private final FileStorageService fileStorageService;
    private final AppUserService appUserService;
    private final CurrentUser currentUser;

    public ProfileController(FileStorageService fileStorageService, AppUserService appUserService,
            CurrentUser currentUser) {
        this.fileStorageService = fileStorageService;
        this.appUserService = appUserService;
        this.currentUser = currentUser;
    }

    @PostMapping
    public Result<CurrentUserResponse> upload(@RequestParam MultipartFile photo) {
        Long userId = currentUser.requireId();
        StoredFile stored = fileStorageService.store(photo, userId);
        if (stored == null) throw new BusinessException(400, "Photo file is required");
        return Result.success(refresh(appUserService.updateAvatar(userId, stored.getUrl())));
    }

    @DeleteMapping
    public Result<CurrentUserResponse> clear() {
        return Result.success(refresh(appUserService.updateAvatar(currentUser.requireId(), null)));
    }

    private CurrentUserResponse refresh(UserPrincipal principal) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
        return CurrentUserResponse.from(principal);
    }
}
