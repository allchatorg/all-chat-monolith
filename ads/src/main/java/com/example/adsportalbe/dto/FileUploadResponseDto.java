package com.example.adsportalbe.dto;

/**
 * Response of the ad-media upload endpoint. {@code key} is the bare storage
 * object key to persist on the ad; {@code url} is a renderable URL for
 * immediate preview (CDN URL in prod, short-lived presigned URL in dev).
 */
public record FileUploadResponseDto(String key, String url) {
}
