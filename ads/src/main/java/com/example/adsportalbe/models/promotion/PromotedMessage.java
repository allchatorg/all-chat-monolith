package com.example.adsportalbe.models.promotion;

import com.example.adsportalbe.enums.CanceledBy;
import com.example.adsportalbe.enums.PromotedMessageStatus;
import com.example.adsportalbe.models.payment.PaymentReceipt;
import com.mk3.chatapp.models.Message;
import com.mk3.chatapp.models.identity.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "promoted_messages", indexes = {
        @Index(name = "idx_promoted_message_room_status", columnList = "chat_room_id, status")
})
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class PromotedMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "message_id")
    private Message message;

    @ManyToOne(optional = false)
    @JoinColumn(name = "owner_id")
    private User owner;

    // Denormalized so sidebar queries and WS broadcasts never need the room join
    @Column(name = "chat_room_id")
    private Long chatRoomId;

    @Column(name = "chat_room_name")
    private String chatRoomName;

    @Enumerated(EnumType.STRING)
    private PromotedMessageStatus status;

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
