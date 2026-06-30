package com.example.adsportalbe.services;

import java.io.File;

public interface FileUploadService {

    String uploadFile(File file);

    void deleteFile(String fileUrl);
}
