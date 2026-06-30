package com.mk3.chatapp.services.impl;

import com.mk3.chatapp.services.ClamAVService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import xyz.capybara.clamav.ClamavClient;
import xyz.capybara.clamav.commands.scan.result.ScanResult;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

@Service
@RequiredArgsConstructor
@Slf4j
public class ClamAVServiceImpl implements ClamAVService {

    private final ClamavClient clamavClient;

    @Override
    public boolean isFileClean(File file) throws IOException {
        try (InputStream inputStream = new FileInputStream(file)) {
            return isStreamClean(inputStream, file.getName());
        }
    }

    @Override
    public boolean isStreamClean(InputStream inputStream, String fileName) throws IOException {
        ScanResult scanResult = clamavClient.scan(inputStream);
        if (scanResult instanceof ScanResult.VirusFound.OK) {
            return true;
        }

        if (scanResult instanceof ScanResult.VirusFound virusFound) {
            log.warn("Virus found in {}: {}", fileName, virusFound.getFoundViruses());
            return false;
        } else {
            log.error("Unknown scan result for {}", fileName);
            throw new IOException("Unable to scan file");
        }
    }

    @Override
    public boolean ping() {
        try {
            clamavClient.ping();
            return true;
        } catch (Exception e) {
            log.error("Failed to ping ClamAV server", e);
            return false;
        }
    }
}
