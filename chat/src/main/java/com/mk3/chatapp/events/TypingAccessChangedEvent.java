package com.mk3.chatapp.events;

/** Null selectors match all users/rooms. Published with the access-changing transaction. */
public record TypingAccessChangedEvent(Long userId, Long chatRoomId) {
}
