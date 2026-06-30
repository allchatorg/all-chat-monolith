package com.mk3.chatapp.services;

import com.mk3.chatapp.dtos.AttachmentDTO;
import com.mk3.chatapp.dtos.requests.CreateMessageRequestDTO;
import com.mk3.chatapp.dtos.requests.EditMessageRequestDTO;
import com.mk3.chatapp.dtos.requests.RemoveMessageAttachmentDTO;
import com.mk3.chatapp.dtos.responses.MessageResponseDTO;
import com.mk3.chatapp.models.Message;
import com.mk3.chatapp.models.MessageDeleteAuditLog;
import org.springframework.web.multipart.MultipartFile;

import java.security.Principal;
import java.util.List;
import java.util.Optional;

public interface ChattingService {
    AttachmentDTO uploadAttachment(MultipartFile file);

    MessageResponseDTO saveAndBroadcastMessage(CreateMessageRequestDTO messageRequestDTO, Principal connectedUser);

    MessageResponseDTO editMessage(Long messageId, EditMessageRequestDTO editMessageRequestDTO);

    MessageResponseDTO removeAttachmentFromMessage(Long messageId, RemoveMessageAttachmentDTO removeAttachmentRequest);

    void deleteAttachment(Long attachmentId);

    Message deleteMessage(Long messageId);

    Optional<MessageDeleteAuditLog> deleteMessageAsSystem(Long messageId, String description);

    List<MessageResponseDTO> getMessageHistory(Long messageId);
}
