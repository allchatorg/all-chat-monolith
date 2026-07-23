package com.mk3.chatapp.services;

import com.mk3.chatapp.dtos.responses.AdvertResponseDTO;

import java.security.Principal;

public interface AdsService {
    AdvertResponseDTO serveAd(Principal userId, String ipAddress);

    void registerClick(Long adId, Principal user, String ipAddress);

    void registerLinkClick(Long adId, String url, Principal user, String ipAddress);
}
