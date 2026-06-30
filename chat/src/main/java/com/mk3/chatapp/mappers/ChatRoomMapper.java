package com.mk3.chatapp.mappers;

import com.mk3.chatapp.dtos.responses.ChatRoomDTO;
import com.mk3.chatapp.models.ChatRoom;
import org.mapstruct.Mapper;


@Mapper(componentModel = "spring", uses = {MessageMapper.class})
public interface ChatRoomMapper {
    ChatRoomDTO toChatRoomDTO(ChatRoom chatRoom);
}
