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
public class ChatImIncident {

    @XmlElement
    private String chatClient;

    @XmlElement
    private String chatRoomName;

    @XmlElement
    private String content;

    @XmlElement
    private String additionalInfo;
}
