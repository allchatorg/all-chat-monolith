package com.mk3.chatapp.mappers;

import com.mk3.chatapp.dtos.responses.ReactionDetailsDTO;
import com.mk3.chatapp.dtos.responses.ReactionSummaryDTO;
import com.mk3.chatapp.models.Reaction;
import com.mk3.chatapp.models.identity.User;
import com.mk3.chatapp.services.SecurityService;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;

@Mapper(componentModel = "spring", uses = {UserMapper.class})
public abstract class ReactionMapper {

    @Autowired
    protected SecurityService securityService;

    @Mapping(target = "messageId", source = "message.id")
    @Mapping(target = "usersCount", expression = "java(reaction.getUsers().size())")
    public abstract ReactionDetailsDTO toDetailsDTO(Reaction reaction);

    public abstract List<ReactionDetailsDTO> toDetailsDTOList(List<Reaction> reactions);

    public ReactionSummaryDTO toSummaryDTO(Reaction reaction) {
        User currentUser = securityService.getCurrentUser();
        Long currentUserId = currentUser != null ? currentUser.getId() : null;

        return new ReactionSummaryDTO(
                reaction.getId(),
                reaction.getMessage().getId(),
                reaction.getEmoji(),
                reaction.getEmojiId(),
                reaction.getUsers().size(),
                currentUserId != null && reaction.getUsers().stream()
                        .anyMatch(user -> user.getId().equals(currentUserId))
        );
    }

    public List<ReactionSummaryDTO> toSummaryDTOList(List<Reaction> reactions) {
        if (reactions == null || reactions.isEmpty()) {
            return new ArrayList<>();
        }
        return reactions.stream()
                .map(this::toSummaryDTO)
                .toList();
    }
}