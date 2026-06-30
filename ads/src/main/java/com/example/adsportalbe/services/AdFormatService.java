package com.example.adsportalbe.services;

import com.example.adsportalbe.dto.AdFormatDto;

import java.util.List;

public interface AdFormatService {
    List<AdFormatDto> getAllFormats();

    AdFormatDto getFormatById(Long id);

    double calculateTextCPM(int textLength);
}
