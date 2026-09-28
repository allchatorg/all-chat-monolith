package com.mk3.chatapp.utils;

import com.mk3.chatapp.models.AttachmentType;
import com.mk3.chatapp.models.identity.User;

/** Effective account limits; paid access is independent of the public badge preference. */
public final class AccountLimits {
    public static final int REGULAR_MESSAGE_LENGTH = 500;
    public static final int PRO_MESSAGE_LENGTH = 2500;
    public static final int MAX_RAW_MESSAGE_LENGTH = 4 * PRO_MESSAGE_LENGTH;
    public static final long PRO_FILE_BYTES = 100L * 1024 * 1024;
    public static final long REGULAR_HOURLY_UPLOAD_BYTES = 25L * 1024 * 1024;
    public static final long PRO_HOURLY_UPLOAD_BYTES = 500L * 1024 * 1024;

    private AccountLimits() {
    }

    public static int messageLength(User user) {
        return user.isProActive() ? PRO_MESSAGE_LENGTH : REGULAR_MESSAGE_LENGTH;
    }

    /** Staff retain unlimited public-room memberships. */
    public static int joinedPublicRooms(User user) {
        if (user.getRole().isStaffMember()) {
            return Integer.MAX_VALUE;
        }
        if (user.isProActive()) {
            return 100;
        }
        if (user.isVerified()) {
            return 50;
        }
        return user.isClaimed() ? 25 : 20;
    }

    /** Staff retain the existing exemption from the rolling upload quota. */
    public static long hourlyUploadBytes(User user) {
        if (user.getRole().isStaffMember()) {
            return Long.MAX_VALUE;
        }
        return user.isProActive() ? PRO_HOURLY_UPLOAD_BYTES : REGULAR_HOURLY_UPLOAD_BYTES;
    }

    public static long attachmentBytes(User user, AttachmentType attachmentType) {
        if (user.isProActive()) {
            return PRO_FILE_BYTES;
        }
        return attachmentType.getMaxFileSizeBytes();
    }
}
