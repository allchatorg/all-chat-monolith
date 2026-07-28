package com.mk3.chatapp.mappers;

import com.mk3.chatapp.dtos.responses.MessageResponseDTO;
import com.mk3.chatapp.dtos.responses.ReplyInfoDTO;
import com.mk3.chatapp.models.Message;
import com.mk3.chatapp.utils.DateTimeMapperUtil;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", uses = {AttachmentMapper.class, UserMapper.class, DateTimeMapperUtil.class, ReactionMapper.class})
public interface MessageMapper {

    @Mapping(target = "senderUsername", expression = "java(message.getSender().getApplicationUsername())")
    @Mapping(target = "senderRole", expression = "java(message.getSender().getRole())")
    @Mapping(target = "senderId", source = "message.sender.id")
    @Mapping(target = "bannedUser", source = "message.sender.banned")
    @Mapping(target = "deleted", source = "message.deleted")
    @Mapping(target = "chatRoomId", source = "message.chatRoom.id")
    @Mapping(target = "chatRoomName", source = "message.chatRoom.name")
    @Mapping(target = "createdAt", source = "message.createdAt", qualifiedByName = "instantToString")
    @Mapping(target = "color", source = "message.sender.displayColor")
    @Mapping(target = "senderCountryCode", source = "message.sender.countryCode")
    @Mapping(target = "senderIdVerificationStatus", source = "message.sender.idVerificationStatus")
    @Mapping(target = "replyTo", expression = "java(toReplyInfoDTO(message.getReplyTo(), false))")
    @Mapping(target = "promotion", ignore = true)
    MessageResponseDTO toMessageResponseDTO(Message message);

    default MessageResponseDTO toMessageResponseDTO(Message message, boolean isStaff) {
        var dto = toMessageResponseDTO(message);
        if (dto == null || message.getReplyTo() == null) {
            return dto;
        }
        return dto.withReplyTo(toReplyInfoDTO(message.getReplyTo(), isStaff));
    }

    default ReplyInfoDTO toReplyInfoDTO(Message parent, boolean isStaff) {
        if (parent == null) {
            return null;
        }
        boolean quarantined = Boolean.TRUE.equals(parent.getQuarantined());
        boolean deleted = Boolean.TRUE.equals(parent.getDeleted());
        boolean hideContent = quarantined || (deleted && !isStaff);
        boolean hasAttachment = !hideContent && parent.getAttachments() != null && !parent.getAttachments().isEmpty();
        String attachmentName = hasAttachment ? parent.getAttachments().get(0).getName() : null;
        return new ReplyInfoDTO(
                parent.getId(),
                parent.getSender().getId(),
                parent.getSender().getApplicationUsername(),
                parent.getSender().getDisplayColor(),
                hideContent ? null : parent.getContent(),
                deleted || quarantined,
                hasAttachment,
                attachmentName);
    }
}

