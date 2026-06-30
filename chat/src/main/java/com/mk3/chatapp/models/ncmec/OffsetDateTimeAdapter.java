package com.mk3.chatapp.models.ncmec;

import jakarta.xml.bind.annotation.adapters.XmlAdapter;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

public class OffsetDateTimeAdapter extends XmlAdapter<String, OffsetDateTime> {

    @Override
    public OffsetDateTime unmarshal(String v) {
        return v != null ? OffsetDateTime.parse(v, DateTimeFormatter.ISO_OFFSET_DATE_TIME) : null;
    }

    @Override
    public String marshal(OffsetDateTime v) {
        return v != null ? v.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME) : null;
    }
}
