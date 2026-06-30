package com.mk3.chatapp.models;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Entity
@DiscriminatorValue("ARCHIVE_CHATROOM")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class ArchiveChatRoomAuditLog extends AuditLog {

    @Column(name = "chat_room_id", nullable = false)
    private Long chatRoomId;

    @Column(name = "chat_room_name", nullable = false)
    private String chatRoomName;
}
