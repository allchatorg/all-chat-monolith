package com.mk3.chatapp.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
public class UploadLimitExceededException extends RuntimeException {
    public UploadLimitExceededException(String message) {
        super(message);
    }
}

