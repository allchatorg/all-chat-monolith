package com.mk3.chatapp.services.impl;

import com.mk3.chatapp.dtos.AttachmentTypeDTO;
import com.mk3.chatapp.dtos.TagDTO;
import com.mk3.chatapp.dtos.requests.UpdateTimeFormatSettingsRequest;
import com.mk3.chatapp.mappers.AttachmentTypeMapper;
import com.mk3.chatapp.mappers.TagMapper;
import com.mk3.chatapp.services.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

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
}
