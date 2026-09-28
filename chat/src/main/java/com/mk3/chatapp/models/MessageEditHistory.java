package com.mk3.chatapp.models;

import com.mk3.chatapp.models.identity.User;
import com.mk3.chatapp.utils.AccountLimits;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "message_edit_history")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageEditHistory extends Base {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "message_id", nullable = false)
    private Message message;

    // Archives prior raw marker text; sized to Message.content
    @Column(nullable = false, length = AccountLimits.MAX_RAW_MESSAGE_LENGTH)
    private String content;

    @ManyToOne
    @JoinColumn(name = "edited_by_user_id")
    private User editedBy;

    @ManyToMany
    @JoinTable(
            name = "message_edit_history_attachments",
            joinColumns = @JoinColumn(name = "edit_history_id"),
            inverseJoinColumns = @JoinColumn(name = "attachment_id")
    )
    private List<Attachment> attachments = new ArrayList<>();
}
