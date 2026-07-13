package com.travelmemory.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.travelmemory.security.CurrentUser;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

class PrivateUploadControllerTest {

    @TempDir
    Path uploads;

    @Test
    void userCannotReadAnotherUsersOriginalPhotoUrl() throws Exception {
        Path foreignPhoto = uploads.resolve("users/2/2026/07/private.jpg");
        Files.createDirectories(foreignPhoto.getParent());
        Files.writeString(foreignPhoto, "private image");
        CurrentUser user = mock(CurrentUser.class);
        when(user.requireId()).thenReturn(1L);
        PrivateUploadController controller = new PrivateUploadController(user);
        ReflectionTestUtils.setField(controller, "uploadDir", uploads.toString());

        assertEquals(HttpStatus.NOT_FOUND, controller.get("users/2/2026/07/private.jpg").getStatusCode());
    }

    @Test
    void ownerCanReadOwnPhoto() throws Exception {
        Path ownPhoto = uploads.resolve("users/2/2026/07/private.jpg");
        Files.createDirectories(ownPhoto.getParent());
        Files.writeString(ownPhoto, "private image");
        CurrentUser user = mock(CurrentUser.class);
        when(user.requireId()).thenReturn(2L);
        PrivateUploadController controller = new PrivateUploadController(user);
        ReflectionTestUtils.setField(controller, "uploadDir", uploads.toString());

        assertEquals(HttpStatus.OK, controller.get("users/2/2026/07/private.jpg").getStatusCode());
    }
}
