package com.mk3.chatapp.models;

import com.mk3.chatapp.models.identity.User;
import com.mk3.chatapp.utils.AccountLimits;
import com.mk3.chatapp.utils.ChatMessageContent;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "messages", indexes = {
        @Index(name = "idx_message_chatroom", columnList = "chatroom_id"),
        @Index(name = "idx_message_chatroom_date", columnList = "chatroom_id, created_at")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Message extends Base {
    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chatroom_id")
    private ChatRoom chatRoom;

    // Raw marker text; capped by MessagesServiceImpl.MAX_RAW_LENGTH
    @Column(nullable = false, length = AccountLimits.MAX_RAW_MESSAGE_LENGTH)
    private String content;

    /** Canonical local catalog ID for a standalone sticker; never an image URL. */
    @Column(name = "sticker_id", length = 32)
    private String stickerId;

    // Chat text with formatting stripped and each inline emoji represented by
    // one placeholder, kept for LIKE search; null on rows
    // written before the column existed (search falls back to content).
    @Column(name = "content_plain", length = AccountLimits.VIP_MESSAGE_LENGTH)
    private String contentPlain;

    @PrePersist
    @PreUpdate
    private void syncContentPlain() {
        contentPlain = ChatMessageContent.plainText(content);
    }

    @ManyToOne
    @JoinColumn(name = "user_id")
    private User sender;

    @OneToMany(mappedBy = "message", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @SQLRestriction("deleted = false")
    @ToString.Exclude
    private List<Attachment> attachments = new ArrayList<>();

    @OneToMany(mappedBy = "message")
    private List<Reaction> reactions = new ArrayList<>();

    @Column(name = "edited_at")
    private Instant editedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reply_to_id")
    @ToString.Exclude
    private Message replyTo;

    @OneToMany(mappedBy = "message", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<MessageEditHistory> editHistory = new ArrayList<>();
}
