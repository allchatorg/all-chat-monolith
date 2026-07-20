package com.mk3.chatapp.services.impl;

import com.mk3.chatapp.dtos.AttachmentDTO;
import com.mk3.chatapp.dtos.requests.CreateMessageRequestDTO;
import com.mk3.chatapp.dtos.requests.EditMessageRequestDTO;
import com.mk3.chatapp.dtos.requests.RemoveMessageAttachmentDTO;
import com.mk3.chatapp.dtos.responses.MessageResponseDTO;
import com.mk3.chatapp.enums.AttachmentTypeEnum;
import com.mk3.chatapp.enums.ChatRoomType;
import com.mk3.chatapp.mappers.MessageHistoryTransformer;
import com.mk3.chatapp.mappers.MessageMapper;
import com.mk3.chatapp.models.Attachment;
import com.mk3.chatapp.models.ChatRoom;
import com.mk3.chatapp.models.Message;
import com.mk3.chatapp.models.MessageDeleteAuditLog;
import com.mk3.chatapp.models.identity.User;
import com.mk3.chatapp.services.*;
import com.mk3.chatapp.services.csam.CsamAnalysisPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.security.Principal;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChattingServiceImpl implements ChattingService {

    private final AttachmentService attachmentService;
    private final ChatRoomService chatRoomService;
    private final MessagesService messagesService;
    private final UserChatRoomService userChatRoomService;
    private final UserService userService;
    private final MessageMapper messageMapper;
    private final SecurityService securityService;
    private final AuditLogService auditLogService;
    private final RoomActivityService roomActivityService;
    private final MessageEditHistoryService messageEditHistoryService;
    private final MessageHistoryTransformer messageHistoryTransformer;
    private final CsamAnalysisPublisher csamAnalysisPublisher;
    private final PrivateChatService privateChatService;
    private final MessagePromotionPort messagePromotionPort;

    @Transactional
    public AttachmentDTO uploadAttachment(MultipartFile file) {
        var user = securityService.getCurrentUser();
        var attachmentDTO = attachmentService.uploadAttachment(file, user);
        userService.incrementTotalUploadedFilesSize(file.getSize(), user);
        return attachmentDTO;
    }

    @Transactional
    @Override
    public MessageResponseDTO saveAndBroadcastMessage(CreateMessageRequestDTO messageRequestDTO,
                                                      Principal connectedUser) {
        var user = userService.getPrincipal(connectedUser);
        var chatRoom = chatRoomService.findById(messageRequestDTO.chatRoomId());

        if (chatRoom.getType() == ChatRoomType.PRIVATE) {
            validatePrivateSend(user, chatRoom);
        }

        Message message = messagesService.saveMessage(messageRequestDTO, user, chatRoom);
        userChatRoomService.updateLastReadMessage(user, message.getChatRoom(), message);

        if (messageRequestDTO.attachments() != null && !messageRequestDTO.attachments().isEmpty()) {
            message.setAttachments(attachmentService.saveAttachments(messageRequestDTO.attachments(), message));
            publishAttachmentsForAnalysis(message);
        }

        if (chatRoom.getType() == ChatRoomType.PRIVATE) {
            User counterpart = privateChatService.getCounterpart(user, chatRoom);
            if (counterpart != null) {
                privateChatService.unhideIfHidden(counterpart, chatRoom);
            }
        } else {
            roomActivityService.addMessage(chatRoom.getId().toString());
        }

        messagesService.broadcastMessage(message, connectedUser);
        return messageMapper.toMessageResponseDTO(message);
    }

    private void validatePrivateSend(User sender, ChatRoom chatRoom) {
        if (!sender.getRole().isStaffMember()) {
            throw new AccessDeniedException("Private messaging is available to staff only");
        }
        privateChatService.assertMember(sender, chatRoom);
        User counterpart = privateChatService.getCounterpart(sender, chatRoom);
        if (counterpart != null
                && (sender.getBlockedUsers().contains(counterpart)
                || counterpart.getBlockedUsers().contains(sender))) {
            throw new AccessDeniedException("Cannot send a message to a blocked user");
        }
    }

    @Override
    public MessageResponseDTO editMessage(Long messageId, EditMessageRequestDTO editMessageRequestDTO) {
        var user = securityService.getCurrentUser();
        return messageMapper.toMessageResponseDTO(
                messagesService.editMessage(
                        messageId,
                        editMessageRequestDTO.content(),
                        user));
    }

    @Override
    public MessageResponseDTO removeAttachmentFromMessage(Long messageId,
                                                          RemoveMessageAttachmentDTO removeAttachmentRequest) {
        var user = securityService.getCurrentUser();
        var message = messagesService.findById(messageId);
        var attachment = attachmentService.findById(removeAttachmentRequest.attachmentId());

        return messageMapper.toMessageResponseDTO(
                messagesService.removeAttachmentFromMessage(
                        message.getId(),
                        attachment.getId(),
                        user));
    }

    @Override
    public void deleteAttachment(Long attachmentId) {
        attachmentService.deleteAttachment(attachmentId);
    }

    @Override
    public Message deleteMessage(Long messageId) {
        var requestor = securityService.getCurrentUser();
        var message = messagesService.findById(messageId, requestor.getRole());

        validateMessageDeletion(message, requestor);

        // Removing a message cancels its active promotion (pending hold released,
        // approved payment kept). A payment-provider failure aborts the removal so
        // no active promotion is left pointing at a deleted message.
        try {
            messagePromotionPort.cancelActivePromotionForMessage(messageId, deletedByStaff(message, requestor));
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Failed to cancel the promotion on this message — the message was not removed.", e);
        }

        boolean alreadyDeleted = message.getDeleted();
        var deletedMessage = deleteAndBroadcastMessage(message);

        if (!alreadyDeleted && deletedByStaff(deletedMessage, requestor)) {
            auditLogService.logMessageDelete(
                    "Removed Message",
                    "",
                    deletedMessage.getId().toString(),
                    message.getSender().getId());
        }

        return deletedMessage;
    }

    @Override
    @Transactional
    public Optional<MessageDeleteAuditLog> deleteMessageAsSystem(Long messageId, String description) {
        var message = messagesService.findById(messageId);
        // System deletion must never fail on a payment error — log and continue.
        try {
            messagePromotionPort.cancelActivePromotionForMessage(messageId, true);
        } catch (Exception e) {
            log.error("Failed to cancel promotion for system-deleted message {}: {}", messageId, e.getMessage());
        }
        boolean alreadyDeleted = message.getDeleted();
        var deletedMessage = deleteAndBroadcastMessage(message);
        if (alreadyDeleted) {
            return Optional.empty();
        }
        return Optional.of(auditLogService.logMessageDelete(
                "Removed Message",
                description,
                deletedMessage.getId().toString(),
                deletedMessage.getSender().getId()));
    }

    @Override
    public List<MessageResponseDTO> getMessageHistory(Long messageId) {
        var requestor = securityService.getCurrentUser();
        // if (!requestor.getRole().isStaffMember()) {
        // throw new AccessDeniedException("You do not have permission to view message
        // history");
        // }

        var message = messagesService.findById(messageId);
        return messageHistoryTransformer.transform(messageEditHistoryService.findByMessage(message));
    }

    private void validateMessageDeletion(Message message, User requestor) {
        if (message == null) {
            throw new IllegalArgumentException("Message not found");
        }
        var sender = message.getSender();

        if (sender.getId().equals(requestor.getId())) {
            return;
        }

        if (!requestor.getRole().canActOn(sender.getRole())) {
            throw new IllegalArgumentException("You do not have permission to delete this message");
        }
    }

    private boolean deletedByStaff(Message message, User requestor) {
        var sender = message.getSender();
        return !sender.getId().equals(requestor.getId()) && requestor.getRole().canActOn(sender.getRole());
    }

    private Message deleteAndBroadcastMessage(Message message) {
        boolean alreadyDeleted = message.getDeleted();
        var deletedMessage = messagesService.deleteMessage(message);
        if (!alreadyDeleted) {
            messagesService.broadcastMessageDeletion(deletedMessage);
        }
        return deletedMessage;
    }

    private void publishAttachmentsForAnalysis(Message message) {
        message.getAttachments().stream()
                .filter(this::shouldRequestCsamAnalysis)
                .forEach(attachment ->
                        csamAnalysisPublisher.publishAttachmentForAnalysis(attachment, message.getSender()));
    }

    private boolean shouldRequestCsamAnalysis(Attachment attachment) {
        if (attachment == null || attachment.getAttachmentType() == null) {
            return false;
        }

        AttachmentTypeEnum fileType = attachment.getAttachmentType().getFileType();
        return fileType == AttachmentTypeEnum.IMAGE || fileType == AttachmentTypeEnum.VIDEO;
    }
}
