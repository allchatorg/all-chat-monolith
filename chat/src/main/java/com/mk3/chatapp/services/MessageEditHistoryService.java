package com.mk3.chatapp.services;

import com.mk3.chatapp.models.Attachment;
import com.mk3.chatapp.models.Message;
import com.mk3.chatapp.models.MessageEditHistory;
import com.mk3.chatapp.models.identity.User;

import java.util.List;

public interface MessageEditHistoryService {
    MessageEditHistory save(String previousContent, Message message, List<Attachment> attachmentsAtTime, User editedBy);

    List<MessageEditHistory> findByMessage(Message message);
}
