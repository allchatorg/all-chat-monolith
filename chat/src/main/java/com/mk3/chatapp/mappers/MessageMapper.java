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
    @Mapping(target = "chatRoomVipOnly", source = "message.chatRoom.vipOnly")
    @Mapping(target = "createdAt", source = "message.createdAt", qualifiedByName = "instantToString")
    @Mapping(target = "color", source = "message.sender.displayColor")
    @Mapping(target = "senderCountryCode", source = "message.sender.countryCode")
    @Mapping(target = "senderIdVerificationStatus", source = "message.sender.idVerificationStatus")
    @Mapping(target = "senderVipBadgeVisible", source = "message.sender.vipBadgeVisible")
    @Mapping(target = "senderVipBadgeRevision", source = "message.sender.vipBadgeRevision")
    @Mapping(target = "senderUsernameFont", source = "message.sender.effectiveUsernameFont")
    @Mapping(target = "senderMessageFont", source = "message.sender.effectiveMessageFont")
    @Mapping(target = "senderFontRevision", source = "message.sender.fontRevision")
    @Mapping(target = "replyTo", expression = "java(toReplyInfoDTO(message.getReplyTo(), false))")
    @Mapping(target = "promotion", ignore = true)
    @Mapping(target = "stickerId", expression = "java(visibleStickerId(message, false))")
    MessageResponseDTO toMessageResponseDTO(Message message);

    default MessageResponseDTO toMessageResponseDTO(Message message, boolean isStaff) {
        var dto = toMessageResponseDTO(message);
        if (dto == null) {
            return dto;
        }
        return dto.withStickerId(visibleStickerId(message, isStaff))
                .withReplyTo(toReplyInfoDTO(message.getReplyTo(), isStaff));
    }

    default String visibleStickerId(Message message, boolean isStaff) {
        if (message == null || Boolean.TRUE.equals(message.getQuarantined())
                || (Boolean.TRUE.equals(message.getDeleted()) && !isStaff)) {
            return null;
        }
        return message.getStickerId();
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
                attachmentName,
                parent.getSender().isVipBadgeVisible(),
                parent.getSender().getVipBadgeRevision(),
                parent.getSender().getEffectiveUsernameFont(),
                parent.getSender().getEffectiveMessageFont(),
                parent.getSender().getFontRevision(),
                hideContent ? null : parent.getStickerId());
    }
}
