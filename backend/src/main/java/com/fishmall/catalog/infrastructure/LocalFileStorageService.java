package com.fishmall.catalog.infrastructure;

import com.fishmall.catalog.config.ProductStorageProperties;
import com.fishmall.common.BusinessException;
import com.fishmall.common.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name="storage.type", havingValue="local", matchIfMissing=true)
public class LocalFileStorageService implements FileStorage {
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp");
    private static final long MAX_IMAGE_BYTES = 5L * 1024 * 1024;

    private final ProductStorageProperties properties;
    private final Path root;

    public LocalFileStorageService(ProductStorageProperties properties) {
        this.properties = properties;
        this.root = Path.of(properties.imageDir()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(root);
        } catch (IOException exception) {
            throw new IllegalStateException("商品图片目录初始化失败", exception);
        }
    }

    @Override
    public StoredFile store(MultipartFile file) throws IOException {
        String extension = validate(file, properties.imageMaxBytes());
        String fileName = UUID.randomUUID() + "." + extension;
        Path target = root.resolve(fileName).normalize();
        if (!target.getParent().equals(root)) {
            throw new BusinessException(ErrorCode.FILE_TYPE_INVALID);
        }
        try (InputStream input = file.getInputStream()) {
            Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException exception) {
            throw new BusinessException(ErrorCode.FILE_WRITE_FAILED);
        }
        return new StoredFile(fileName, properties.imageBaseUrl() + "/" + fileName, file.getSize());
    }

    public static String validate(MultipartFile file,long configuredMax) throws IOException {
        if(file==null||file.isEmpty())throw new BusinessException(ErrorCode.FILE_TYPE_INVALID);
        if(file.getSize()>Math.min(configuredMax,MAX_IMAGE_BYTES))throw new BusinessException(ErrorCode.FILE_TOO_LARGE);
        String ext=extension(file.getOriginalFilename());
        String type=file.getContentType()==null?"":file.getContentType().toLowerCase(Locale.ROOT);
        if(!ALLOWED_EXTENSIONS.contains(ext)||!matchesContentType(ext,type)||!matchesMagic(file,ext))throw new BusinessException(ErrorCode.FILE_TYPE_INVALID);
        return ext;
    }

    private static String extension(String originalFilename) {
        if (originalFilename == null) return "";
        int index = originalFilename.lastIndexOf('.');
        return index < 0 ? "" : originalFilename.substring(index + 1).toLowerCase(Locale.ROOT);
    }

    private static boolean matchesContentType(String extension, String contentType) {
        return switch (extension) {
            case "jpg", "jpeg" -> "image/jpeg".equals(contentType);
            case "png" -> "image/png".equals(contentType);
            case "webp" -> "image/webp".equals(contentType);
            default -> false;
        };
    }

    private static boolean matchesMagic(MultipartFile file, String extension) throws IOException {
        byte[] header;
        try (InputStream input = file.getInputStream()) {
            header = input.readNBytes(12);
        }
        return switch (extension) {
            case "jpg", "jpeg" -> header.length >= 3 && (header[0] & 0xff) == 0xff && (header[1] & 0xff) == 0xd8 && (header[2] & 0xff) == 0xff;
            case "png" -> header.length >= 8 && (header[0] & 0xff) == 0x89 && header[1] == 'P' && header[2] == 'N' && header[3] == 'G'
                    && header[4] == 0x0d && header[5] == 0x0a && header[6] == 0x1a && header[7] == 0x0a;
            case "webp" -> header.length >= 12 && header[0] == 'R' && header[1] == 'I' && header[2] == 'F' && header[3] == 'F'
                    && header[8] == 'W' && header[9] == 'E' && header[10] == 'B' && header[11] == 'P';
            default -> false;
        };
    }
}
