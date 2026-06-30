package com.mk3.chatapp.models.ncmec;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@XmlRootElement(name = "reportResponse")
@XmlAccessorType(XmlAccessType.FIELD)
public class NcmecReportResponse {

    @XmlElement
    private int responseCode;

    @XmlElement
    private String responseDescription;

    @XmlElement
    private Long reportId;

    @XmlElement
    private String fileId;

    @XmlElement
    private String hash;
}
