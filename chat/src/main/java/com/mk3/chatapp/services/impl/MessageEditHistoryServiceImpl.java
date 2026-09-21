package com.mk3.chatapp.services.impl;

import com.mk3.chatapp.models.Attachment;
import com.mk3.chatapp.models.Message;
import com.mk3.chatapp.models.MessageEditHistory;
import com.mk3.chatapp.models.identity.User;
import com.mk3.chatapp.repositories.MessageEditHistoryRepository;
import com.mk3.chatapp.services.MessageEditHistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@RequiredArgsConstructor
@Service
public class MessageEditHistoryServiceImpl implements MessageEditHistoryService {

    private final MessageEditHistoryRepository messageEditHistoryRepository;

    @Override
    public MessageEditHistory save(String previousContent, Message message, List<Attachment> attachmentsAtTime,
                                   User editedBy) {
        MessageEditHistory messageEditHistory = MessageEditHistory.builder()
                .content(previousContent)
                .message(message)
                .attachments(attachmentsAtTime)
                .editedBy(editedBy)
                .build();
        return messageEditHistoryRepository.save(messageEditHistory);
    }

    @Override
    public List<MessageEditHistory> findByMessage(Message message) {
        return messageEditHistoryRepository.findAllByMessage(message);
    }
}
