package com.mk3.chatapp.dtos.responses;

import java.util.List;

public record RoomPopulationListDTO(
        List<RoomPopulationDTO> roomPopulations
) {

}
