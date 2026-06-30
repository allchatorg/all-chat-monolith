package com.mk3.chatapp.services;

import com.mk3.chatapp.dtos.responses.PrivateChatDTO;
import com.mk3.chatapp.models.ChatRoom;
import com.mk3.chatapp.models.UserChatRoom;
import com.mk3.chatapp.models.identity.User;

import java.util.List;

public interface PrivateChatService {

    PrivateChatDTO getOrCreatePrivateChat(User initiator, Long otherUserId);

    List<PrivateChatDTO> listMyConversations(User user);

    PrivateChatDTO openConversation(User user, Long chatRoomId);

    void hideConversation(User user, Long chatRoomId);

    void unhideIfHidden(User user, ChatRoom chatRoom);

    UserChatRoom assertMember(User user, ChatRoom chatRoom);

    User getCounterpart(User user, ChatRoom chatRoom);
}
