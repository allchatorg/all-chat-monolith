package com.mk3.chatapp.services;

import com.mk3.chatapp.dtos.requests.CreateMessageRequestDTO;
import com.mk3.chatapp.dtos.requests.MessageSearchRequestDTO;
import com.mk3.chatapp.dtos.responses.MessageEditHistoryDTO;
import com.mk3.chatapp.dtos.responses.MessagePageDTO;
import com.mk3.chatapp.dtos.responses.MessageResponseDTO;
import com.mk3.chatapp.enums.Role;
import com.mk3.chatapp.models.ChatRoom;
import com.mk3.chatapp.models.Message;
import com.mk3.chatapp.models.identity.User;
import org.springframework.data.domain.Page;

import java.security.Principal;
import java.time.Instant;
import java.util.List;

public interface MessagesService {
    MessageResponseDTO broadcastMessage(Message messageRequestDTO, Principal connectedUser);

    MessageResponseDTO broadcastMessageEdit(MessageResponseDTO messageResponseDTO);

    Message saveMessage(CreateMessageRequestDTO messageRequestDTO, User connectedUser, ChatRoom chatRoom);

    MessagePageDTO getPaginatedMessages(Long chatRoomId, Long afterMessageId, Long beforeMessageId,
                                        Long aroundMessageId, Role role);

    Long getMessageCount(Long roomId, Role role);

    Page<MessageResponseDTO> searchMessages(Long roomId, MessageSearchRequestDTO request, Role role);

    Message findById(Long messageId, Role role);

    Message findById(Long messageId);

    Message getLastMessage(Long chatRoomId, Role role);

    Integer countByChatRoomIdAndIdGreaterThan(Long chatRoomId, Long id, Role role);

    void deleteAllUserMessages(User user);

    void deleteUserMessagesAfter(User user, Instant createdAt);

    Message findNearestPreviousNotDeletedMessage(Long chatRoomId, Long currentMessageId);

    void deleteMessage(Long messageId);

    Message deleteMessage(Message message);

    void broadcastMessageDeletion(Message message);

    Message editMessage(Long messageId, String content, User user);

    Message removeAttachmentFromMessage(Long messageId, Long attachmentId, User user);

    List<MessageEditHistoryDTO> getMessageHistory(Long messageId);
}
