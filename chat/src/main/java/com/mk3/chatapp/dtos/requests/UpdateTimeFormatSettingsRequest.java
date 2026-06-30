package com.mk3.chatapp.dtos.requests;

import com.mk3.chatapp.enums.TimeFormat;

public record UpdateTimeFormatSettingsRequest(
        TimeFormat timeFormat
) {
}
