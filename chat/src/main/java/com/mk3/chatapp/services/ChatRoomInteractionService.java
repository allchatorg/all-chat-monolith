package com.mk3.chatapp.services;

import com.mk3.chatapp.dtos.requests.CreateChatRoomRequestDTO;
import com.mk3.chatapp.dtos.requests.MessageSearchRequestDTO;
import com.mk3.chatapp.dtos.requests.ReactionRequestDTO;
import com.mk3.chatapp.dtos.requests.ReportRequest;
import com.mk3.chatapp.dtos.responses.*;
import com.mk3.chatapp.enums.ChatRoomNoiseLevelEnum;
import com.mk3.chatapp.enums.ReactionType;
import com.mk3.chatapp.enums.RoomPopularitySort;
import com.mk3.chatapp.enums.TopReactedPeriod;
import com.mk3.chatapp.models.identity.User;
import org.springframework.data.domain.Page;

import java.security.Principal;
import java.util.List;

public interface ChatRoomInteractionService {
    UserChatRoomDTO joinChatRoomAndBroadcastEvent(Principal user, Long chatRoomId);

    UserChatRoomDTO joinRandomChatRoomAndBroadcastEvent(Principal user);

    void joinChatRoom(User user, String chatRoomName);

    void leaveChatRoom(Principal user, Long chatRoomId);

    RoomPopulationDTO userBecomesActiveInChatRoom(Principal user, Long previousActiveChatRoomId, Long chatRoomId);

    UserChatRoomDTO createAndJoinChatRoom(Principal user, CreateChatRoomRequestDTO request);

    void disconnectUserFromChatRooms(User user);

    void connectUserToChatRooms(User user);

    void handleHeartbeat(Principal principal, Long activeRoomId);

    List<UserChatRoomDTO> findAllUserChatRoomsWithPopulationAndLastMessage(User user);

    ChatRoomDTO findById(Long chatRoomId);

    List<RoomPopulationDTO> searchChatRoomsByName(String name, Principal principal);

    MessagePageDTO getMessages(Long roomId, Long afterMessageId, Long beforeMessageId, Long aroundMessageId,
                               Principal principal);

    ChatRoomWithMessageMetadataDTO getChatRoomWithMessagesPage(Long roomId, Principal connectedUser);

    Page<MessageResponseDTO> searchMessages(Long roomId, MessageSearchRequestDTO request, Principal principal);

    MessageResponseDTO updateLastReadMessage(Principal connectedUser, Long roomId, Long messageId);

    void reportMessage(ReportRequest request);

    void reactToMessage(ReactionRequestDTO reactionRequestDTO, ReactionType reactionType);

    ReactionDetailsDTO getMessageEmojiReactions(Long messageId, String emoji, Integer limit);

    Page<RoomPopulationDTO> getChatRoomLeaderboard(int page,
                                                   int pageSize,
                                                   RoomPopularitySort popularitySort,
                                                   ChatRoomNoiseLevelEnum chatRoomNoiseLevel);

    Page<MessageResponseDTO> getTopReactedMessages(Long roomId, int page, int pageSize, TopReactedPeriod period, Principal principal);

    Page<MessageResponseDTO> getPromotedMessages(Long roomId, int page, int pageSize, Principal principal);

    /**
     * Rooms with at least one approved room promotion, most recently approved
     * first, enriched with live population stats. Serves at most 25 pages.
     */
    Page<PromotedRoomDTO> getPromotedRooms(int page, int pageSize);
}
