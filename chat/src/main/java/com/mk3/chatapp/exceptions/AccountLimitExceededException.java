package com.mk3.chatapp.exceptions;

import lombok.Getter;

/** Account allowances are distinct from request-rate throttling. */
@Getter
public class AccountLimitExceededException extends IllegalArgumentException {
    public enum Code {
        PUBLIC_ROOMS, MESSAGE_CHARACTERS, MESSAGE_FORMATTING, ATTACHMENT_BYTES, HOURLY_UPLOAD_BYTES
    }

    private final Code code;
    private final long maximum;
    private final long used;
    private final long requested;
    private final Long vipMaximum;

    public AccountLimitExceededException(Code code, String message, long maximum, long used,
                                         long requested, Long vipMaximum) {
        super(message);
        this.code = code;
        this.maximum = maximum;
        this.used = used;
        this.requested = requested;
        this.vipMaximum = vipMaximum;
    }
}
