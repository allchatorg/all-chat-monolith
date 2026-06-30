package com.example.adsportalbe.controllers;

import com.example.adsportalbe.dto.AdFormatDto;
import com.example.adsportalbe.services.AdFormatService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/ads-portal/ad-formats")
@RequiredArgsConstructor
public class AdFormatController {

    private final AdFormatService adFormatService;

    @GetMapping
    public ResponseEntity<List<AdFormatDto>> getAllFormats() {
        return ResponseEntity.ok(adFormatService.getAllFormats());
    }

    @GetMapping("/{id}")
    public ResponseEntity<AdFormatDto> getFormatById(@PathVariable Long id) {
        return ResponseEntity.ok(adFormatService.getFormatById(id));
    }
}
