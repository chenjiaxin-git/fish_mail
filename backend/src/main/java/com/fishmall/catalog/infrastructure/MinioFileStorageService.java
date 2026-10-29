package com.fishmall.catalog.infrastructure;

import com.fishmall.catalog.config.MinioProperties;
import com.fishmall.catalog.config.ProductStorageProperties;
import com.fishmall.common.*;

import io.minio.*;
import io.minio.errors.ErrorResponseException;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Service
@ConditionalOnProperty(name = "storage.type", havingValue = "minio")
public class MinioFileStorageService implements FileStorage {
    private final MinioProperties settings;
    private final ProductStorageProperties images;
    private final MinioClient client;

    public MinioFileStorageService(MinioProperties settings, ProductStorageProperties images) {
        this.settings = settings;
        this.images = images;
        if (settings.endpoint() == null
                || settings.accessKey() == null
                || settings.secretKey() == null
                || settings.accessKey().isBlank()
                || settings.secretKey().isBlank()) throw new IllegalStateException("MinIO连接配置缺失");
        client =
                MinioClient.builder()
                        .endpoint(settings.endpoint())
                        .credentials(settings.accessKey(), settings.secretKey())
                        .httpClient(
                                new okhttp3.OkHttpClient.Builder()
                                        .connectTimeout(5, TimeUnit.SECONDS)
                                        .readTimeout(10, TimeUnit.SECONDS)
                                        .writeTimeout(10, TimeUnit.SECONDS)
                                        .callTimeout(20, TimeUnit.SECONDS)
                                        .build())
                        .build();
    }

    public MinioClient client() {
        return client;
    }

    public MinioProperties settings() {
        return settings;
    }

    @Override
    public StoredFile store(MultipartFile file) throws IOException {
        String extension = LocalFileStorageService.validate(file, images.imageMaxBytes());
        String name = UUID.randomUUID() + "." + extension;
        try (var input = file.getInputStream()) {
            client.putObject(
                    PutObjectArgs.builder()
                            .bucket(settings.bucket())
                            .object(settings.prefix() + name)
                            .stream(input, file.getSize(), -1)
                            .contentType(contentType(name))
                            .build());
            return new StoredFile(name, images.imageBaseUrl() + "/" + name, file.getSize());
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.FILE_WRITE_FAILED);
        }
    }

    public byte[] read(String name) throws IOException {
        validateName(name);
        try (var input =
                client.getObject(
                        GetObjectArgs.builder()
                                .bucket(settings.bucket())
                                .object(settings.prefix() + name)
                                .build())) {
            byte[] value = input.readNBytes(5 * 1024 * 1024 + 1);
            if (value.length > 5 * 1024 * 1024) throw new IOException("图片超出读取限制");
            return value;
        } catch (ErrorResponseException e) {
            if (!"NoSuchKey".equals(e.errorResponse().code())) throw new IOException("MinIO读取失败");
            Path root = Path.of(images.imageDir()).toAbsolutePath().normalize(),
                    path = root.resolve(name).normalize();
            if (!path.getParent().equals(root)
                    || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                throw new FileNotFoundException("图片不存在");
            try (var input = Files.newInputStream(path)) {
                byte[] value = input.readNBytes(5 * 1024 * 1024 + 1);
                if (value.length > 5 * 1024 * 1024) throw new IOException("图片超出读取限制");
                return value;
            }
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("MinIO读取失败");
        }
    }

    public static void validateName(String name) {
        if (name == null
                || !name.matches(
                        "[a-fA-F0-9]{8}(-[a-fA-F0-9]{4}){3}-[a-fA-F0-9]{12}\\.(jpg|jpeg|png|webp)"))
            throw new IllegalArgumentException("图片文件名不合法");
    }

    public static String contentType(String name) {
        return name.endsWith(".png")
                ? "image/png"
                : name.endsWith(".webp") ? "image/webp" : "image/jpeg";
    }
}
