package com.mk3.chatapp.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum NcmecReportAnnotation {
    SEXTORTION("sextortion"),
    CSAM_SOLICITATION("csamSolicitation"),
    MINOR_TO_MINOR("minorToMinorInteraction"),
    SPAM("spam"),
    SADISTIC_EXPLOITATION("sadisticOnlineExploitation");

    private final String value;
}
