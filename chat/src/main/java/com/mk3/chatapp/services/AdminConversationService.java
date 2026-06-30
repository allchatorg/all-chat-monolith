package com.mk3.chatapp.services;

import com.mk3.chatapp.dtos.requests.MessageSearchRequestDTO;
import com.mk3.chatapp.dtos.responses.AdminConversationDTO;
import com.mk3.chatapp.dtos.responses.MessagePageDTO;
import com.mk3.chatapp.dtos.responses.MessageResponseDTO;
import org.springframework.data.domain.Page;

/**
 * Admin/moderation review of another user's PRIVATE conversations.
 *
 * Visibility rule enforced on every operation: the viewer may only see/open/act on a
 * conversation between the target user and a counterpart whose role the viewer strictly
 * outranks ({@code viewer.role.canActOn(counterpart.role)}). A conversation whose counterpart
 * has an equal-or-higher role is invisible and cannot be opened even by direct room id.
 */
public interface AdminConversationService {

    Page<AdminConversationDTO> listConversations(Long targetUserId, String search, int page, int pageSize);

    MessagePageDTO getConversationMessages(Long targetUserId, Long roomId, Long afterMessageId,
                                           Long beforeMessageId, Long aroundMessageId);

    void deleteConversationMessage(Long targetUserId, Long roomId, Long messageId);

    /**
     * Searches messages within a single conversation of the target user, identified by
     * {@code request.chatRoomId()}. Enforces the same membership + counterpart-visibility
     * checks as {@link #getConversationMessages}.
     */
    Page<MessageResponseDTO> searchConversationMessages(Long targetUserId, MessageSearchRequestDTO request);
}
