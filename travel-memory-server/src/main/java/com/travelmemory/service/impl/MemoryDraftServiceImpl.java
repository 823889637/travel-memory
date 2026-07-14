package com.travelmemory.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.travelmemory.dto.MemoryDraftRequest;
import com.travelmemory.dto.MemoryDraftResponse;
import com.travelmemory.entity.MemoryDraft;
import com.travelmemory.entity.TravelMemory;
import com.travelmemory.entity.TripCompanion;
import com.travelmemory.exception.BusinessException;
import com.travelmemory.mapper.MemoryDraftMapper;
import com.travelmemory.mapper.TravelMemoryMapper;
import com.travelmemory.security.CurrentUser;
import com.travelmemory.security.UploadPathGuard;
import com.travelmemory.service.MemoryDraftService;
import com.travelmemory.service.TravelTripService;
import com.travelmemory.service.TripCompanionService;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MemoryDraftServiceImpl implements MemoryDraftService {

    private static final int MAX_PHOTOS = 6;

    private final MemoryDraftMapper memoryDraftMapper;
    private final TravelMemoryMapper travelMemoryMapper;
    private final TravelTripService travelTripService;
    private final TripCompanionService tripCompanionService;
    private final CurrentUser currentUser;

    @Value("${app.upload.dir:../uploads}")
    private String uploadDir;

    public MemoryDraftServiceImpl(
            MemoryDraftMapper memoryDraftMapper,
            TravelMemoryMapper travelMemoryMapper,
            TravelTripService travelTripService,
            TripCompanionService tripCompanionService,
            CurrentUser currentUser
    ) {
        this.memoryDraftMapper = memoryDraftMapper;
        this.travelMemoryMapper = travelMemoryMapper;
        this.travelTripService = travelTripService;
        this.tripCompanionService = tripCompanionService;
        this.currentUser = currentUser;
    }

    @Override
    public MemoryDraftResponse get(Long tripId, Long memoryId) {
        verifyTarget(tripId, memoryId);
        MemoryDraft draft = find(currentUser.requireId(), draftKey(tripId, memoryId));
        return draft == null ? null : toResponse(draft);
    }

    @Override
    @Transactional
    public MemoryDraftResponse save(MemoryDraftRequest request) {
        verifyTarget(request.getTripId(), request.getMemoryId());
        List<String> photoUrls = normalizePhotoUrls(request.getPhotoUrls());
        List<Long> companionIds = normalizeCompanionIds(request.getTripId(), request.getCompanionIds());
        Long userId = currentUser.requireId();
        String key = draftKey(request.getTripId(), request.getMemoryId());
        MemoryDraft draft = find(userId, key);
        if (draft == null) {
            draft = new MemoryDraft();
            draft.setUserId(userId);
            draft.setTripId(request.getTripId());
            draft.setMemoryId(request.getMemoryId());
            draft.setDraftKey(key);
        }
        draft.setContent(normalizeNullable(request.getContent()));
        draft.setLocationName(normalizeNullable(request.getLocationName()));
        draft.setRecordTime(request.getRecordTime());
        draft.setLatitude(request.getLatitude());
        draft.setLongitude(request.getLongitude());
        draft.setPhotoUrls(String.join("\n", photoUrls));
        draft.setCompanionIds(companionIds.stream().map(String::valueOf).collect(Collectors.joining(",")));
        draft.setUpdateTime(LocalDateTime.now());

        if (draft.getId() == null) memoryDraftMapper.insert(draft);
        else memoryDraftMapper.updateById(draft);
        return toResponse(draft);
    }

    @Override
    @Transactional
    public void delete(Long tripId, Long memoryId) {
        verifyTarget(tripId, memoryId);
        Long userId = currentUser.requireId();
        memoryDraftMapper.delete(new LambdaQueryWrapper<MemoryDraft>()
                .eq(MemoryDraft::getUserId, userId)
                .eq(MemoryDraft::getDraftKey, draftKey(tripId, memoryId)));
    }

    private void verifyTarget(Long tripId, Long memoryId) {
        if (tripId == null) throw new BusinessException(400, "tripId is required");
        travelTripService.getById(tripId);
        if (memoryId == null) return;
        TravelMemory memory = travelMemoryMapper.selectById(memoryId);
        if (memory == null || !tripId.equals(memory.getTripId())) {
            throw new BusinessException(404, "Memory not found");
        }
    }

    private MemoryDraft find(Long userId, String key) {
        return memoryDraftMapper.selectOne(new LambdaQueryWrapper<MemoryDraft>()
                .eq(MemoryDraft::getUserId, userId)
                .eq(MemoryDraft::getDraftKey, key)
                .last("LIMIT 1"));
    }

    private String draftKey(Long tripId, Long memoryId) {
        return memoryId == null ? "create:" + tripId : "edit:" + memoryId;
    }

    private List<Long> normalizeCompanionIds(Long tripId, List<Long> values) {
        if (values == null || values.isEmpty()) return List.of();
        List<Long> normalized = values.stream().filter(value -> value != null && value > 0).distinct().toList();
        Set<Long> allowed = tripCompanionService.list(tripId).stream().map(TripCompanion::getId).collect(Collectors.toSet());
        if (!allowed.containsAll(normalized)) throw new BusinessException(404, "Companion not found");
        return normalized;
    }

    private List<String> normalizePhotoUrls(List<String> values) {
        if (values == null || values.isEmpty()) return List.of();
        if (values.size() > MAX_PHOTOS) throw new BusinessException(400, "A draft can contain at most 6 photos");
        Set<String> seen = new HashSet<>();
        List<String> normalized = new ArrayList<>();
        for (String value : values) {
            String url = normalizeAndVerifyUploadUrl(value);
            if (!seen.add(url)) throw new BusinessException(400, "Duplicate photo URLs are not allowed");
            normalized.add(url);
        }
        return normalized;
    }

    private String normalizeAndVerifyUploadUrl(String value) {
        if (value == null || value.trim().isEmpty()) throw new BusinessException(400, "Photo URL is required");
        String raw = value.trim();
        try {
            if (URI.create(raw).isAbsolute()) throw new BusinessException(400, "Photo URL must use /uploads/");
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(400, "Invalid photo URL");
        }
        String decoded;
        try {
            decoded = URLDecoder.decode(raw, StandardCharsets.UTF_8).replace('\\', '/');
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(400, "Invalid photo URL");
        }
        if (!decoded.startsWith("/uploads/") || decoded.contains("?") || decoded.contains("#")) {
            throw new BusinessException(400, "Photo URL must use /uploads/");
        }
        String relative = decoded.substring("/uploads/".length());
        Long userId = currentUser.requireId();
        String userPrefix = "users/" + userId + "/";
        if (relative.isBlank() || (!relative.startsWith(userPrefix) && !(userId.equals(1L) && !relative.startsWith("users/")))) {
            throw new BusinessException(404, "Photo URL not found");
        }
        Path root = Paths.get(uploadDir).toAbsolutePath().normalize();
        Path candidate;
        try {
            candidate = root.resolve(relative).normalize();
        } catch (RuntimeException exception) {
            throw new BusinessException(400, "Invalid photo URL");
        }
        if (!UploadPathGuard.isSafeRegularFile(root, candidate)) {
            throw new BusinessException(400, "Photo URL does not reference an uploaded file");
        }
        return "/uploads/" + root.relativize(candidate).toString().replace('\\', '/');
    }

    private String normalizeNullable(String value) {
        if (value == null) return null;
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private MemoryDraftResponse toResponse(MemoryDraft draft) {
        MemoryDraftResponse response = new MemoryDraftResponse();
        response.setId(draft.getId());
        response.setTripId(draft.getTripId());
        response.setMemoryId(draft.getMemoryId());
        response.setContent(draft.getContent());
        response.setLocationName(draft.getLocationName());
        response.setRecordTime(draft.getRecordTime());
        response.setLatitude(draft.getLatitude());
        response.setLongitude(draft.getLongitude());
        response.setPhotoUrls(parsePhotoUrls(draft.getPhotoUrls()));
        response.setCompanionIds(parseCompanionIds(draft.getCompanionIds()));
        response.setUpdateTime(draft.getUpdateTime());
        return response;
    }

    private List<String> parsePhotoUrls(String value) {
        if (value == null || value.isBlank()) return List.of();
        return value.lines().map(String::trim).filter(item -> !item.isEmpty()).limit(MAX_PHOTOS).toList();
    }

    private List<Long> parseCompanionIds(String value) {
        if (value == null || value.isBlank()) return List.of();
        List<Long> result = new ArrayList<>();
        for (String item : value.split(",")) {
            try { result.add(Long.valueOf(item)); }
            catch (NumberFormatException ignored) { }
        }
        return result;
    }
}
