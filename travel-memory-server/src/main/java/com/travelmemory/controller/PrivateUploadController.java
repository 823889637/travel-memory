package com.travelmemory.controller;

import com.travelmemory.security.CurrentUser;
import com.travelmemory.security.UploadPathGuard;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PrivateUploadController {
    private final CurrentUser currentUser;
    @Value("${app.upload.dir:../uploads}") private String uploadDir;
    public PrivateUploadController(CurrentUser currentUser) { this.currentUser = currentUser; }

    @GetMapping("/uploads/{*path}")
    public ResponseEntity<Resource> get(@PathVariable String path) throws Exception {
        Long userId = currentUser.requireId();
        String relative = path == null ? "" : path.replace('\\', '/').replaceFirst("^/", "");
        if (!canRead(userId, relative)) return ResponseEntity.notFound().build();
        Path root = Paths.get(uploadDir).toAbsolutePath().normalize();
        Path file = root.resolve(relative).normalize();
        if (!UploadPathGuard.isSafeRegularFile(root, file)) return ResponseEntity.notFound().build();
        String type = Files.probeContentType(file);
        MediaType mediaType = type == null ? MediaType.APPLICATION_OCTET_STREAM : MediaType.parseMediaType(type);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore().cachePrivate()).contentType(mediaType).body(new FileSystemResource(file));
    }
    private boolean canRead(Long userId, String relative) {
        String prefix = "users/" + userId + "/";
        return relative.startsWith(prefix) || (userId.equals(1L) && !relative.startsWith("users/"));
    }
}
