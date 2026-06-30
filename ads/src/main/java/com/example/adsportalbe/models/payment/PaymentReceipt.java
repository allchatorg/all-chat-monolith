package com.example.adsportalbe.models.payment;

import com.example.adsportalbe.models.ad.Ad;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "payment_receipts")
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class PaymentReceipt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String stripePaymentIntentId;

    private String cardBrand; // “visa”, “mastercard”
    private String cardLast4; // “4242”
    private String cardholderName;

    private String status; // e.g. AUTHORIZED, CAPTURED, REFUNDED
    private String provider; // e.g. STRIPE
    private String currency; // e.g. USD

    private Double amountPaid;

    private Instant paidAt;

    @OneToOne
    @JoinColumn(name = "ad_id")
    private Ad ad;
}
