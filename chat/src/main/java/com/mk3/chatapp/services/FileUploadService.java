package com.mk3.chatapp.services;

import java.io.File;

public interface FileUploadService {

    String uploadFile(File file);

    void deleteFile(String fileURL);

    String getFileUrl(String key);

    byte[] getFileContent(String fileUrl);
}
