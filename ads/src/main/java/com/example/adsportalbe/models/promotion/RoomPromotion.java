package com.example.adsportalbe.models.promotion;

import com.example.adsportalbe.enums.CanceledBy;
import com.example.adsportalbe.enums.RoomPromotionStatus;
import com.example.adsportalbe.models.payment.PaymentReceipt;
import com.mk3.chatapp.models.ChatRoom;
import com.mk3.chatapp.models.identity.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * A paid, admin-approved promotion of a public chat room (mirrors
 * {@link PromotedMessage}). Any claimed user may promote any public room;
 * each approval bumps the room to the top of the promoted list. The public
 * list is capped separately by the chat module.
 */
@Entity
@Table(name = "room_promotions", indexes = {
        @Index(name = "idx_room_promotion_room_status", columnList = "chat_room_id, status"),
        @Index(name = "idx_room_promotion_owner_room_status", columnList = "owner_id, chat_room_id, status")
})
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class RoomPromotion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "chat_room_id")
    private ChatRoom chatRoom;

    @ManyToOne(optional = false)
    @JoinColumn(name = "owner_id")
    private User owner;

    // Denormalized so DTOs and WS broadcasts never need the room join
    @Column(name = "chat_room_name")
    private String chatRoomName;

    @Enumerated(EnumType.STRING)
    private RoomPromotionStatus status;

    private Double amount;

    private String currency;

    private Instant submittedAt;
    private Instant approvedAt;
    private Instant resolvedAt;

    @Column(columnDefinition = "TEXT")
    private String reason;

    @Enumerated(EnumType.STRING)
    private CanceledBy canceledBy;

    // Owner-submitted cancellation request on a PENDING promotion; the
    // promotion stays PENDING until an admin acts on it.
    private boolean cancelRequested;

    @Column(columnDefinition = "TEXT")
    private String cancelRequestReason;

    private Instant cancelRequestedAt;

    // Unidirectional; receipt.ad stays null for promotion receipts
    @OneToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "receipt_id")
    private PaymentReceipt receipt;
}
