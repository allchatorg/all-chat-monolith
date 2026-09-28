package com.mk3.chatapp.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.mk3.chatapp.dtos.requests.UpdateFontSettingsRequest;
import com.mk3.chatapp.dtos.responses.FontSettingsDTO;
import com.mk3.chatapp.enums.FontPreset;
import com.mk3.chatapp.exceptions.GlobalExceptionHandler;
import com.mk3.chatapp.models.identity.User;
import com.mk3.chatapp.services.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class FontSettingsControllerTest {
    private final SecurityService security = mock(SecurityService.class);
    private final ProFontService fonts = mock(ProFontService.class);
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        var mapper = new ObjectMapper().findAndRegisterModules().disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mvc = MockMvcBuilders.standaloneSetup(new SettingsController(mock(SettingsService.class),
                        mock(ProBadgeService.class), security, fonts))
                .addPlaceholderValue("app.FRONT_END.URL", "http://localhost:3000")
                .setControllerAdvice(new GlobalExceptionHandler(mapper))
                .setMessageConverters(new MappingJackson2HttpMessageConverter(mapper)).build();
        when(security.getCurrentUser()).thenReturn(User.builder().id(7L).username("tester").build());
    }

    @Test
    void getReturnsOwnerSettingsWithIsoResetTime() throws Exception {
        when(fonts.getSettings(7L)).thenReturn(result());
        mvc.perform(get("/api/v1/settings/fonts")).andExpect(status().isOk())
                .andExpect(jsonPath("$.usernameFont").value("INTER"))
                .andExpect(jsonPath("$.messageFont").value("OPEN_SANS"))
                .andExpect(jsonPath("$.fontRevision").value(3))
                .andExpect(jsonPath("$.dailyLimit").value(5))
                .andExpect(jsonPath("$.changesRemaining").value(2))
                .andExpect(jsonPath("$.resetsAt").value("2026-09-22T00:00:00Z"));
    }

    @Test
    void validPairReachesService() throws Exception {
        when(fonts.updateSettings(eq(7L), any())).thenReturn(result());
        mvc.perform(patch("/api/v1/settings/fonts").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usernameFont\":\"INTER\",\"messageFont\":\"OPEN_SANS\"}"))
                .andExpect(status().isOk());
        verify(fonts).updateSettings(7L, new UpdateFontSettingsRequest(FontPreset.INTER, FontPreset.OPEN_SANS));
    }

    @Test
    void invalidMissingNumericAndMalformedRequestsReturn400() throws Exception {
        for (String body : new String[]{
                "{\"usernameFont\":\"COMIC_SANS\",\"messageFont\":\"DEFAULT\"}",
                "{\"usernameFont\":\"INTER\"}",
                "{\"usernameFont\":1,\"messageFont\":\"DEFAULT\"}",
                "{\"usernameFont\":null,\"messageFont\":\"DEFAULT\"}", "{"}) {
            mvc.perform(patch("/api/v1/settings/fonts").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(fonts);
    }

    @Test
    void unauthenticatedOwnerEndpointsReturn401() throws Exception {
        when(security.getCurrentUser()).thenReturn(null);
        mvc.perform(get("/api/v1/settings/fonts")).andExpect(status().isUnauthorized());
        mvc.perform(patch("/api/v1/settings/fonts").contentType(MediaType.APPLICATION_JSON)
                .content("{\"usernameFont\":\"DEFAULT\",\"messageFont\":\"DEFAULT\"}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(fonts);
    }

    @Test
    void entitlementAndQuotaFailuresPreserveHttpStatus() throws Exception {
        for (HttpStatus status : new HttpStatus[]{HttpStatus.FORBIDDEN, HttpStatus.TOO_MANY_REQUESTS}) {
            when(fonts.updateSettings(eq(7L), any())).thenThrow(new ResponseStatusException(status, "Unavailable"));
            mvc.perform(patch("/api/v1/settings/fonts").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"usernameFont\":\"INTER\",\"messageFont\":\"DEFAULT\"}"))
                    .andExpect(status().is(status.value()));
        }
    }

    private static FontSettingsDTO result() {
        return new FontSettingsDTO(FontPreset.INTER, FontPreset.OPEN_SANS, 3, true, 5, 2,
                Instant.parse("2026-09-22T00:00:00Z"));
    }
}
