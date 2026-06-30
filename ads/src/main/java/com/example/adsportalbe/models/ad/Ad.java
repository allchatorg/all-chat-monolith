package com.example.adsportalbe.models.ad;

import com.example.adsportalbe.enums.AdStatus;
import com.mk3.chatapp.models.identity.User;
import com.example.adsportalbe.models.payment.PaymentReceipt;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.List;

@Entity
@Table(name = "ads")
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class Ad {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;

    @ManyToOne(optional = false)
    private User owner;

    @ManyToOne(optional = false)
    private AdFormat format;

    // A single ad may use multiple formats (photo + text for example)
    @ManyToMany(cascade = CascadeType.ALL)
    @JoinTable(name = "ad_selected_formats", joinColumns = @JoinColumn(name = "ad_id"), inverseJoinColumns = @JoinColumn(name = "format_id"))
    private List<AdFormatSnapshot> selectedFormats; // snapshot, not the live AdFormat

    private String imageUrl;
    private String videoUrl;

    @Column(columnDefinition = "TEXT")
    private String textContent;

    private Integer totalViewsBought;

    private Integer servedViews;

    private Double totalCost;

    @Enumerated(EnumType.STRING)
    private AdStatus status;

    private Instant submittedAt;
    private Instant approvedAt;

    @Column(columnDefinition = "TEXT")
    private String rejectionReason;

    @OneToOne(mappedBy = "ad", cascade = CascadeType.ALL)
    private PaymentReceipt receipt;
}
