package com.travelmemory.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.travelmemory.dto.TripDraftRequest;
import com.travelmemory.dto.TripDraftResponse;
import com.travelmemory.entity.TripDraft;
import com.travelmemory.mapper.TripDraftMapper;
import com.travelmemory.security.CurrentUser;
import com.travelmemory.service.ProtectedUploadReferenceService;
import com.travelmemory.service.TripDraftService;
import com.travelmemory.exception.BusinessException;
import java.math.BigDecimal;
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
        validateDestinationCoordinates(request.getDestinationLatitude(), request.getDestinationLongitude());
        if (request.getDestinationLatitude() != null && normalize(request.getDestination()) == null) {
            throw new BusinessException(400, "Destination name is required when destination coordinates are provided");
        }
        Long userId = currentUser.requireId();
        TripDraft draft = find(userId);
        if (draft == null) {
            draft = new TripDraft();
            draft.setUserId(userId);
        }
        draft.setTitle(normalize(request.getTitle()));
        String destination = normalize(request.getDestination());
        draft.setDestination(destination);
        draft.setDestinationCountry(destination == null ? null : normalize(request.getDestinationCountry()));
        draft.setDestinationLatitude(request.getDestinationLatitude());
        draft.setDestinationLongitude(request.getDestinationLongitude());
        draft.setStartDate(request.getStartDate());
        draft.setEndDate(request.getEndDate());
        draft.setDescription(normalize(request.getDescription()));
        draft.setNotes(normalize(request.getNotes()));
        draft.setCoverPhotoUrl(normalizeCover(request.getCoverPhotoUrl()));
        draft.setUpdateTime(LocalDateTime.now());
        if (draft.getId() == null) {
            tripDraftMapper.insert(draft);
        } else {
            tripDraftMapper.updateById(draft);
            tripDraftMapper.update(null, new UpdateWrapper<TripDraft>()
                    .eq("id", draft.getId())
                    .set("destination_country", draft.getDestinationCountry())
                    .set("destination_latitude", draft.getDestinationLatitude())
                    .set("destination_longitude", draft.getDestinationLongitude()));
        }
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

    private void validateDestinationCoordinates(BigDecimal latitude, BigDecimal longitude) {
        if ((latitude == null) != (longitude == null)) {
            throw new BusinessException(400, "Destination latitude and longitude must be provided together");
        }
        if (latitude != null && (latitude.compareTo(BigDecimal.valueOf(-90)) < 0
                || latitude.compareTo(BigDecimal.valueOf(90)) > 0
                || longitude.compareTo(BigDecimal.valueOf(-180)) < 0
                || longitude.compareTo(BigDecimal.valueOf(180)) > 0)) {
            throw new BusinessException(400, "Destination coordinates are invalid");
        }
    }

    private TripDraftResponse toResponse(TripDraft draft) {
        return new TripDraftResponse(draft.getId(), draft.getTitle(), draft.getDestination(),
                draft.getDestinationCountry(),
                draft.getDestinationLatitude(), draft.getDestinationLongitude(),
                draft.getStartDate(), draft.getEndDate(), draft.getDescription(), draft.getNotes(),
                draft.getCoverPhotoUrl(), draft.getUpdateTime());
    }
}
