package com.travelmemory.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.travelmemory.dto.TripDraftRequest;
import com.travelmemory.dto.TripDraftResponse;
import com.travelmemory.entity.TripDraft;
import com.travelmemory.mapper.TripDraftMapper;
import com.travelmemory.security.CurrentUser;
import com.travelmemory.service.ProtectedUploadReferenceService;
import com.travelmemory.service.TripDraftService;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TripDraftServiceImpl implements TripDraftService {
    private final TripDraftMapper tripDraftMapper;
    private final CurrentUser currentUser;
    private final ProtectedUploadReferenceService uploadReferences;

    public TripDraftServiceImpl(TripDraftMapper tripDraftMapper, CurrentUser currentUser,
            ProtectedUploadReferenceService uploadReferences) {
        this.tripDraftMapper = tripDraftMapper;
        this.currentUser = currentUser;
        this.uploadReferences = uploadReferences;
    }

    @Override
    public TripDraftResponse get() {
        TripDraft draft = find(currentUser.requireId());
        return draft == null ? null : toResponse(draft);
    }

    @Override
    @Transactional
    public TripDraftResponse save(TripDraftRequest request) {
        Long userId = currentUser.requireId();
        TripDraft draft = find(userId);
        if (draft == null) {
            draft = new TripDraft();
            draft.setUserId(userId);
        }
        draft.setTitle(normalize(request.getTitle()));
        draft.setDestination(normalize(request.getDestination()));
        draft.setStartDate(request.getStartDate());
        draft.setEndDate(request.getEndDate());
        draft.setDescription(normalize(request.getDescription()));
        draft.setNotes(normalize(request.getNotes()));
        draft.setCoverPhotoUrl(normalizeCover(request.getCoverPhotoUrl()));
        draft.setUpdateTime(LocalDateTime.now());
        if (draft.getId() == null) tripDraftMapper.insert(draft);
        else tripDraftMapper.updateById(draft);
        return toResponse(draft);
    }

    @Override
    @Transactional
    public void delete() {
        tripDraftMapper.delete(new LambdaQueryWrapper<TripDraft>()
                .eq(TripDraft::getUserId, currentUser.requireId()));
    }

    private TripDraft find(Long userId) {
        return tripDraftMapper.selectOne(new LambdaQueryWrapper<TripDraft>()
                .eq(TripDraft::getUserId, userId)
                .last("LIMIT 1"));
    }

    private String normalizeCover(String value) {
        String normalized = normalize(value);
        return normalized == null ? null : uploadReferences.requireOwnedImage(normalized);
    }

    private String normalize(String value) {
        if (value == null) return null;
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private TripDraftResponse toResponse(TripDraft draft) {
        return new TripDraftResponse(draft.getId(), draft.getTitle(), draft.getDestination(),
                draft.getStartDate(), draft.getEndDate(), draft.getDescription(), draft.getNotes(),
                draft.getCoverPhotoUrl(), draft.getUpdateTime());
    }
}
