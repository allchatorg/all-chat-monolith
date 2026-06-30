package com.mk3.chatapp.dtos.requests;

import lombok.NonNull;

public record AddPhoneNumberRequest(
        @NonNull
        String phoneNumber
) {
}
