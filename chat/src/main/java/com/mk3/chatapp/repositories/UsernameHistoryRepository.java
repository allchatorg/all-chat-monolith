package com.mk3.chatapp.repositories;

import com.mk3.chatapp.models.UsernameHistory;
import com.mk3.chatapp.models.identity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UsernameHistoryRepository extends JpaRepository<UsernameHistory, Long> {
    List<UsernameHistory> findAllByUser(User user);
}
