package com.mk3.chatapp.controllers;

import com.mk3.chatapp.dtos.AttachmentTypeDTO;
import com.mk3.chatapp.dtos.TagDTO;
import com.mk3.chatapp.dtos.requests.UpdateTimeFormatSettingsRequest;
import com.mk3.chatapp.dtos.requests.UpdateTimeZoneRequest;
import com.mk3.chatapp.enums.TimeFormat;
import com.mk3.chatapp.services.SettingsService;
import com.mk3.chatapp.services.VipBadgeService;
import com.mk3.chatapp.services.VipFontService;
import com.mk3.chatapp.dtos.requests.UpdateFontSettingsRequest;
import com.mk3.chatapp.dtos.responses.FontSettingsDTO;
import com.mk3.chatapp.services.SecurityService;
import com.mk3.chatapp.dtos.requests.UpdateAppearanceRequest;
import com.mk3.chatapp.dtos.responses.AppearanceDTO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/settings")
@CrossOrigin(
        origins = "${app.FRONT_END.URL}",
        allowCredentials = "true"
)
public class SettingsController {
    private final SettingsService settingsService;
    private final VipBadgeService vipBadgeService;
    private final SecurityService securityService;
    private final VipFontService vipFontService;

    @GetMapping("/fonts")
    public FontSettingsDTO getFonts() {
        return vipFontService.getSettings(currentUserId());
    }

    @PatchMapping("/fonts")
    public FontSettingsDTO updateFonts(@Valid @RequestBody UpdateFontSettingsRequest request) {
        return vipFontService.updateSettings(currentUserId(), request);
    }

    private Long currentUserId() {
        var user = securityService.getCurrentUser();
        if (user == null) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.UNAUTHORIZED);
        }
        return user.getId();
    }

    @PatchMapping("/appearance")
    public AppearanceDTO updateAppearance(@Valid @RequestBody UpdateAppearanceRequest request) {
        var user = securityService.getCurrentUser();
        if (user == null) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.UNAUTHORIZED);
        }
        return vipBadgeService.updatePreference(user.getId(), request.showVipBadge());
    }

    @GetMapping("/tags")
    public ResponseEntity<List<TagDTO>> getAllTags() {
        List<TagDTO> tags = settingsService.findAllTags();
        return ResponseEntity.ok(tags);
    }

    @GetMapping("/attachment-types")
    public ResponseEntity<List<AttachmentTypeDTO>> getAllAttachmentTypes() {
        List<AttachmentTypeDTO> types = settingsService.findAllAttachmentTypes();
        return ResponseEntity.ok(types);
    }

    @PatchMapping("/time-zone")
    public ResponseEntity<Void> updateTimeZone(@Valid @RequestBody UpdateTimeZoneRequest updateTimeZoneRequest) {
        settingsService.updateTimeZone(updateTimeZoneRequest);
        return ResponseEntity.ok().build();
    }

    @PatchMapping("/time-format")
    public ResponseEntity<TimeFormat> updateTimeFormat(@RequestBody UpdateTimeFormatSettingsRequest updateTimeFormatSettingsRequest) {
        settingsService.updateTimeFormat(updateTimeFormatSettingsRequest);
        return ResponseEntity.ok().build();
    }

}
