package com.mk3.chatapp.utils;

import org.mapstruct.Named;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

public class DateTimeMapperUtil {

    @Named("instantToString")
    public static String instantToString(Instant instant) {
        if (instant == null) return null;
        return DateTimeFormatter.ISO_INSTANT
                .withZone(ZoneOffset.UTC)
                .format(instant);
    }
}