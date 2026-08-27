package com.mk3.chatapp.services.impl;

import com.mk3.chatapp.dtos.AttachmentTypeDTO;
import com.mk3.chatapp.dtos.TagDTO;
import com.mk3.chatapp.dtos.requests.UpdateTimeFormatSettingsRequest;
import com.mk3.chatapp.dtos.requests.UpdateTimeZoneRequest;
import com.mk3.chatapp.mappers.AttachmentTypeMapper;
import com.mk3.chatapp.mappers.TagMapper;
import com.mk3.chatapp.services.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.List;

@RequiredArgsConstructor
@Service
public class SettingsServiceImpl implements SettingsService {
    private final TagService tagService;
    private final AttachmentTypeService attachmentTypeService;

    private final TagMapper tagMapper;
    private final AttachmentTypeMapper attachmentTypeMapper;
    private final SecurityService securityService;
    private final UserService userService;

    @Override
    public List<TagDTO> findAllTags() {
        return tagService.findAll()
                .stream()
                .map(tagMapper::toDto)
                .toList();
    }

    @Override
    public List<AttachmentTypeDTO> findAllAttachmentTypes() {
        return attachmentTypeService.findAll()
                .stream()
                .map(attachmentTypeMapper::toDto)
                .toList();
    }

    @Override
    public void updateTimeFormat(UpdateTimeFormatSettingsRequest updateTimeFormatSettingsRequest) {
        var user = securityService.getCurrentUser();
        user.setTimeFormatSetting(updateTimeFormatSettingsRequest.timeFormat());
        userService.save(user);
    }

    @Override
    public void updateTimeZone(UpdateTimeZoneRequest updateTimeZoneRequest) {
        String zone;
        try {
            zone = ZoneId.of(updateTimeZoneRequest.timeZone().trim()).getId();
        } catch (DateTimeException e) {
            throw new IllegalArgumentException("Unknown time zone: " + updateTimeZoneRequest.timeZone());
        }
        var user = securityService.getCurrentUser();
        if (zone.equals(user.getTimeZone())) {
            return;
        }
        user.setTimeZone(zone);
        userService.save(user);
    }
}
