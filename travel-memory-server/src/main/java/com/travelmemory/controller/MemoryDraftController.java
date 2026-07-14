package com.travelmemory.controller;

import com.travelmemory.common.Result;
import com.travelmemory.dto.MemoryDraftRequest;
import com.travelmemory.dto.MemoryDraftResponse;
import com.travelmemory.service.MemoryDraftService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/memory-drafts")
public class MemoryDraftController {

    private final MemoryDraftService memoryDraftService;

    public MemoryDraftController(MemoryDraftService memoryDraftService) {
        this.memoryDraftService = memoryDraftService;
    }

    @GetMapping
    public Result<MemoryDraftResponse> get(
            @RequestParam Long tripId,
            @RequestParam(required = false) Long memoryId
    ) {
        return Result.success(memoryDraftService.get(tripId, memoryId));
    }

    @PutMapping
    public Result<MemoryDraftResponse> save(@Valid @RequestBody MemoryDraftRequest request) {
        return Result.success(memoryDraftService.save(request));
    }

    @DeleteMapping
    public Result<Void> delete(
            @RequestParam Long tripId,
            @RequestParam(required = false) Long memoryId
    ) {
        memoryDraftService.delete(tripId, memoryId);
        return Result.success();
    }
}
