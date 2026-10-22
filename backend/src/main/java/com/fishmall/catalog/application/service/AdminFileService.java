package com.fishmall.catalog.application.service;

import com.fishmall.catalog.api.vo.FileUploadResponse;
import com.fishmall.catalog.infrastructure.FileStorage;
import com.fishmall.common.BusinessException;
import com.fishmall.common.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@Service
public class AdminFileService {
    private final FileStorage fileStorage;

    public AdminFileService(FileStorage fileStorage) {
        this.fileStorage = fileStorage;
    }

    public FileUploadResponse upload(MultipartFile file) {
        try {
            FileStorage.StoredFile storedFile = fileStorage.store(file);
            return new FileUploadResponse(storedFile.relativeUrl(), storedFile.fileName(), storedFile.sizeBytes());
        } catch (BusinessException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new BusinessException(ErrorCode.FILE_WRITE_FAILED);
        }
    }
}
