package com.travelmemory.service;

import com.travelmemory.common.StoredFile;
import org.springframework.web.multipart.MultipartFile;

public interface FileStorageService {

    StoredFile store(MultipartFile file, Long userId);
}
