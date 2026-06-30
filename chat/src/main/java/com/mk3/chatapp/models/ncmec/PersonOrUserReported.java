package com.mk3.chatapp.models.ncmec;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@XmlAccessorType(XmlAccessType.FIELD)
public class PersonOrUserReported {

    @XmlElement(name = "personOrUserReportedPerson")
    private PersonOrUserReportedPerson personOrUserReportedPerson;

    @XmlElement
    private String espIdentifier;

    @XmlElement
    private String espService;

    @XmlElement
    private String screenName;

    @XmlElement
    private String displayName;

    @XmlElement
    private String profileUrl;

    @XmlElement
    private String profileBio;

    @XmlElement
    private Boolean compromisedAccount;

    @XmlElement
    private String additionalInfo;
}
