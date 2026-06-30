package com.mk3.chatapp.services.impl;

import com.amazonaws.HttpMethod;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.DeleteObjectRequest;
import com.amazonaws.services.s3.model.GeneratePresignedUrlRequest;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.mk3.chatapp.services.ClamAVService;
import com.mk3.chatapp.services.FileUploadService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Date;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class FileUploadServiceImpl implements FileUploadService {

    private final AmazonS3 wasabiS3Client;
    private final ClamAVService clamAVService;

    @Value("${wasabi.s3.bucket-name}")
    private String bucketName;

    @Value("${wasabi.s3.public-url}")
    private String publicUrl;

    @Value("${spring.profiles.active:prod}")
    private String activeProfile;

    @Value("${app.cdn.domain}")
    private String cdnDomain;

    // Parse object key from file URL (public or storage)
    public String getObjectKey(String fileUrl) {
        if (fileUrl == null || fileUrl.isEmpty()) {
            return null;
        }

        // Since we are no longer using backwards compatibility,
        // the fileUrl stored in the DB is actually just the S3 object key.
        return fileUrl;
    }

    // Parse bucket name from file URL
    public String getBucketName(String fileUrl) {
        return this.bucketName;
    }

    @Override
    public String uploadFile(File file) {
        try {
            if (!clamAVService.isFileClean(file)) {
                throw new SecurityException("File contains malware and cannot be uploaded");
            }
        } catch (IOException e) {
            log.error("Failed to scan file for viruses", e);
            throw new RuntimeException("Failed to scan file for viruses: " + e.getMessage(), e);
        }

        String fileName = generateFileName(file);
        String key = getEnvironmentFolder() + "/" + fileName;

        try {
            wasabiS3Client.putObject(new PutObjectRequest(bucketName, key, file));
            // Return exactly the key, omitting the public URL
            return key;
        } catch (Exception e) {
            throw new RuntimeException("Failed to upload file: " + e.getMessage(), e);
        }
    }

    @Override
    public String getFileUrl(String key) {
        if (key == null || key.isEmpty()) {
            return null;
        }

        if (isProd()) {
            // Cloudflare Worker handles the routing, simply append the key to the domain.
            String baseUrl = cdnDomain.endsWith("/") ? cdnDomain : cdnDomain + "/";
            return baseUrl + key;
        } else {
            return generateSignedUrl(key);
        }
    }

    public String generateSignedUrl(String key) {
        try {
            // Set expiration to 1 hour from now
            Date expiration = new Date();
            long expTimeMillis = expiration.getTime();
            expTimeMillis += 1000 * 60 * 60;
            expiration.setTime(expTimeMillis);

            GeneratePresignedUrlRequest generatePresignedUrlRequest = new GeneratePresignedUrlRequest(bucketName, key)
                    .withMethod(HttpMethod.GET)
                    .withExpiration(expiration);

            URL url = wasabiS3Client.generatePresignedUrl(generatePresignedUrlRequest);
            return url.toString();
        } catch (Exception e) {
            log.error("Failed to generate signed URL for key: {}", key, e);
            throw new RuntimeException("Failed to generate signed URL", e);
        }
    }

    @Override
    public byte[] getFileContent(String fileUrl) {
        try {
            String key = getObjectKey(fileUrl);
            // Use parsed bucket name, or fall back to configured bucket name for public
            // URLs
            String bucketName = getBucketName(fileUrl);
            if (bucketName == null) {
                bucketName = this.bucketName;
            }
            return wasabiS3Client.getObject(bucketName, key).getObjectContent().readAllBytes();
        } catch (Exception e) {
            throw new RuntimeException("Failed to download file content: " + e.getMessage(), e);
        }
    }

    @Override
    public void deleteFile(String fileURL) {
        try {
            String key = getObjectKey(fileURL);
            // Use parsed bucket name, or fall back to configured bucket name for public
            // URLs
            String bucketName = getBucketName(fileURL);
            if (bucketName == null) {
                bucketName = this.bucketName;
            }

            wasabiS3Client.deleteObject(new DeleteObjectRequest(bucketName, key));
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