package com.mk3.chatapp.models.ncmec;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.adapters.XmlJavaTypeAdapter;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@XmlAccessorType(XmlAccessType.FIELD)
public class IncidentSummary {

    @XmlElement(required = true)
    private String incidentType;

    @XmlElement
    private String platform;

    @XmlElement
    private ReportAnnotations reportAnnotations;

    @XmlElement(required = true)
    @XmlJavaTypeAdapter(OffsetDateTimeAdapter.class)
    private OffsetDateTime incidentDateTime;

    @XmlElement
    private String incidentDateTimeDescription;

    @XmlElement
    private Boolean escalateToHighPriority;
}
