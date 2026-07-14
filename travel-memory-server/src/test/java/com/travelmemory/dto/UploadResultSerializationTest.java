package com.travelmemory.dto;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class UploadResultSerializationTest {

    @Test
    void doesNotExposeInternalPhotoPath() throws Exception {
        UploadResult result = new UploadResult();
        result.setPhotoUrl("/uploads/users/1/2026/07/photo.jpg");
        result.setPhotoPath("/app/uploads/users/1/2026/07/photo.jpg");

        String json = new ObjectMapper().writeValueAsString(result);

        assertTrue(json.contains("photoUrl"));
        assertFalse(json.contains("photoPath"));
        assertFalse(json.contains("/app/uploads"));
    }
}
