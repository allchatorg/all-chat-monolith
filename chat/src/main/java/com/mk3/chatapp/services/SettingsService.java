package com.mk3.chatapp.services;

import com.mk3.chatapp.dtos.AttachmentTypeDTO;
import com.mk3.chatapp.dtos.TagDTO;
import com.mk3.chatapp.dtos.requests.UpdateTimeFormatSettingsRequest;
import com.mk3.chatapp.dtos.requests.UpdateTimeZoneRequest;

import java.util.List;

public interface SettingsService {
    List<TagDTO> findAllTags();

    List<AttachmentTypeDTO> findAllAttachmentTypes();

    void updateTimeFormat(UpdateTimeFormatSettingsRequest updateTimeFormatSettingsRequest);

    void updateTimeZone(UpdateTimeZoneRequest updateTimeZoneRequest);
}
