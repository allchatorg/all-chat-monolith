package com.mk3.chatapp.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum NcmecFileAnnotation {
    ANIME_DRAWING_VIRTUAL("animeDrawingVirtualHentai"),
    /**
     * CAUTION: Cannot be used if relevance is SUPPLEMENTAL
     */
    POTENTIAL_MEME("potentialMeme"),
    VIRAL("viral"),
    POSSIBLE_SELF_PRODUCTION("possibleSelfProduction"),
    PHYSICAL_HARM("physicalHarm"),
    VIOLENCE_GORE("violenceGore"),
    BESTIALITY("bestiality"),
    LIVE_STREAMING("liveStreaming"),
    INFANT("infant"),
    GENERATIVE_AI("generativeAi");

    private final String value;
}
