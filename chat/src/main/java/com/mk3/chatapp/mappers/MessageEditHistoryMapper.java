package com.mk3.chatapp.mappers;

import com.mk3.chatapp.dtos.responses.MessageEditHistoryDTO;
import com.mk3.chatapp.models.MessageEditHistory;
import com.mk3.chatapp.utils.DateTimeMapperUtil;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring", uses = {DateTimeMapperUtil.class, AttachmentMapper.class})
public interface MessageEditHistoryMapper {

    @Mapping(target = "messageId", source = "message.id")
    @Mapping(target = "chatRoomId", source = "message.chatRoom.id")
    @Mapping(target = "senderId", source = "message.sender.id")
    @Mapping(target = "senderUsername", source = "message.sender.username")
    @Mapping(target = "createdAt", source = "createdAt", qualifiedByName = "instantToString")
    @Mapping(target = "attachments", source = "attachments")
    MessageEditHistoryDTO toDto(MessageEditHistory history);

    List<MessageEditHistoryDTO> toDtos(List<MessageEditHistory> histories);

}
