package com.mk3.chatapp.dtos;

import com.mk3.chatapp.exceptions.AccountLimitExceededException;

import java.time.LocalDateTime;

/** Preserves the usual error fields and adds machine-readable allowance details. */
public record AccountLimitErrorDTO(int status, String error, String message, LocalDateTime timestamp,
                                   Limit limit) {
    public record Limit(AccountLimitExceededException.Code code, long maximum, long used,
                        long requested, Long proMaximum) {}
}
