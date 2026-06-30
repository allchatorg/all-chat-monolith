package com.mk3.chatapp.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;


@Getter
@RequiredArgsConstructor
public enum NcmecFileRelevance {
    /**
     * Content that is the motivation for the report (the illegal image/video
     * itself)
     */
    REPORTED("Reported"),

    /**
     * Contextual evidence (chat logs, server logs, screenshots of text)
     */
    SUPPLEMENTAL("Supplemental Reported");

    private final String value;
}
