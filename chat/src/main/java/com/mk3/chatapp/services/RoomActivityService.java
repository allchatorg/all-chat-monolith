package com.mk3.chatapp.services;

import com.mk3.chatapp.dtos.responses.RoomPopulationDTO;
import com.mk3.chatapp.dtos.responses.TopReactedMessageDTO;
import com.mk3.chatapp.enums.ChatRoomNoiseLevelEnum;
import com.mk3.chatapp.enums.RoomPopularitySort;
import org.springframework.data.domain.Page;

import java.util.List;

public interface RoomActivityService {

    void recordHeartbeat(String userId, String activeRoomId, List<String> allUserRoomIds);

    RoomPopulationDTO userJoinedRoom(String roomId, String userId);

    RoomPopulationDTO userLeftRoom(String roomId, String userId);

    RoomPopulationDTO userBecomesActiveInRoom(String roomId, String userId);

    RoomPopulationDTO userBecomesInactiveInRoom(String roomId, String userId);

    void storeRoomMetadata(String roomId, String roomName);

    void addMessage(String roomId);

    long getMessagesLastHour(String roomId);

    List<RoomPopulationDTO> getTopActiveRooms(int topN);

    List<RoomPopulationDTO> getTopOnlineRooms(int topN);

    RoomPopulationDTO getRoomPopulation(String roomId);

    void resetRoomPopulationData();

    Page<RoomPopulationDTO> getChatRoomLeaderboard(int page, int pageSize, List<String> roomsToExclude,
                                                   RoomPopularitySort roomPopularitySort, ChatRoomNoiseLevelEnum chatRoomNoiseLevelEnum);

    void setMessageCount(String roomId, long count);

    void incrementReactionCount(Long roomId, Long messageId);

    void decrementReactionCount(Long roomId, Long messageId);

    void removeMessageReactions(Long roomId, Long messageId);

    Page<TopReactedMessageDTO> getTopReactedMessages(Long roomId, int page, int pageSize);

    void markRoomAsArchived(String roomId);

    void markRoomAsUnarchived(String roomId);

}
