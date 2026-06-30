package com.mk3.chatapp.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum NcmecIncidentType {
    CHILD_PORNOGRAPHY("Child Pornography (possession, manufacture, and distribution)"),
    CHILD_SEX_TRAFFICKING("Child Sex Trafficking"),
    CHILD_SEX_TOURISM("Child Sex Tourism"),
    CHILD_SEXUAL_MOLESTATION("Child Sexual Molestation"),
    MISLEADING_DOMAIN("Misleading Domain Name"),
    MISLEADING_WORDS("Misleading Words or Digital Images on the Internet"),
    ONLINE_ENTICEMENT("Online Enticement of Children for Sexual Acts"),
    UNSOLICITED_OBSCENE("Unsolicited Obscene Material Sent to a Child");

    private final String description;
}
