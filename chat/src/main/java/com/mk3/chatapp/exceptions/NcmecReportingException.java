package com.mk3.chatapp.exceptions;

public class NcmecReportingException extends RuntimeException {

    public NcmecReportingException(String message) {
        super(message);
    }

    public NcmecReportingException(String message, Throwable cause) {
        super(message, cause);
    }
}
