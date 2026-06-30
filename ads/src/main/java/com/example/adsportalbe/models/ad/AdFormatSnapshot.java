package com.example.adsportalbe.models.ad;

import com.example.adsportalbe.models.ad.AdFormatType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "ad_format_snapshots")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdFormatSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long originalFormatId;

    @Enumerated(EnumType.STRING)
    private AdFormatType type;

    private String title;
    private String description;

    private Double pricePerMille;
}
