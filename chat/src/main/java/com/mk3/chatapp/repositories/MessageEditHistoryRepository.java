package com.mk3.chatapp.repositories;

import com.mk3.chatapp.models.Message;
import com.mk3.chatapp.models.MessageEditHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MessageEditHistoryRepository extends JpaRepository<MessageEditHistory, Long> {

    List<MessageEditHistory> findAllByMessage(Message message);
}
