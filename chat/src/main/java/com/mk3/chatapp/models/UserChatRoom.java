package com.mk3.chatapp.models;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.mk3.chatapp.models.identity.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.NotFound;
import org.hibernate.annotations.NotFoundAction;
import org.hibernate.annotations.Where;

@Entity
@Table(name = "user_chatroom", uniqueConstraints = {@UniqueConstraint(columnNames = {"user_id", "chatroom_id"})})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Where(clause = "deleted = false")
public class UserChatRoom extends Base {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JsonBackReference
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JsonBackReference
    @JoinColumn(name = "chatroom_id", nullable = false)
    private ChatRoom chatRoom;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "last_read_message_id")
    @NotFound(action = NotFoundAction.IGNORE)
    private Message lastReadMessage;

    @Column(nullable = false)
    @Builder.Default
    private boolean hidden = false;
}
