package com.mk3.chatapp.services;

import com.mk3.chatapp.dtos.responses.AdvertResponseDTO;

import java.security.Principal;

public interface AdsService {
    AdvertResponseDTO serveAd(Principal userId, String ipAddress);
}
