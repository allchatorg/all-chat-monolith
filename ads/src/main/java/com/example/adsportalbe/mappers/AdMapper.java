package com.example.adsportalbe.mappers;


import com.example.adsportalbe.dto.CachedAd;
import com.example.adsportalbe.dto.ad.AdDetailedViewDto;
import com.example.adsportalbe.dto.ad.AdDto;
import com.example.adsportalbe.models.ad.Ad;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", uses = AdMediaUrlMapper.class)
public interface AdMapper {

    @Mapping(source = "format.type", target = "formatType")
    @Mapping(source = "totalViewsBought", target = "viewsBought")
    @Mapping(source = "totalCost", target = "price")
    @Mapping(source = "submittedAt", target = "submittedDate")
    @Mapping(source = "approvedAt", target = "startDate")
    @Mapping(source = "owner.email", target = "email")
    @Mapping(source = "owner.id", target = "userId")
    AdDto toDto(Ad ad);

    @Mapping(source = "format.type", target = "formatType")
    @Mapping(source = "totalViewsBought", target = "viewsBought")
    @Mapping(source = "totalCost", target = "price")
    @Mapping(source = "submittedAt", target = "submittedDate")
    @Mapping(source = "approvedAt", target = "startDate")
    @Mapping(source = "owner.email", target = "email")
    @Mapping(source = "owner.id", target = "userId")
    @Mapping(source = "textContent", target = "textContent")
    @Mapping(source = "imageUrl", target = "imageUrl", qualifiedByName = "mediaKeyToUrl")
    @Mapping(source = "videoUrl", target = "videoUrl", qualifiedByName = "mediaKeyToUrl")
    @Mapping(source = "receipt.cardBrand", target = "cardBrand")
    @Mapping(source = "receipt.cardLast4", target = "cardLast4")
    @Mapping(source = "rejectionReason", target = "rejectionReason")
    AdDetailedViewDto toDetailedDto(Ad ad);

    @Mapping(source = "format.type", target = "format")
    @Mapping(source = "totalViewsBought", target = "totalViewsBought")
    @Mapping(source = "servedViews", target = "servedViews")
    CachedAd toCachedAd(Ad ad);
}
