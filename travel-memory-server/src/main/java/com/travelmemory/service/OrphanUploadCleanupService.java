package com.travelmemory.service;

import com.travelmemory.dto.CleanupResult;
import java.util.Collection;

public interface OrphanUploadCleanupService {

    CleanupResult cleanupOrphans();

    void deleteUnreferencedUploads(Collection<String> photoUrls);
}
