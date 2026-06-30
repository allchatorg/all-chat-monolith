package com.mk3.chatapp.repositories;

import com.mk3.chatapp.enums.Role;
import com.mk3.chatapp.models.ChatRoom;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ChatRoomRepository extends JpaRepository<ChatRoom, Long> {
    boolean existsChatRoomByName(String name);

    Optional<ChatRoom> findByName(String name);

    Optional<ChatRoom> findByNameIgnoreCase(String name);

    Optional<ChatRoom> findByPairKey(String pairKey);

    @Query("""
            select chatRoom
            from ChatRoom chatRoom
            where chatRoom.type = com.mk3.chatapp.enums.ChatRoomType.PUBLIC
              and lower(chatRoom.name) like lower(concat('%', :name, '%'))
            """)
    List<ChatRoom> findByNameContainingIgnoreCase(@Param("name") String name);

    @Query("""
            select chatRoom
            from ChatRoom chatRoom
            where chatRoom.type = com.mk3.chatapp.enums.ChatRoomType.PUBLIC
              and chatRoom.requiredAccessLevel = :role
              and chatRoom.isArchived = false
              and lower(chatRoom.name) like lower(concat('%', :name, '%'))
            """)
    List<ChatRoom> findVisibleGuestChatRoomsByName(@Param("role") Role role, @Param("name") String name);

    @Query("""
            select count(chatRoom)
            from ChatRoom chatRoom
            where chatRoom.type = com.mk3.chatapp.enums.ChatRoomType.PUBLIC
              and chatRoom.isArchived = false
              and chatRoom.requiredAccessLevel in :roles
              and not exists (
                  select 1
                  from UserChatRoom userChatRoom
                  where userChatRoom.chatRoom = chatRoom
                    and userChatRoom.user.id = :userId
                    and userChatRoom.deleted = false
              )
            """)
    long countUnjoinedJoinableChatRooms(@Param("roles") List<Role> roles, @Param("userId") Long userId);

    @Query("""
            select chatRoom
            from ChatRoom chatRoom
            where chatRoom.type = com.mk3.chatapp.enums.ChatRoomType.PUBLIC
              and chatRoom.isArchived = false
              and chatRoom.requiredAccessLevel in :roles
              and not exists (
                  select 1
                  from UserChatRoom userChatRoom
                  where userChatRoom.chatRoom = chatRoom
                    and userChatRoom.user.id = :userId
                    and userChatRoom.deleted = false
              )
            order by chatRoom.id
            """)
    List<ChatRoom> findUnjoinedJoinableChatRooms(@Param("roles") List<Role> roles,
                                                 @Param("userId") Long userId,
                                                 Pageable pageable);

    @Query("""
            select count(chatRoom)
            from ChatRoom chatRoom
            where chatRoom.type = com.mk3.chatapp.enums.ChatRoomType.PUBLIC
              and chatRoom.isArchived = false
              and chatRoom.requiredAccessLevel in :roles
            """)
    long countJoinableChatRooms(@Param("roles") List<Role> roles);

    @Query("""
            select chatRoom
            from ChatRoom chatRoom
            where chatRoom.type = com.mk3.chatapp.enums.ChatRoomType.PUBLIC
              and chatRoom.isArchived = false
              and chatRoom.requiredAccessLevel in :roles
            order by chatRoom.id
            """)
    List<ChatRoom> findJoinableChatRooms(@Param("roles") List<Role> roles, Pageable pageable);

    boolean existsChatRoomByNameIgnoreCase(String name);
}
