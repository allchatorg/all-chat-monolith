package com.example.adsportalbe.models.identity;

import com.mk3.chatapp.enums.Role;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.Subselect;
import org.hibernate.annotations.Synchronize;

import java.time.Instant;

/** Admin-only purchase totals, computed before filtering, sorting, and pagination. */
@Entity
@Immutable
@Subselect("""
        select u.id, u.first_name, u.last_name, u.email, u.role, u.created_at,
               coalesce(spending.purchased_ads_count, 0) as purchased_ads_count,
               coalesce(spending.total_spent, 0) as total_spent,
               (spending.user_id is not null) as has_purchases
        from chat_user u
        left join (
            select purchases.user_id,
                   sum(purchases.ad_count) as purchased_ads_count,
                   sum(purchases.amount) as total_spent
            from (
                select a.owner_id as user_id, 1 as ad_count,
                       case when r.status = 'CAPTURED'
                            then cast(coalesce(r.amount_paid, 0) as numeric(19, 2))
                            else 0 end as amount
                from ads a
                left join payment_receipts r on r.ad_id = a.id
                union all
                select pm.owner_id, 0,
                       case when r.status = 'CAPTURED'
                            then cast(coalesce(r.amount_paid, 0) as numeric(19, 2))
                            else 0 end
                from promoted_messages pm
                left join payment_receipts r on r.id = pm.receipt_id
                union all
                select rp.owner_id, 0,
                       case when r.status = 'CAPTURED'
                            then cast(coalesce(r.amount_paid, 0) as numeric(19, 2))
                            else 0 end
                from room_promotions rp
                left join payment_receipts r on r.id = rp.receipt_id
                union all
                select p.user_id, 0, (p.paid_cents - p.refunded_cents) / 100.0
                from vip_subscription_payment p
            ) purchases
            group by purchases.user_id
        ) spending on spending.user_id = u.id
        """)
@Synchronize({"chat_user", "ads", "payment_receipts", "promoted_messages", "room_promotions", "vip_subscription_payment"})
@Getter
@NoArgsConstructor
public class AdminUserSummary {
    @Id
    private Long id;
    @Column(name = "first_name")
    private String firstName;
    @Column(name = "last_name")
    private String lastName;
    private String email;
    @Enumerated(EnumType.STRING)
    private Role role;
    @Column(name = "created_at")
    private Instant createdAt;
    @Column(name = "purchased_ads_count")
    private Long purchasedAdsCount;
    @Column(name = "total_spent")
    private Double totalSpent;
    @Column(name = "has_purchases")
    private boolean hasPurchases;
}
