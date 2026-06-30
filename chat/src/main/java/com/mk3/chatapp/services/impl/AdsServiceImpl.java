package com.mk3.chatapp.services.impl;

import com.mk3.chatapp.dtos.AttachmentDTO;
import com.mk3.chatapp.dtos.AttachmentTypeDTO;
import com.mk3.chatapp.dtos.requests.ServeAdRequestDto;
import com.mk3.chatapp.dtos.responses.AdvertResponseDTO;
import com.mk3.chatapp.dtos.responses.ServedAdDto;
import com.mk3.chatapp.enums.AttachmentTypeEnum;
import com.mk3.chatapp.enums.MimeType;
import com.mk3.chatapp.services.AdsService;
import com.mk3.chatapp.services.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.security.Principal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdsServiceImpl implements AdsService {

    private final UserService userService;
    private final RestTemplate restTemplate;

    @Value("${ads.service.url:http://localhost:8081/api}")
    private String adsServiceUrl;


    @Override
    public AdvertResponseDTO serveAd(Principal user, String ipAddress) {
        try {
            ServeAdRequestDto request = new ServeAdRequestDto(userService.getPrincipal(user).getId(), ipAddress);
            String url = adsServiceUrl;

            ResponseEntity<ServedAdDto> response = restTemplate.postForEntity(
                    url,
                    request,
                    ServedAdDto.class
            );

            if (response.getStatusCode().is2xxSuccessful()) {
                ServedAdDto servedAdDto = response.getBody();

                if (servedAdDto == null) {
                    return null;
                }

                return convertToAdvertResponse(servedAdDto);
            }

            throw new RuntimeException("Serving ad failed with status: " + response.getStatusCode());
        } catch (Exception e) {
            throw new RuntimeException("Failed to serve ad", e);
        }
    }

    private AdvertResponseDTO convertToAdvertResponse(ServedAdDto servedAdDto) {
        Random random = new Random();

        // Create attachments list
        List<AttachmentDTO> attachments = new ArrayList<>();

        // If format is IMAGE or VIDEO, add attachment
        if ("PHOTO".equalsIgnoreCase(servedAdDto.getFormat()) && servedAdDto.getImageUrl() != null
                && !servedAdDto.getImageUrl().isEmpty()) {
            // Create attachment type for IMAGE
            AttachmentTypeDTO attachmentType = new AttachmentTypeDTO(
                    1L, // id
                    AttachmentTypeEnum.IMAGE,
                    Collections.emptySet(), // acceptedMimeTypes
                    null, // maxFileSizeBytes
                    Collections.emptySet() // availableTags
            );

            AttachmentDTO attachment = new AttachmentDTO(
                    null, // id
                    null, // messageId
                    servedAdDto.getTitle(), // name
                    (long) (random.nextInt(5000000) + 100000), // random size between 100KB and 5MB
                    servedAdDto.getImageUrl(), // url
                    attachmentType,
                    MimeType.PNG, // default mime type for images
                    Collections.emptySet() // no tags
            );
            attachments.add(attachment);
        } else if ("VIDEO".equalsIgnoreCase(servedAdDto.getFormat()) && servedAdDto.getVideoUrl() != null
                && !servedAdDto.getVideoUrl().isEmpty()) {
            // Create attachment type for VIDEO
            AttachmentTypeDTO attachmentType = new AttachmentTypeDTO(
                    2L, // id
                    AttachmentTypeEnum.VIDEO,
                    Collections.emptySet(), // acceptedMimeTypes
                    null, // maxFileSizeBytes
                    Collections.emptySet() // availableTags
            );

            AttachmentDTO attachment = new AttachmentDTO(
                    null, // id
                    null, // messageId
                    servedAdDto.getTitle(), // name
                    (long) (random.nextInt(50000000) + 1000000), // random size between 1MB and 50MB
                    servedAdDto.getVideoUrl(), // url
                    attachmentType,
                    MimeType.MP4, // default mime type for videos
                    Collections.emptySet() // no tags
            );
            attachments.add(attachment);
        }

        // Create the MessageResponseDTO
        return new AdvertResponseDTO(
                servedAdDto.getId(), // id
                Instant.now().toString(),
                servedAdDto.getTextContent(), // content
                null,
                servedAdDto.getTitle(), // senderUsername (using title as sender)
                "#E0EEFF", // color
                attachments, // attachments
                true
        );
    }
}
