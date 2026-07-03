package com.example.adsportalbe.mappers;

import com.mk3.chatapp.services.FileUploadService;
import lombok.RequiredArgsConstructor;
import org.mapstruct.Named;
import org.springframework.stereotype.Component;

/**
 * Resolves stored ad-media object keys to renderable URLs at read time,
 * using the shared chat file upload service (Wasabi).
 */
@Component
@RequiredArgsConstructor
public class AdMediaUrlMapper {

    private final FileUploadService fileUploadService;

    @Named("mediaKeyToUrl")
    public String mediaKeyToUrl(String key) {
        return fileUploadService.getFileUrl(key);
    }
}
