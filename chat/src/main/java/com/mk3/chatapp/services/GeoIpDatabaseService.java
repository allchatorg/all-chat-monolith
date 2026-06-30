package com.mk3.chatapp.services;

import com.maxmind.geoip2.DatabaseReader;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

@Service
@Slf4j
public class GeoIpDatabaseService {

    private static final String DB_URL = "https://git.io/GeoLite2-Country.mmdb";
    private static final String DB_FILENAME = "GeoLite2-Country.mmdb";
    private File databaseFile;

    @Getter
    private DatabaseReader databaseReader;

    @PostConstruct
    public void init() {
        try {
            Path dbPath = Path.of(System.getProperty("java.io.tmpdir"), DB_FILENAME);
            databaseFile = dbPath.toFile();

            downloadDatabase(dbPath);

            if (databaseFile.exists()) {
                this.databaseReader = new DatabaseReader.Builder(databaseFile).build();
                log.info("GeoIP database loaded successfully from {}", databaseFile.getAbsolutePath());
            } else {
                log.error("Failed to load GeoIP database: file not found at {}", dbPath);
            }
        } catch (IOException e) {
            log.error("Error initializing GeoIP database service", e);
        }
    }

    private void downloadDatabase(Path targetPath) {
        log.info("Attempting to download GeoIP database from {}", DB_URL);
        try (InputStream in = URI.create(DB_URL).toURL().openStream()) {
            Files.copy(in, targetPath, StandardCopyOption.REPLACE_EXISTING);
            log.info("GeoIP database downloaded successfully to {}", targetPath);
        } catch (IOException e) {
            log.error("Failed to download GeoIP database", e);
            // Verify if we have an existing file to fallback to
            if (targetPath.toFile().exists()) {
                log.warn("Using existing GeoIP database at {}", targetPath);
            }
        }
    }

}
