package com.mk3.chatapp.models;


import com.fasterxml.jackson.annotation.JsonManagedReference;
import com.mk3.chatapp.enums.ChatRoomType;
import com.mk3.chatapp.enums.Role;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "chat_rooms")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatRoom extends Base {
    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column
    private String name;

    @OneToMany(mappedBy = "chatRoom", cascade = CascadeType.ALL, orphanRemoval = true)
    @ToString.Exclude
    private List<Message> messages = new ArrayList<>();

    @OneToMany(mappedBy = "chatRoom", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonManagedReference
    private List<UserChatRoom> userChatRooms = new ArrayList<>();

    @Column(name = "required_access_level")
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private Role requiredAccessLevel = Role.GUEST;

    @Column(name = "is_archived")
    @Builder.Default
    private boolean isArchived = false;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private ChatRoomType type = ChatRoomType.PUBLIC;

    @Column(name = "pair_key", unique = true)
    private String pairKey;
}
