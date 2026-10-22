package com.fishmall.catalog.infrastructure;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

public interface FileStorage {
    StoredFile store(MultipartFile file) throws IOException;

    record StoredFile(String fileName, String relativeUrl, long sizeBytes) {
    }
}
