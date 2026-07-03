package com.example.adsportalbe.controllers;

import com.example.adsportalbe.dto.FileUploadResponseDto;
import com.mk3.chatapp.services.FileUploadService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

@RestController
@RequestMapping("/api/v1/ads-portal/files")
@RequiredArgsConstructor
public class FileUploadController {

    private final FileUploadService fileUploadService;

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<FileUploadResponseDto> uploadFile(@RequestParam("file") MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File is empty");
        }

        String contentType = file.getContentType();
        if (contentType == null || (!contentType.startsWith("image/") && !contentType.startsWith("video/"))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid file type. Only images and videos are allowed.");
        }

        // Create a temporary file
        Path tempDir = Files.createTempDirectory("uploads");
        File tempFile = tempDir.resolve(Objects.requireNonNull(file.getOriginalFilename())).toFile();
        file.transferTo(tempFile);

        try {
            String key = fileUploadService.uploadFile(tempFile);
            return ResponseEntity.ok(new FileUploadResponseDto(key, fileUploadService.getFileUrl(key)));
        } catch (SecurityException e) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
        } finally {
            // Clean up temporary file
            if (tempFile.exists()) {
                tempFile.delete();
            }
            if (tempDir.toFile().exists()) {
                tempDir.toFile().delete();
            }
        }
    }
}
