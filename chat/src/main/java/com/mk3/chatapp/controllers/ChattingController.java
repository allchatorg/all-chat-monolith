package com.mk3.chatapp.controllers;

import com.mk3.chatapp.dtos.AttachmentDTO;
import com.mk3.chatapp.dtos.requests.CreateMessageRequestDTO;
import com.mk3.chatapp.dtos.requests.EditMessageRequestDTO;
import com.mk3.chatapp.dtos.requests.RemoveMessageAttachmentDTO;
import com.mk3.chatapp.dtos.responses.MessagingAvailabilityDTO;
import com.mk3.chatapp.dtos.responses.MessageResponseDTO;
import com.mk3.chatapp.services.ChattingService;
import com.mk3.chatapp.services.MessagingAvailabilityService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.security.Principal;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/chatting")
public class ChattingController {
    private final ChattingService chattingService;
    private final MessagingAvailabilityService messagingAvailabilityService;

    @GetMapping("/messaging-availability")
    public ResponseEntity<MessagingAvailabilityDTO> getMessagingAvailability() {
        return ResponseEntity.ok(messagingAvailabilityService.getCurrentAvailability());
    }

    @PostMapping("/messages")
    public ResponseEntity<MessageResponseDTO> sendMessage(@RequestBody CreateMessageRequestDTO createMessageRequestDTO,
                                                          Principal principal) {
        return ResponseEntity.ok(chattingService.saveAndBroadcastMessage(createMessageRequestDTO, principal));
    }

    @GetMapping("/messages/{messageId}/history")
    public ResponseEntity<List<MessageResponseDTO>> getMessageHistory(@PathVariable Long messageId) {
        return ResponseEntity.ok(chattingService.getMessageHistory(messageId));
    }

    @DeleteMapping("/messages/{messageId}")
    public ResponseEntity<Void> deleteMessage(@PathVariable Long messageId) {
        chattingService.deleteMessage(messageId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/attachments")
    public ResponseEntity<AttachmentDTO> uploadAttachment(MultipartFile attachment) {
        return ResponseEntity.ok(chattingService.uploadAttachment(attachment));
    }

    @DeleteMapping("/attachments/{attachmentId}")
    public ResponseEntity<Void> deleteAttachment(@PathVariable Long attachmentId) {
        chattingService.deleteAttachment(attachmentId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/messages/{messageId}/edit")
    public ResponseEntity<MessageResponseDTO> editMessage(@PathVariable Long messageId,
                                                          @RequestBody EditMessageRequestDTO editMessageRequest) {
        return ResponseEntity.ok(chattingService.editMessage(messageId, editMessageRequest));
    }

    @PatchMapping("/messages/{messageId}/remove-attachment")
    public ResponseEntity<MessageResponseDTO> removeAttachmentFromMessage(
            @PathVariable Long messageId,
            @RequestBody RemoveMessageAttachmentDTO removeAttachmentRequest) {
        return ResponseEntity.ok(chattingService.removeAttachmentFromMessage(messageId, removeAttachmentRequest));
    }

}
