package com.fishmall.catalog.infrastructure;

import com.fishmall.catalog.config.ProductStorageProperties;
import com.fishmall.common.BusinessException;
import com.fishmall.common.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalFileStorageServiceTest {
    @TempDir
    Path tempDir;

    @Test
    void storesPngUsingUuidFileName() throws Exception {
        LocalFileStorageService service = service();
        FileStorage.StoredFile file = service.store(new MockMultipartFile("file", "client-name.png", "image/png", pngBytes()));

        assertThat(file.fileName()).endsWith(".png").doesNotContain("client-name");
        assertThat(file.relativeUrl()).startsWith("/uploads/products/");
    }

    @Test
    void rejectsFileWithInvalidMagic() {
        LocalFileStorageService service = service();
        MockMultipartFile file = new MockMultipartFile("file", "fake.png", "image/png", new byte[]{1, 2, 3, 4, 5, 6, 7, 8});

        assertThatThrownBy(() -> service.store(file))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).errorCode())
                .isEqualTo(ErrorCode.FILE_TYPE_INVALID);
    }

    @Test
    void rejectsFileLargerThanFiveMegabytes() {
        LocalFileStorageService service = service();
        byte[] bytes = new byte[5 * 1024 * 1024 + 1];
        System.arraycopy(pngBytes(), 0, bytes, 0, 8);

        assertThatThrownBy(() -> service.store(new MockMultipartFile("file", "large.png", "image/png", bytes)))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).errorCode())
                .isEqualTo(ErrorCode.FILE_TOO_LARGE);
    }

    private LocalFileStorageService service() {
        return new LocalFileStorageService(new ProductStorageProperties(tempDir.toString(), "/uploads/products", 5L * 1024 * 1024));
    }

    private byte[] pngBytes() {
        return new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0d, 0x0a, 0x1a, 0x0a, 0, 0, 0, 0};
    }
}
