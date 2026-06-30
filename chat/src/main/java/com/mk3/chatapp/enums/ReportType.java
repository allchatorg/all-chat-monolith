package com.mk3.chatapp.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

@Getter
@RequiredArgsConstructor
public enum ReportType {
    SPAMMING_ADVERTISING_BEGGING("SPAMMING_ADVERTISING_BEGGING", 12),
    POSTING_NSFW_CONTENT_AS_SFW("POSTING_NSFW_CONTENT_AS_SFW", 8),
    IMPERSONATING_A_MODERATOR("IMPERSONATING_A_MODERATOR", 12),
    PIRATED_CONTENT_TORRENTS("PIRATED_CONTENT_TORRENTS", 11),
    CRIMINAL_INSTRUCTIONS("CRIMINAL_INSTRUCTIONS", 4),
    DOXXING_HARASSMENT_STALKING("DOXXING_HARASSMENT_STALKING", 7),
    NON_CONSENSUAL_SEXUAL_IMAGES("NON_CONSENSUAL_SEXUAL_IMAGES", 6),
    INCITEMENT_TO_LAWLESS_ACTION("INCITEMENT_TO_LAWLESS_ACTION", 5),
    TRUE_THREAT("TRUE_THREAT", 3),
    ATTEMPTING_TO_TRIGGER_EPILEPSY("ATTEMPTING_TO_TRIGGER_EPILEPSY", 3),
    POSTING_IN_A_ROOM_DEDICATED_TO_CRIMINAL_ACTIVITY("POSTING_IN_A_ROOM_DEDICATED_TO_CRIMINAL_ACTIVITY", 3),
    UNDERAGE("UNDERAGE", 4),

    ILLEGAL_CONTENT("ILLEGAL_CONTENT", 10),

    REAL_CHILD_SEXUAL_ABUSE_MATERIAL("REAL_CHILD_SEXUAL_ABUSE_MATERIAL", 1),
    FICTIONAL_CHILD_SEXUAL_ABUSE_MATERIAL("FICTIONAL_CHILD_SEXUAL_ABUSE_MATERIAL", 2),
    OTHER_REAL_OBSCENE_PORNOGRAPHY("OTHER_REAL_OBSCENE_PORNOGRAPHY", 8),
    OTHER_FICTIONAL_OBSCENE_PORNOGRAPHY("OTHER_FICTIONAL_OBSCENE_PORNOGRAPHY", 9),
    SNUFF_CRUSH_MATERIAL("SNUFF_CRUSH_MATERIAL", 5),

    INAPPROPRIATE_COMMUNICATION_WITH_MINOR("INAPPROPRIATE_COMMUNICATION_WITH_MINOR", 1),
    KNOWINGLY_SOLICITING_IDENTIFYING_INFO_OR_SEXUAL_CONTENT_FROM_MINOR("KNOWINGLY_SOLICITING_IDENTIFYING_INFO_OR_SEXUAL_CONTENT_FROM_MINOR", 1),
    KNOWINGLY_SENDING_SEXUAL_MESSAGES_OR_CONTENT_TO_MINOR("KNOWINGLY_SENDING_SEXUAL_MESSAGES_OR_CONTENT_TO_MINOR", 1),

    POSTING_ONION_LINK("POSTING_ONION_LINK", 13);

    private static final Map<ReportType, ReportType> PARENT_MAP = Map.of(
            REAL_CHILD_SEXUAL_ABUSE_MATERIAL, ILLEGAL_CONTENT,
            FICTIONAL_CHILD_SEXUAL_ABUSE_MATERIAL, ILLEGAL_CONTENT,
            OTHER_REAL_OBSCENE_PORNOGRAPHY, ILLEGAL_CONTENT,
            OTHER_FICTIONAL_OBSCENE_PORNOGRAPHY, ILLEGAL_CONTENT,
            SNUFF_CRUSH_MATERIAL, ILLEGAL_CONTENT,
            KNOWINGLY_SOLICITING_IDENTIFYING_INFO_OR_SEXUAL_CONTENT_FROM_MINOR, INAPPROPRIATE_COMMUNICATION_WITH_MINOR,
            KNOWINGLY_SENDING_SEXUAL_MESSAGES_OR_CONTENT_TO_MINOR, INAPPROPRIATE_COMMUNICATION_WITH_MINOR);
    private static final Set<ReportType> CSAM_RELATED_TYPES = EnumSet.of(
            REAL_CHILD_SEXUAL_ABUSE_MATERIAL,
            FICTIONAL_CHILD_SEXUAL_ABUSE_MATERIAL,
            INAPPROPRIATE_COMMUNICATION_WITH_MINOR,
            KNOWINGLY_SOLICITING_IDENTIFYING_INFO_OR_SEXUAL_CONTENT_FROM_MINOR,
            KNOWINGLY_SENDING_SEXUAL_MESSAGES_OR_CONTENT_TO_MINOR);
    private static final String CSAM_PUBLIC_DESCRIPTION =
            "Your account was restricted for violating platform safety policies.";

    private final String code;
    private final int priority;

    public static ReportType fromCode(String code) {
        for (ReportType type : values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown ReportType code: " + code);
    }

    public ReportType getParent() {
        return PARENT_MAP.get(this);
    }

    public boolean isSubcategory() {
        return PARENT_MAP.containsKey(this);
    }

    public boolean isTopLevel() {
        return !isSubcategory();
    }

    public ReportType getRootCategory() {
        ReportType parent = getParent();
        return parent != null ? parent : this;
    }

    public boolean isCsamRelated() {
        return CSAM_RELATED_TYPES.contains(this);
    }

    public ReportType toUserFacingReportType() {
        return isCsamRelated() ? ILLEGAL_CONTENT : this;
    }

    public String toUserFacingDescription(String description) {
        return isCsamRelated() ? CSAM_PUBLIC_DESCRIPTION : description;
    }

    public boolean hasHigherPriorityThan(ReportType other) {
        return this.priority < other.priority;
    }
}
