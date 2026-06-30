package com.mk3.chatapp.models;

import com.mk3.chatapp.models.identity.User;
import jakarta.persistence.*;
import lombok.*;

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

    @Column(nullable = false, length = 500)
    private String content;

    @ManyToOne
    @JoinColumn(name = "user_id")
    private User sender;

    @OneToMany(mappedBy = "message", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
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