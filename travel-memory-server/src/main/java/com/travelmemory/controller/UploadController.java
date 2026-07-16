package com.travelmemory.controller;

import com.travelmemory.common.Result;
import com.travelmemory.common.StoredFile;
import com.travelmemory.dto.PhotoUploadResponse;
import com.travelmemory.exception.BusinessException;
import com.travelmemory.security.CurrentUser;
import com.travelmemory.service.FileStorageService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/uploads")
public class UploadController {
    private final FileStorageService fileStorageService;
    private final CurrentUser currentUser;

    public UploadController(FileStorageService fileStorageService, CurrentUser currentUser) {
        this.fileStorageService = fileStorageService;
        this.currentUser = currentUser;
    }

    @PostMapping("/images")
    public Result<PhotoUploadResponse> uploadImage(@RequestParam MultipartFile photo) {
        StoredFile stored = fileStorageService.store(photo, currentUser.requireId());
        if (stored == null) {
            throw new BusinessException(400, "Photo file is required");
        }
        return Result.success(new PhotoUploadResponse(stored.getUrl()));
    }
}
