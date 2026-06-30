package com.mk3.chatapp.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

@Getter
@AllArgsConstructor
public enum MimeType {
    // Audio
    MP3("audio/mpeg"),
    OGG("audio/ogg"),

    // Video
    MP4("video/mp4"),
    AVI("video/avi"),
    MOV("video/quicktime"),
    WEBM("video/webm"),
    MPEG("video/mpeg"),

    // Image
    PNG("image/png"),
    JPEG("image/jpeg"),
    JPG("image/jpg"),
    GIF("image/gif"),
    BMP("image/bmp"),
    SVG("image/svg+xml"),
    WEBP("image/webp"),

    // Flash
    SWF("application/x-shockwave-flash"),

    // Fallback
    UNKNOWN("unknown");

    private final String mime;

    public static MimeType fromMime(String mime) {
        return Arrays.stream(values())
                .filter(m -> m.mime.equalsIgnoreCase(mime))
                .findFirst()
                .orElse(UNKNOWN);
    }

    public AttachmentTypeEnum getAttachmentTypeEnum() {
        return switch (this) {
            case MP3, OGG -> AttachmentTypeEnum.AUDIO;

            case MP4, AVI, MOV, WEBM, MPEG, GIF -> AttachmentTypeEnum.VIDEO;

            case PNG, JPEG, JPG, BMP, SVG, WEBP -> AttachmentTypeEnum.IMAGE;

            case SWF -> AttachmentTypeEnum.FLASH;

            default -> AttachmentTypeEnum.UNKNOWN;
        };
    }
}
