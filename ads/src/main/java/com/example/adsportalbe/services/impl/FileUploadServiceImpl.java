package com.example.adsportalbe.services.impl;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.DeleteObjectRequest;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.example.adsportalbe.services.FileUploadService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class FileUploadServiceImpl implements FileUploadService {

    private final AmazonS3 cloudflareR2Client;

    @Value("${cloudflare.r2.bucket-name}")
    private String bucketName;

    @Value("${cloudflare.r2.public-url}")
    private String publicUrl;

    @Value("${spring.profiles.active:prod}")
    private String activeProfile;

    public static String getObjectKey(String r2Url) {
        if (r2Url == null || r2Url.isEmpty()) {
            return null;
        }

        Pattern publicPattern = Pattern.compile("https://[^/]+\\.r2\\.dev/(.*)");
        Matcher publicMatcher = publicPattern.matcher(r2Url);
        if (publicMatcher.matches()) {
            return publicMatcher.group(1);
        }

        Pattern storagePattern = Pattern.compile("https://[^.]+\\.r2\\.cloudflarestorage\\.com/[^/]+/(.*)");
        Matcher storageMatcher = storagePattern.matcher(r2Url);
        if (storageMatcher.matches()) {
            return storageMatcher.group(1);
        }

        return null;
    }

    public static String getBucketName(String r2Url) {
        if (r2Url == null || r2Url.isEmpty() || r2Url.contains(".r2.dev/")) {
            return null;
        }

        Pattern pattern = Pattern.compile("https://[^.]+\\.r2\\.cloudflarestorage\\.com/([^/]+)/.*");
        Matcher matcher = pattern.matcher(r2Url);
        if (matcher.matches()) {
            return matcher.group(1);
        }

        return null;
    }

    @Override
    public String uploadFile(File file) {
        String fileName = generateFileName(file);
        String key = getEnvironmentFolder() + "/" + fileName;

        try {
            cloudflareR2Client.putObject(new PutObjectRequest(bucketName, key, file));
            return publicUrl + "/" + key;
        } catch (Exception e) {
            throw new RuntimeException("Failed to upload file: " + e.getMessage(), e);
        }
    }

    @Override
    public void deleteFile(String fileURL) {
        try {
            String key = getObjectKey(fileURL);
            if (key != null) {
                String resolvedBucketName = getBucketName(fileURL);
                if (resolvedBucketName == null) {
                    resolvedBucketName = bucketName;
                }

                cloudflareR2Client.deleteObject(new DeleteObjectRequest(resolvedBucketName, key));
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to delete file: " + e.getMessage(), e);
        }
    }

    private String generateFileName(File file) {
        return UUID.randomUUID() + "_" + file.getName();
    }

    private String getEnvironmentFolder() {
        return isProd() ? "prod" : "dev";
    }

    private boolean isProd() {
        return "prod".equalsIgnoreCase(activeProfile);
    }
}
