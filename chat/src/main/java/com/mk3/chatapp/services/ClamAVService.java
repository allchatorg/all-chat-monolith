package com.mk3.chatapp.services;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

public interface ClamAVService {

    /**
     * Scans a file for viruses.
     *
     * @param file the file to scan
     * @return true if file is clean, false if infected
     * @throws IOException if scanning fails
     */
    boolean isFileClean(File file) throws IOException;

    /**
     * Scans an InputStream for viruses.
     *
     * @param inputStream the input stream to scan
     * @param fileName    the name of the file (for logging)
     * @return true if content is clean, false if infected
     * @throws IOException if scanning fails
     */
    boolean isStreamClean(InputStream inputStream, String fileName) throws IOException;

    /**
     * Pings ClamAV server to check if it's available.
     *
     * @return true if ClamAV is available, false otherwise
     */
    boolean ping();
}
