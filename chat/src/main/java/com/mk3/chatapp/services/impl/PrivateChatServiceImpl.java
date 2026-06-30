package com.mk3.chatapp.services.impl;

import com.mk3.chatapp.dtos.responses.PrivateChatDTO;
import com.mk3.chatapp.enums.ChatRoomType;
import com.mk3.chatapp.enums.Role;
import com.mk3.chatapp.mappers.MessageMapper;
import com.mk3.chatapp.mappers.UserMapper;
import com.mk3.chatapp.models.ChatRoom;
import com.mk3.chatapp.models.Message;
import com.mk3.chatapp.models.UserChatRoom;
import com.mk3.chatapp.models.identity.User;
import com.mk3.chatapp.repositories.ChatRoomRepository;
import com.mk3.chatapp.repositories.UserChatRoomRepository;
import com.mk3.chatapp.repositories.UserRepository;
import com.mk3.chatapp.services.MessagesService;
import com.mk3.chatapp.services.PrivateChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class PrivateChatServiceImpl implements PrivateChatService {

    private final ChatRoomRepository chatRoomRepository;
    private final UserChatRoomRepository userChatRoomRepository;
    private final UserRepository userRepository;
    private final MessagesService messagesService;
    private final MessageMapper messageMapper;
    private final UserMapper userMapper;

    @Override
    @Transactional
    public PrivateChatDTO getOrCreatePrivateChat(User initiator, Long otherUserId) {
        assertClaimed(initiator);
        assertStaff(initiator);
        if (Objects.equals(initiator.getId(), otherUserId)) {
            throw new IllegalArgumentException("Cannot start a private chat with yourself");
        }

        User other = userRepository.findById(otherUserId)
                .orElseThrow(() -> new IllegalArgumentException("User not found with id: " + otherUserId));
        assertClaimed(other);
        assertStaff(other);
        if (other.isBanned()) {
            throw new IllegalArgumentException("User is not available");
        }

        String pairKey = buildPairKey(initiator.getId(), other.getId());

        ChatRoom room = chatRoomRepository.findByPairKey(pairKey)
                .orElseGet(() -> createPrivateRoom(initiator, other, pairKey));

        UserChatRoom initiatorUcr = userChatRoomRepository.findUserChatRoomByUserAndChatRoom(initiator, room)
                .orElseGet(() -> userChatRoomRepository.save(UserChatRoom.builder()
                        .user(initiator)
                        .chatRoom(room)
                        .build()));
        if (initiatorUcr.isHidden()) {
            initiatorUcr.setHidden(false);
            userChatRoomRepository.save(initiatorUcr);
        }

        // Keep the counterpart's membership hidden until the initiator actually
        // sends a message. Opening a chat room from search must not push an empty
        // conversation into the other user's list; saveAndBroadcastMessage calls
        // unhideIfHidden(counterpart, room) on the first message to reveal it.
        userChatRoomRepository.findUserChatRoomByUserAndChatRoom(other, room)
                .orElseGet(() -> userChatRoomRepository.save(UserChatRoom.builder()
                        .user(other)
                        .chatRoom(room)
                        .hidden(true)
                        .build()));

        return toDto(initiator, other, room, initiatorUcr);
    }

    private ChatRoom createPrivateRoom(User a, User b, String pairKey) {
        try {
            ChatRoom room = ChatRoom.builder()
                    .name(null)
                    .type(ChatRoomType.PRIVATE)
                    .pairKey(pairKey)
                    .requiredAccessLevel(Role.GUEST)
                    .build();
            return chatRoomRepository.save(room);
        } catch (DataIntegrityViolationException race) {
            return chatRoomRepository.findByPairKey(pairKey)
                    .orElseThrow(() -> race);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<PrivateChatDTO> listMyConversations(User user) {
        assertClaimed(user);
        List<UserChatRoom> rows = userChatRoomRepository
                .findByUserAndChatRoom_TypeAndHiddenFalse(user, ChatRoomType.PRIVATE);

        return rows.stream()
                .map(ucr -> toDto(user, getCounterpart(user, ucr.getChatRoom()), ucr.getChatRoom(), ucr))
                .toList();
    }

    @Override
    @Transactional
    public PrivateChatDTO openConversation(User user, Long chatRoomId) {
        assertClaimed(user);
        ChatRoom room = chatRoomRepository.findById(chatRoomId)
                .orElseThrow(() -> new IllegalArgumentException("Chat room not found: " + chatRoomId));
        assertPrivate(room);
        UserChatRoom ucr = assertMember(user, room);
        if (ucr.isHidden()) {
            ucr.setHidden(false);
            userChatRoomRepository.save(ucr);
        }
        return toDto(user, getCounterpart(user, room), room, ucr);
    }

    @Override
    @Transactional
    public void hideConversation(User user, Long chatRoomId) {
        assertClaimed(user);
        ChatRoom room = chatRoomRepository.findById(chatRoomId)
                .orElseThrow(() -> new IllegalArgumentException("Chat room not found: " + chatRoomId));
        assertPrivate(room);
        UserChatRoom ucr = assertMember(user, room);
        if (!ucr.isHidden()) {
            ucr.setHidden(true);
            userChatRoomRepository.save(ucr);
        }
    }

    @Override
    @Transactional
    public void unhideIfHidden(User user, ChatRoom chatRoom) {
        userChatRoomRepository.findUserChatRoomByUserAndChatRoom(user, chatRoom)
                .filter(UserChatRoom::isHidden)
                .ifPresent(ucr -> {
                    ucr.setHidden(false);
                    userChatRoomRepository.save(ucr);
                });
    }

    @Override
    public UserChatRoom assertMember(User user, ChatRoom chatRoom) {
        return userChatRoomRepository.findUserChatRoomByUserAndChatRoom(user, chatRoom)
                .orElseThrow(() -> new AccessDeniedException("Not a member of this conversation"));
    }

    @Override
    public User getCounterpart(User user, ChatRoom chatRoom) {
        return userChatRoomRepository.findByChatRoom(chatRoom).stream()
                .map(UserChatRoom::getUser)
                .filter(member -> !Objects.equals(member.getId(), user.getId()))
                .findFirst()
                .orElse(null);
    }

    private PrivateChatDTO toDto(User user, User counterpart, ChatRoom room, UserChatRoom ucr) {
        Message lastMessage = messagesService.getLastMessage(room.getId(), user.getRole());
        Integer unread = countUnread(room.getId(), user, ucr);

        boolean blocked = counterpart != null
                && (user.getBlockedUsers().contains(counterpart)
                || counterpart.getBlockedUsers().contains(user));

        return new PrivateChatDTO(
                room.getId(),
                counterpart != null ? userMapper.toMinimalDto(counterpart) : null,
                unread,
                ucr.getLastReadMessage() != null
                        ? messageMapper.toMessageResponseDTO(ucr.getLastReadMessage())
                        : null,
                lastMessage != null ? messageMapper.toMessageResponseDTO(lastMessage) : null,
                blocked
        );
    }

    private Integer countUnread(Long chatRoomId, User user, UserChatRoom ucr) {
        Message lastRead = ucr.getLastReadMessage();
        if (lastRead == null) {
            Message last = messagesService.getLastMessage(chatRoomId, user.getRole());
            return last == null ? 0 : null;
        }
        Message last = messagesService.getLastMessage(chatRoomId, user.getRole());
        if (last == null || last.getId().equals(lastRead.getId()) || last.getId() < lastRead.getId()) {
            return 0;
        }
        return messagesService.countByChatRoomIdAndIdGreaterThan(chatRoomId, lastRead.getId(), user.getRole());
    }

    private void assertClaimed(User user) {
        if (!user.isClaimed()) {
            throw new AccessDeniedException("Only claimed users can use private chats");
        }
    }

    private void assertStaff(User user) {
        if (!user.getRole().isStaffMember()) {
            throw new AccessDeniedException("Private messaging is available to staff only");
        }
    }

    private void assertPrivate(ChatRoom room) {
        if (room.getType() != ChatRoomType.PRIVATE) {
            throw new IllegalArgumentException("Chat room is not private");
        }
    }

    private String buildPairKey(Long a, Long b) {
        long lo = Math.min(a, b);
        long hi = Math.max(a, b);
        return "private:" + lo + ":" + hi;
    }
}
