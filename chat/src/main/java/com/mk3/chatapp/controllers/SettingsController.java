package com.mk3.chatapp.controllers;

import com.mk3.chatapp.dtos.AttachmentTypeDTO;
import com.mk3.chatapp.dtos.TagDTO;
import com.mk3.chatapp.dtos.requests.UpdateTimeFormatSettingsRequest;
import com.mk3.chatapp.dtos.requests.UpdateTimeZoneRequest;
import com.mk3.chatapp.enums.TimeFormat;
import com.mk3.chatapp.services.SettingsService;
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
