package com.mk3.chatapp.messaging.csam;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.Set;

public record CsamAnalysisResultMessage(
        Long attachmentId,
        Long messageId,
        @JsonProperty("csamDetected")
        @JsonAlias({"csam", "detected", "flagged", "match"})
        Boolean csamDetected,
        String verdict,
        Double confidence,
        String details,
        @JsonAlias({"analysisId", "reportId", "workerReportId"})
        String workerReference,
        Instant analyzedAt) {

    private static final Set<String> POSITIVE_VERDICTS = Set.of(
            "CSAM",
            "MATCH",
            "DETECTED",
            "FLAGGED",
            "POSITIVE");

    public boolean reportsCsam() {
        if (Boolean.TRUE.equals(csamDetected)) {
            return true;
        }

        return verdict != null && POSITIVE_VERDICTS.contains(verdict.trim().toUpperCase());
    }
}
