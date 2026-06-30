package com.mk3.chatapp.models.ncmec;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@XmlRootElement(name = "report")
@XmlAccessorType(XmlAccessType.FIELD)
public class NcmecReport {

    @XmlElement(required = true)
    private IncidentSummary incidentSummary;

    @XmlElement
    private List<InternetDetails> internetDetails;

    @XmlElement(required = true)
    private Reporter reporter;

    @XmlElement
    private PersonOrUserReported personOrUserReported;
}
