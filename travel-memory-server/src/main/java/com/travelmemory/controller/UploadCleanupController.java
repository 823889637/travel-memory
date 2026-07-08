package com.travelmemory.controller;

import com.travelmemory.common.Result;
import com.travelmemory.dto.CleanupResult;
import com.travelmemory.service.OrphanUploadCleanupService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/uploads")
public class UploadCleanupController {

    private final OrphanUploadCleanupService orphanUploadCleanupService;

    public UploadCleanupController(OrphanUploadCleanupService orphanUploadCleanupService) {
        this.orphanUploadCleanupService = orphanUploadCleanupService;
    }

    @PostMapping("/cleanup-orphans")
    public Result<CleanupResult> cleanupOrphans(@RequestParam(defaultValue = "false") boolean dryRun) {
        return Result.success(orphanUploadCleanupService.cleanupOrphans(dryRun));
    }
}
