package com.mk3.chatapp.mappers;

import com.mk3.chatapp.dtos.responses.MessageResponseDTO;
import com.mk3.chatapp.models.MessageEditHistory;

import java.util.List;

public interface MessageHistoryTransformer {
    MessageResponseDTO transform(MessageEditHistory messageEditHistory);

    List<MessageResponseDTO> transform(List<MessageEditHistory> messageEditHistoryList);
}
