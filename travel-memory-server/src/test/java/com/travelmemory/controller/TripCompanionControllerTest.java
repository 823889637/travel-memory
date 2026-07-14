package com.travelmemory.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.travelmemory.exception.BusinessException;
import com.travelmemory.exception.GlobalExceptionHandler;
import com.travelmemory.service.TripCompanionService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class TripCompanionControllerTest {

    @Test
    void ownershipFailureUsesRealHttp404AndKeepsResultBody() throws Exception {
        TripCompanionService service = mock(TripCompanionService.class);
        when(service.list(20L)).thenThrow(new BusinessException(404, "Trip not found"));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new TripCompanionController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        mvc.perform(get("/api/trips/20/companions"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.data").doesNotExist());
    }
}
