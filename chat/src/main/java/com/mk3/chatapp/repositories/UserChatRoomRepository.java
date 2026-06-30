package com.mk3.chatapp.repositories;

import com.mk3.chatapp.enums.ChatRoomType;
import com.mk3.chatapp.enums.Role;
import com.mk3.chatapp.models.ChatRoom;
import com.mk3.chatapp.models.UserChatRoom;
import com.mk3.chatapp.models.identity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface UserChatRoomRepository extends JpaRepository<UserChatRoom, Long> {
    boolean existsByUserAndChatRoom(User user, ChatRoom chatRoom);

    Optional<UserChatRoom> findUserChatRoomByUserAndChatRoom(User user, ChatRoom chatRoom);

    @EntityGraph(attributePaths = {"chatRoom"})
    List<UserChatRoom> findByUser(User user);

    /**
     * Lists the counterpart membership rows of every PRIVATE conversation the target user
     * takes part in, for an admin review. Filtered by the counterpart's role (only counterparts
     * the viewer strictly outranks are passed in {@code allowedRoles}) and an optional
     * counterpart-username search. Only conversations that actually have at least one message
     * are returned (skips freshly-created, still-empty private rooms). Ordered most-recent first.
     */
    @EntityGraph(attributePaths = {"user", "chatRoom"})
    @Query("""
            select cp from UserChatRoom cp
            where cp.chatRoom.type = com.mk3.chatapp.enums.ChatRoomType.PRIVATE
              and cp.user.id <> :targetUserId
              and cp.user.role in :allowedRoles
              and lower(cp.user.username) like lower(concat('%', :search, '%'))
              and cp.chatRoom.id in (
                  select t.chatRoom.id from UserChatRoom t
                  where t.user.id = :targetUserId
                    and t.chatRoom.type = com.mk3.chatapp.enums.ChatRoomType.PRIVATE
              )
              and exists (
                  select 1 from Message m where m.chatRoom = cp.chatRoom
              )
            order by cp.chatRoom.id desc
            """)
    Page<UserChatRoom> findCounterpartConversationsForUser(
            @Param("targetUserId") Long targetUserId,
            @Param("allowedRoles") Set<Role> allowedRoles,
            @Param("search") String search,
            Pageable pageable);

    Optional<UserChatRoom> findByChatRoom_Name(String chatRoomName);

    @EntityGraph(attributePaths = {"chatRoom"})
    List<UserChatRoom> findByUserAndChatRoom_TypeAndHiddenFalse(User user, ChatRoomType type);

    @EntityGraph(attributePaths = {"chatRoom"})
    List<UserChatRoom> findByUserAndChatRoom_Type(User user, ChatRoomType type);

    @EntityGraph(attributePaths = {"user"})
    List<UserChatRoom> findByChatRoom(ChatRoom chatRoom);
}
