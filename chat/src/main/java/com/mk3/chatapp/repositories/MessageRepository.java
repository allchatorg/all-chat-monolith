package com.mk3.chatapp.repositories;

import com.mk3.chatapp.models.Message;
import com.mk3.chatapp.models.identity.User;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface MessageRepository extends JpaRepository<Message, Long>, JpaSpecificationExecutor<Message> {

    @Query("""
            SELECT m FROM Message m
            LEFT JOIN FETCH m.replyTo r
            LEFT JOIN FETCH r.sender
            WHERE m.chatRoom.id = :chatRoomId
              AND m.id < :currentMessageId
              AND m.quarantined = false
              AND (m.deleted = false OR :isStaff = true)
            ORDER BY m.id DESC
            """)
    List<Message> findPreviousMessages(@Param("chatRoomId") Long chatRoomId, @Param("currentMessageId") Long currentMessageId, @Param("isStaff") boolean isStaff, Pageable pageable);

    @Query("""
            SELECT CASE WHEN COUNT(m) > 0 THEN true ELSE false END FROM Message m
            WHERE m.chatRoom.id = :chatRoomId
              AND m.id > :messageId
              AND m.quarantined = false
              AND (m.deleted = false OR :isStaff = true)
            """)
    boolean existsByChatRoomIdAndIdGreaterThan(@Param("chatRoomId") Long chatRoomId, @Param("messageId") Long messageId, @Param("isStaff") boolean isStaff);

    @Query("""
            SELECT CASE WHEN COUNT(m) > 0 THEN true ELSE false END FROM Message m
            WHERE m.chatRoom.id = :chatRoomId
              AND m.id < :messageId
              AND m.quarantined = false
              AND (m.deleted = false OR :isStaff = true)
            """)
    boolean existsByChatRoomIdAndIdLessThan(@Param("chatRoomId") Long chatRoomId, @Param("messageId") Long messageId, @Param("isStaff") boolean isStaff);

    @Query("""
            SELECT m FROM Message m
            LEFT JOIN FETCH m.replyTo r
            LEFT JOIN FETCH r.sender
            WHERE m.chatRoom.id = :chatRoomId
              AND m.id > :currentMessageId
              AND m.quarantined = false
              AND (m.deleted = false OR :isStaff = true)
            ORDER BY m.id ASC
            """)
    List<Message> findNextMessages(@Param("chatRoomId") Long chatRoomId, @Param("currentMessageId") Long currentMessageId, @Param("isStaff") boolean isStaff, Pageable pageable);

    @Query("""
            SELECT m FROM Message m
            LEFT JOIN FETCH m.replyTo r
            LEFT JOIN FETCH r.sender
            WHERE m.chatRoom.id = :chatRoomId
              AND m.quarantined = false
              AND (m.deleted = false OR :isStaff = true)
            """)
    List<Message> findVisibleByChatRoomId(@Param("chatRoomId") Long chatRoomId, @Param("isStaff") boolean isStaff, Pageable pageable);

    @Query("""
            SELECT count(m) FROM Message m
            WHERE m.chatRoom.id = :chatRoomId
              AND m.quarantined = false
              AND (m.deleted = false OR :isStaff = true)
            """)
    Long countVisibleByChatRoomId(@Param("chatRoomId") Long chatRoomId, @Param("isStaff") boolean isStaff);

    @Query("""
            SELECT m FROM Message m
            WHERE m.chatRoom.id = :chatRoomId
            AND m.quarantined = false
            AND (m.deleted = false OR :isStaff = true)
            ORDER BY m.id DESC
            LIMIT 1
            """)
    Message findTopVisibleByChatRoomIdOrderByIdDesc(@Param("chatRoomId") Long chatRoomId, @Param("isStaff") boolean isStaff);

    @Query("""
            SELECT m FROM Message m
            WHERE m.id = :messageId
            AND m.quarantined = false
            AND (m.deleted = false OR :isStaff = true)
            """)
    Optional<Message> findById(@Param("messageId") Long messageId, @Param("isStaff") boolean isStaff);

    @Query("""
            SELECT count(m) FROM Message m
            WHERE m.chatRoom.id = :chatRoomId
              AND m.id > :messageId
              AND m.quarantined = false
              AND (m.deleted = false OR :isStaff = true)
            """)
    Integer countVisibleByChatRoomIdAndIdGreaterThan(@Param("chatRoomId") Long chatRoomId, @Param("messageId") Long messageId, @Param("isStaff") boolean isStaff);

    List<Message> findAllBySender(User sender);

    List<Message> findAllBySenderAndCreatedAtAfter(User sender, Instant createdAt);
}