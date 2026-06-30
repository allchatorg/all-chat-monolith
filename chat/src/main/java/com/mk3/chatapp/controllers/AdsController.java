package com.mk3.chatapp.controllers;

import com.mk3.chatapp.dtos.responses.AdvertResponseDTO;
import com.mk3.chatapp.services.AdsService;
import com.mk3.chatapp.utils.IpAddressUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

@RestController
@RequestMapping("/api/v1/ads")
@RequiredArgsConstructor
public class AdsController {

    private final AdsService adsService;

    @PostMapping("/serve")
    public ResponseEntity<AdvertResponseDTO> serveAd(Principal user, HttpServletRequest request) {
        String ip = IpAddressUtils.getClientIpAddress(request);
        AdvertResponseDTO advertResponseDTO = adsService.serveAd(user, ip);

        // If no ad available, return 204 No Content
        if (advertResponseDTO == null) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(advertResponseDTO);
    }
}
