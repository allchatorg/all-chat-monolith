package com.mk3.chatapp.models;

import com.mk3.chatapp.models.identity.User;
import jakarta.persistence.*;
import lombok.*;

import java.util.HashSet;
import java.util.Set;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "reaction", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"message_id", "emoji"})
})
public class Reaction extends Base {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "message_id", nullable = false)
    private Message message;

    @Column(name = "emoji", nullable = false)
    private String emoji;

    @Column(name = "emojiId", nullable = false)
    private String emojiId;

    @ManyToMany
    @JoinTable(name = "reaction_user", joinColumns = @JoinColumn(name = "reaction_id"), inverseJoinColumns = @JoinColumn(name = "user_id"))
    @Builder.Default
    private Set<User> users = new HashSet<>();
}
