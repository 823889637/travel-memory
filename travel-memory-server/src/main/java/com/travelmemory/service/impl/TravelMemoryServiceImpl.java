package com.travelmemory.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.travelmemory.common.StoredFile;
import com.travelmemory.dto.UploadResult;
import com.travelmemory.dto.MemoryPhotoReferenceRequest;
import com.travelmemory.dto.MemoryUpdateRequest;
import com.travelmemory.entity.MemoryPhoto;
import com.travelmemory.entity.TravelMemory;
import com.travelmemory.exception.BusinessException;
import com.travelmemory.mapper.MemoryPhotoMapper;
import com.travelmemory.mapper.TravelMemoryMapper;
import com.travelmemory.service.FileStorageService;
import com.travelmemory.service.TravelMemoryService;
import com.travelmemory.service.TravelTripService;
import com.travelmemory.service.TripCompanionService;
import com.travelmemory.security.CurrentUser;
import com.travelmemory.security.UploadPathGuard;
import com.travelmemory.util.ImageMetadataExtractor;
import com.travelmemory.util.ImageMetadataInfo;
import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class TravelMemoryServiceImpl extends ServiceImpl<TravelMemoryMapper, TravelMemory> implements TravelMemoryService {

    private static final int MAX_PHOTOS = 6;

    private final TravelMemoryMapper travelMemoryMapper;
    private final MemoryPhotoMapper memoryPhotoMapper;
    private final TravelTripService travelTripService;
    private final FileStorageService fileStorageService;
    private final ImageMetadataExtractor imageMetadataExtractor;
    private final CurrentUser currentUser;
    private final TripCompanionService tripCompanionService;

    @Value("${app.upload.dir:../uploads}")
    private String uploadDir;

    @Autowired
    public TravelMemoryServiceImpl(TravelMemoryMapper travelMemoryMapper, MemoryPhotoMapper memoryPhotoMapper,
            TravelTripService travelTripService, FileStorageService fileStorageService,
            ImageMetadataExtractor imageMetadataExtractor, CurrentUser currentUser,
            TripCompanionService tripCompanionService) {
        this.travelMemoryMapper = travelMemoryMapper;
        this.memoryPhotoMapper = memoryPhotoMapper;
        this.travelTripService = travelTripService;
        this.fileStorageService = fileStorageService;
        this.imageMetadataExtractor = imageMetadataExtractor;
        this.currentUser = currentUser;
        this.tripCompanionService = tripCompanionService;
    }

    @Override
    public List<TravelMemory> timeline(Long tripId) {
        travelTripService.getById(tripId);
        return attachPhotos(travelMemoryMapper.selectList(new LambdaQueryWrapper<TravelMemory>()
                .eq(TravelMemory::getTripId, tripId).orderByAsc(TravelMemory::getRecordTime)
                .orderByAsc(TravelMemory::getCreateTime)));
    }

    @Override
    public List<TravelMemory> search(Long tripId, String keyword) {
        String normalizedKeyword = keyword == null ? "" : keyword.trim();
        if (normalizedKeyword.isEmpty()) return List.of();
        if (tripId == null) throw new BusinessException(400, "tripId is required");
        travelTripService.getById(tripId);
        LambdaQueryWrapper<TravelMemory> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TravelMemory::getTripId, tripId);
        wrapper.and(query -> query.like(TravelMemory::getContent, normalizedKeyword)
                .or().like(TravelMemory::getLocationName, normalizedKeyword))
                .orderByAsc(TravelMemory::getRecordTime).orderByAsc(TravelMemory::getCreateTime);
        return attachPhotos(travelMemoryMapper.selectList(wrapper));
    }

    @Override
    public TravelMemory getById(Long id) {
        TravelMemory memory = travelMemoryMapper.selectById(id);
        if (memory == null) throw new BusinessException(404, "Memory not found");
        travelTripService.getById(memory.getTripId());
        return attachPhotos(List.of(memory)).get(0);
    }

    @Override
    @Transactional
    public TravelMemory create(TravelMemory memory, List<MultipartFile> uploads) {
        travelTripService.getById(memory.getTripId());
        if (memory.getIsFavorite() == null) memory.setIsFavorite(0);
        List<MemoryPhoto> photos = new ArrayList<>();
        if (memory.getPhotos() != null) {
            memory.getPhotos().forEach(photo -> { if (hasUrl(photo.getPhotoUrl())) photos.add(copyPhoto(photo)); });
        }
        if (uploads != null) {
            for (MultipartFile upload : uploads) {
                if (upload == null || upload.isEmpty()) continue;
                UploadResult result = uploadPhoto(upload);
                MemoryPhoto photo = new MemoryPhoto();
                photo.setPhotoUrl(result.getPhotoUrl());
                photos.add(photo);
                if (photos.size() == 1) applyPhotoMetadata(memory, result);
            }
        }
        if (photos.isEmpty() && hasUrl(memory.getPhotoUrl())) {
            MemoryPhoto photo = new MemoryPhoto(); photo.setPhotoUrl(memory.getPhotoUrl()); photos.add(photo);
        }
        validatePhotoCount(photos.size());
        validatePhotoUrls(photos);
        setPrimary(memory, photos);
        if (memory.getRecordTime() == null) memory.setRecordTime(LocalDateTime.now());
        normalizeMemoryFields(memory);
        validateMemoryText(memory.getContent(), memory.getLocationName());
        validateCoordinates(memory.getLatitude(), memory.getLongitude());
        memory.setPhotos(null); memory.setPhotoCount(null);
        travelMemoryMapper.insert(memory);
        persistPhotos(memory.getId(), photos);
        tripCompanionService.replaceMemoryCompanions(memory.getId(), memory.getTripId(), memory.getCompanionIds());
        return getById(memory.getId());
    }

    @Override
    public UploadResult uploadPhoto(MultipartFile photo) {
        StoredFile storedFile = fileStorageService.store(photo, currentUser.requireId());
        if (storedFile == null) throw new BusinessException(400, "Photo file is required");
        ImageMetadataInfo info = imageMetadataExtractor.extract(storedFile.getPath());
        UploadResult result = new UploadResult();
        result.setPhotoUrl(storedFile.getUrl()); result.setPhotoPath(storedFile.getPath());
        result.setPhotoTakenTime(info.getPhotoTakenTime()); result.setLatitude(info.getLatitude()); result.setLongitude(info.getLongitude());
        result.setHasExifTime(info.hasTime()); result.setHasExifLocation(info.hasLocation());
        return result;
    }

    @Override
    @Transactional
    public UploadResult uploadPhoto(Long id, MultipartFile photo) {
        TravelMemory memory = getById(id);
        UploadResult result = uploadPhoto(photo);
        List<MemoryPhoto> photos = new ArrayList<>(memory.getPhotos());
        if (photos.isEmpty()) {
            MemoryPhoto item = new MemoryPhoto(); item.setMemoryId(id); item.setPhotoUrl(result.getPhotoUrl()); item.setSortOrder(0); memoryPhotoMapper.insert(item);
        } else {
            MemoryPhoto primary = photos.get(0); primary.setPhotoUrl(result.getPhotoUrl()); memoryPhotoMapper.updateById(primary);
        }
        updatePrimary(memory, loadPhotos(id));
        return result;
    }

    @Override
    @Transactional
    public TravelMemory addPhoto(Long id, MultipartFile photo) {
        TravelMemory memory = getById(id);
        List<MemoryPhoto> photos = loadPhotos(id);
        validatePhotoCount(photos.size() + 1);
        UploadResult result = uploadPhoto(photo);
        MemoryPhoto item = new MemoryPhoto(); item.setMemoryId(id); item.setPhotoUrl(result.getPhotoUrl()); item.setSortOrder(photos.size());
        memoryPhotoMapper.insert(item);
        if (photos.isEmpty()) updatePrimary(memory, List.of(item));
        return getById(id);
    }

    @Override
    @Transactional
    public TravelMemory deletePhoto(Long id, Long photoId) {
        TravelMemory memory = getById(id);
        List<MemoryPhoto> photos = loadPhotos(id);
        MemoryPhoto target = photos.stream().filter(photo -> photoId.equals(photo.getId())).findFirst()
                .orElseThrow(() -> new BusinessException(404, "Photo not found"));
        memoryPhotoMapper.deleteById(target.getId());
        updatePrimary(memory, loadPhotos(id));
        return getById(id);
    }

    @Override
    @Transactional
    public TravelMemory reorderPhotos(Long id, List<Long> photoIds) {
        TravelMemory memory = getById(id);
        List<MemoryPhoto> current = loadPhotos(id);
        if (photoIds == null || photoIds.size() != current.size() || new HashSet<>(photoIds).size() != current.size()) {
            throw new BusinessException(400, "Photo order does not match current photos");
        }
        Map<Long, MemoryPhoto> byId = new HashMap<>(); current.forEach(photo -> byId.put(photo.getId(), photo));
        for (int index = 0; index < photoIds.size(); index++) {
            MemoryPhoto photo = byId.get(photoIds.get(index));
            if (photo == null) throw new BusinessException(400, "Photo order contains an invalid photo");
            photo.setSortOrder(index); memoryPhotoMapper.updateById(photo);
        }
        updatePrimary(memory, loadPhotos(id));
        return getById(id);
    }

    @Override
    @Transactional
    public TravelMemory update(Long id, MemoryUpdateRequest request) {
        TravelMemory existing = getById(id);
        validateCoordinates(request.getLatitude(), request.getLongitude());
        String content = normalizeNullable(request.getContent());
        String locationName = normalizeNullable(request.getLocationName());
        validateMemoryText(content, locationName);
        travelMemoryMapper.update(null, new UpdateWrapper<TravelMemory>()
                .eq("id", id)
                .set("content", content)
                .set("location_name", locationName)
                .set("record_time", request.getRecordTime())
                .set("latitude", request.getLatitude())
                .set("longitude", request.getLongitude()));
        if (request.getCompanionIds() != null) {
            tripCompanionService.replaceMemoryCompanions(id, existing.getTripId(), request.getCompanionIds());
        }
        if (request.getPhotos() != null) {
            replacePhotos(existing, request.getPhotos());
        }
        return getById(id);
    }

    @Override
    @Transactional
    public TravelMemory favorite(Long id, Boolean favorite) {
        TravelMemory memory = getById(id); memory.setIsFavorite(Boolean.TRUE.equals(favorite) ? 1 : 0); travelMemoryMapper.updateById(memory); return getById(id);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        TravelMemory memory = getById(id);
        tripCompanionService.deleteByMemoryId(id);
        memoryPhotoMapper.delete(new LambdaQueryWrapper<MemoryPhoto>().eq(MemoryPhoto::getMemoryId, id));
        travelMemoryMapper.deleteById(id);
        travelTripService.clearCoverIfMatches(memory.getTripId(), memory.getPhotoUrl());
    }

    private List<TravelMemory> attachPhotos(List<TravelMemory> memories) {
        if (memories.isEmpty()) return memories;
        Set<Long> ids = new HashSet<>(); memories.forEach(memory -> ids.add(memory.getId()));
        Map<Long, List<MemoryPhoto>> byMemory = new HashMap<>();
        loadPhotos(ids).forEach(photo -> byMemory.computeIfAbsent(photo.getMemoryId(), ignored -> new ArrayList<>()).add(photo));
        Map<Long, List<com.travelmemory.dto.CompanionSummary>> companionsByMemory = tripCompanionService.findByMemoryIds(ids);
        memories.forEach(memory -> {
            List<MemoryPhoto> photos = byMemory.getOrDefault(memory.getId(), new ArrayList<>());
            if (photos.isEmpty() && hasUrl(memory.getPhotoUrl())) {
                MemoryPhoto legacy = new MemoryPhoto(); legacy.setMemoryId(memory.getId()); legacy.setPhotoUrl(memory.getPhotoUrl()); legacy.setSortOrder(0); photos = List.of(legacy);
            }
            memory.setPhotos(photos); memory.setPhotoCount(photos.size());
            List<com.travelmemory.dto.CompanionSummary> companions = companionsByMemory.getOrDefault(memory.getId(), List.of());
            memory.setCompanions(companions);
            memory.setCompanionIds(companions.stream().map(com.travelmemory.dto.CompanionSummary::getId).toList());
        });
        return memories;
    }

    private List<MemoryPhoto> loadPhotos(Long memoryId) { return loadPhotos(Set.of(memoryId)); }
    private List<MemoryPhoto> loadPhotos(Set<Long> memoryIds) {
        if (memoryIds.isEmpty()) return List.of();
        return memoryPhotoMapper.selectList(new LambdaQueryWrapper<MemoryPhoto>().in(MemoryPhoto::getMemoryId, memoryIds)
                .orderByAsc(MemoryPhoto::getMemoryId).orderByAsc(MemoryPhoto::getSortOrder).orderByAsc(MemoryPhoto::getId));
    }
    private void persistPhotos(Long memoryId, List<MemoryPhoto> photos) {
        for (int i = 0; i < photos.size(); i++) { MemoryPhoto photo = photos.get(i); photo.setId(null); photo.setMemoryId(memoryId); photo.setSortOrder(i); photo.setCreateTime(null); memoryPhotoMapper.insert(photo); }
    }
    private void updatePrimary(TravelMemory memory, List<MemoryPhoto> photos) {
        String previous = memory.getPhotoUrl();
        setPrimary(memory, photos);
        travelMemoryMapper.update(null, new UpdateWrapper<TravelMemory>()
                .eq("id", memory.getId())
                .set("photo_url", memory.getPhotoUrl())
                .set("photo_path", null));
        if (hasUrl(previous) && !sameUrl(previous, memory.getPhotoUrl())) {
            if (hasUrl(memory.getPhotoUrl())) travelTripService.replaceCoverIfMatches(memory.getTripId(), previous, memory.getPhotoUrl());
            else travelTripService.clearCoverIfMatches(memory.getTripId(), previous);
        }
    }
    private void setPrimary(TravelMemory memory, List<MemoryPhoto> photos) {
        if (photos.isEmpty()) { memory.setPhotoUrl(null); memory.setPhotoPath(null); return; }
        memory.setPhotoUrl(photos.get(0).getPhotoUrl()); memory.setPhotoPath(null);
    }
    private void replacePhotos(TravelMemory memory, List<MemoryPhotoReferenceRequest> references) {
        validatePhotoCount(references.size());
        List<MemoryPhoto> current = loadPhotos(memory.getId());
        Map<Long, MemoryPhoto> currentById = new HashMap<>();
        current.forEach(photo -> currentById.put(photo.getId(), photo));
        Set<Long> retainedIds = new HashSet<>();
        Set<String> urls = new HashSet<>();
        List<MemoryPhoto> finalPhotos = new ArrayList<>();

        for (MemoryPhotoReferenceRequest reference : references) {
            if (reference.getId() != null) {
                MemoryPhoto existing = currentById.get(reference.getId());
                if (existing == null) throw new BusinessException(404, "Photo not found");
                if (!retainedIds.add(existing.getId())) throw new BusinessException(400, "Duplicate photo references are not allowed");
                if (!urls.add(existing.getPhotoUrl())) throw new BusinessException(400, "Duplicate photo URLs are not allowed");
                finalPhotos.add(existing);
                continue;
            }
            String photoUrl = normalizeAndVerifyUploadUrl(reference.getPhotoUrl());
            if (!urls.add(photoUrl)) throw new BusinessException(400, "Duplicate photo URLs are not allowed");
            MemoryPhoto photo = new MemoryPhoto();
            photo.setMemoryId(memory.getId());
            photo.setPhotoUrl(photoUrl);
            finalPhotos.add(photo);
        }

        for (MemoryPhoto photo : current) {
            if (!retainedIds.contains(photo.getId())) memoryPhotoMapper.deleteById(photo.getId());
        }
        for (int index = 0; index < finalPhotos.size(); index++) {
            MemoryPhoto photo = finalPhotos.get(index);
            photo.setSortOrder(index);
            if (photo.getId() == null) {
                photo.setCreateTime(null);
                memoryPhotoMapper.insert(photo);
            } else {
                memoryPhotoMapper.updateById(photo);
            }
        }
        updatePrimary(memory, finalPhotos);
    }
    private void normalizeMemoryFields(TravelMemory memory) {
        memory.setContent(normalizeNullable(memory.getContent()));
        memory.setLocationName(normalizeNullable(memory.getLocationName()));
    }
    private String normalizeNullable(String value) {
        if (value == null) return null;
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
    private void validateCoordinates(BigDecimal latitude, BigDecimal longitude) {
        if ((latitude == null) != (longitude == null)) throw new BusinessException(400, "Latitude and longitude must be provided together");
        if (latitude != null && (latitude.compareTo(BigDecimal.valueOf(-90)) < 0 || latitude.compareTo(BigDecimal.valueOf(90)) > 0)) {
            throw new BusinessException(400, "Latitude must be between -90 and 90");
        }
        if (longitude != null && (longitude.compareTo(BigDecimal.valueOf(-180)) < 0 || longitude.compareTo(BigDecimal.valueOf(180)) > 0)) {
            throw new BusinessException(400, "Longitude must be between -180 and 180");
        }
    }
    private void validateMemoryText(String content, String locationName) {
        if (content != null && content.length() > 300) throw new BusinessException(400, "Content must not exceed 300 characters");
        if (locationName != null && locationName.length() > 255) throw new BusinessException(400, "Location name must not exceed 255 characters");
    }
    private void applyPhotoMetadata(TravelMemory memory, UploadResult result) {
        if (memory.getRecordTime() == null && result.getPhotoTakenTime() != null) memory.setRecordTime(result.getPhotoTakenTime());
        if (memory.getLatitude() == null && result.getLatitude() != null) memory.setLatitude(result.getLatitude());
        if (memory.getLongitude() == null && result.getLongitude() != null) memory.setLongitude(result.getLongitude());
    }
    private MemoryPhoto copyPhoto(MemoryPhoto source) { MemoryPhoto photo = new MemoryPhoto(); photo.setPhotoUrl(source.getPhotoUrl()); return photo; }
    private void validatePhotoUrls(List<MemoryPhoto> photos) {
        Set<String> urls = new HashSet<>();
        for (MemoryPhoto photo : photos) {
            String normalizedUrl = normalizeAndVerifyUploadUrl(photo.getPhotoUrl());
            if (!urls.add(normalizedUrl)) throw new BusinessException(400, "Duplicate photo URLs are not allowed");
            photo.setPhotoUrl(normalizedUrl);
        }
    }
    private String normalizeAndVerifyUploadUrl(String value) {
        if (!hasUrl(value)) throw new BusinessException(400, "Photo URL is required");
        String raw = value.trim();
        try { if (URI.create(raw).isAbsolute()) throw new BusinessException(400, "Photo URL must use /uploads/"); }
        catch (IllegalArgumentException exception) { throw new BusinessException(400, "Invalid photo URL"); }
        String decoded;
        try { decoded = URLDecoder.decode(raw, StandardCharsets.UTF_8); }
        catch (IllegalArgumentException exception) { throw new BusinessException(400, "Invalid photo URL"); }
        decoded = decoded.replace('\\', '/');
        if (!decoded.startsWith("/uploads/") || decoded.contains("?") || decoded.contains("#")) throw new BusinessException(400, "Photo URL must use /uploads/");
        String relative = decoded.substring("/uploads/".length());
        if (relative.isBlank()) throw new BusinessException(400, "Invalid photo URL");
        String userPrefix = "users/" + currentUser.requireId() + "/";
        if (!relative.startsWith(userPrefix) && !(currentUser.requireId().equals(1L) && !relative.startsWith("users/"))) {
            throw new BusinessException(404, "Photo URL not found");
        }
        Path root = Paths.get(uploadDir).toAbsolutePath().normalize();
        Path candidate;
        try { candidate = root.resolve(relative).normalize(); }
        catch (RuntimeException exception) { throw new BusinessException(400, "Invalid photo URL"); }
        if (!UploadPathGuard.isSafeRegularFile(root, candidate)) {
            throw new BusinessException(400, "Photo URL does not reference an uploaded file");
        }
        return "/uploads/" + root.relativize(candidate).toString().replace('\\', '/');
    }
    private void validatePhotoCount(int count) { if (count > MAX_PHOTOS) throw new BusinessException(400, "A memory can contain at most " + MAX_PHOTOS + " photos"); }
    private boolean hasUrl(String value) { return value != null && !value.trim().isEmpty(); }
    private boolean sameUrl(String first, String second) { return hasUrl(first) && hasUrl(second) && first.trim().equals(second.trim()); }
}
