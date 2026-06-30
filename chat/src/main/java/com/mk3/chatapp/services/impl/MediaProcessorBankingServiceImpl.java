package com.mk3.chatapp.services.impl;

import com.mk3.chatapp.dtos.requests.MediaProcessorBankAttachmentRequestDTO;
import com.mk3.chatapp.dtos.responses.MediaProcessorBankAttachmentResponseDTO;
import com.mk3.chatapp.services.MediaProcessorBankingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Service
public class MediaProcessorBankingServiceImpl implements MediaProcessorBankingService {

    private final RestTemplate restTemplate;

    @Value("${media-processor.base-url}")
    private String mediaProcessorBaseUrl;

    @Value("${media-processor.ncmec-bank-endpoint:/internal/ncmec/bank}")
    private String ncmecBankEndpoint;

    public MediaProcessorBankingServiceImpl(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @Override
    public MediaProcessorBankAttachmentResponseDTO bankAttachment(byte[] fileContent,
                                                                  MediaProcessorBankAttachmentRequestDTO request) {
        if (fileContent == null || fileContent.length == 0) {
            throw new IllegalArgumentException("Attachment content is required for media-processor banking");
        }
        if (request == null) {
            throw new IllegalArgumentException("Banking request metadata is required");
        }

        String filename = StringUtils.hasText(request.filename()) ? request.filename() : "upload.bin";
        ByteArrayResource resource = new ByteArrayResource(fileContent) {
            @Override
            public String getFilename() {
                return filename;
            }
        };

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", resource);
        if (request.attachmentId() != null) {
            body.add("attachmentId", request.attachmentId());
        }
        if (StringUtils.hasText(request.filename())) {
            body.add("filename", request.filename());
        }
        if (StringUtils.hasText(request.mimeType())) {
            body.add("mimeType", request.mimeType());
        }
        if (request.attachmentType() != null) {
            body.add("attachmentType", request.attachmentType().name());
        }
        if (StringUtils.hasText(request.bankName())) {
            body.add("bankName", request.bankName());
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        try {
            MediaProcessorBankAttachmentResponseDTO response = restTemplate.postForObject(
                    buildBankingUrl(),
                    new HttpEntity<>(body, headers),
                    MediaProcessorBankAttachmentResponseDTO.class);

            if (response == null || response.bankContentId() == null) {
                throw new IllegalStateException("Media processor banking returned an empty response");
            }

            return response;
        } catch (RestClientResponseException e) {
            throw new IllegalStateException(
                    "Media processor banking failed with status " + e.getStatusCode() + ": " + e.getResponseBodyAsString(),
                    e);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to bank attachment through media-processor", e);
        }
    }

    private String buildBankingUrl() {
        if (!StringUtils.hasText(mediaProcessorBaseUrl)) {
            throw new IllegalStateException("media-processor.base-url must be configured");
        }

        String baseUrl = mediaProcessorBaseUrl.endsWith("/")
                ? mediaProcessorBaseUrl.substring(0, mediaProcessorBaseUrl.length() - 1)
                : mediaProcessorBaseUrl;
        String endpoint = ncmecBankEndpoint.startsWith("/") ? ncmecBankEndpoint : "/" + ncmecBankEndpoint;
        return baseUrl + endpoint;
    }
}
