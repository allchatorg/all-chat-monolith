package com.mk3.chatapp.services;

import com.mk3.chatapp.models.ChatRoom;
import com.mk3.chatapp.models.identity.User;
import com.mk3.chatapp.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import com.mk3.chatapp.exceptions.ForbiddenException;
import org.springframework.stereotype.Service;

/** Room participation is independent of badge visibility and read access. */
@Service
@RequiredArgsConstructor
public class RoomParticipationService {
    private final UserRepository userRepository;

    public void requireParticipation(ChatRoom room, User user) {
        if (room.isVipOnly()) requireVip(user);
    }

    public void requireVip(User user) {
        // Resolve current entitlement rather than trusting a serialized session user.
        var current = user == null ? null : userRepository.findById(user.getId()).orElse(null);
        if (current == null || !current.isVipActive()) {
            throw new ForbiddenException("Only VIP members can participate in VIP-only rooms. You can still read and report.");
        }
    }
}
