package com.fishmall.catalog.infrastructure;

import static org.assertj.core.api.Assertions.*;

import com.fishmall.catalog.config.MinioProperties;
import com.fishmall.catalog.config.ProductStorageProperties;

import io.minio.*;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.utility.DockerImageName;

import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.util.*;

@Testcontainers
class MinioStorageIntegrationTest {
    @Container
    static final GenericContainer<?> MINIO =
            new GenericContainer<>(
                            DockerImageName.parse(
                                    System.getProperty(
                                            "minio.test.image", "fishmall-minio:2025-09-07")))
                    .withEnv("MINIO_ROOT_USER", "test-root")
                    .withEnv("MINIO_ROOT_PASSWORD", "test-root-password")
                    .withCommand("server", "/data")
                    .withExposedPorts(9000);

    @TempDir Path legacy;
    MinioFileStorageService storage;
    String bucket;
    final byte[] png =
            Base64.getDecoder()
                    .decode(
                            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jL1kAAAAASUVORK5CYII=");

    @BeforeEach
    void setup() throws Exception {
        bucket = "test-" + UUID.randomUUID().toString().replace("-", "");
        storage =
                new MinioFileStorageService(
                        new MinioProperties(
                                endpoint(), "test-root", "test-root-password", bucket, "products/"),
                        new ProductStorageProperties(
                                legacy.toString(), "/uploads/products", 5242880));
        storage.client().makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
    }

    String endpoint() {
        return "http://" + MINIO.getHost() + ":" + MINIO.getMappedPort(9000);
    }

    String name() {
        return UUID.randomUUID() + ".png";
    }

    @Test
    void uploadsKeepStableUrlAndRoundTripContent() throws Exception {
        var file = storage.store(new MockMultipartFile("file", "test.png", "image/png", png));
        assertThat(file.relativeUrl()).isEqualTo("/uploads/products/" + file.fileName());
        assertThat(storage.read(file.fileName())).isEqualTo(png);
        assertThat(Files.exists(legacy.resolve(file.fileName()))).isFalse();
    }

    @Test
    void missingObjectReadsLegacyButRemoteWinsWhenPresent() throws Exception {
        String name = name();
        Files.write(legacy.resolve(name), new byte[] {1, 2});
        assertThat(storage.read(name)).isEqualTo(new byte[] {1, 2});
        storage.client()
                .putObject(
                        PutObjectArgs.builder().bucket(bucket).object("products/" + name).stream(
                                        new ByteArrayInputStream(png), png.length, -1)
                                .build());
        assertThat(storage.read(name)).isEqualTo(png);
    }

    @Test
    void unavailableBucketCannotFallBackToLocal() throws Exception {
        String name = name();
        Files.write(legacy.resolve(name), png);
        var missing =
                new MinioFileStorageService(
                        new MinioProperties(
                                endpoint(),
                                "test-root",
                                "test-root-password",
                                "missing-bucket",
                                "products/"),
                        new ProductStorageProperties(
                                legacy.toString(), "/uploads/products", 5242880));
        assertThatThrownBy(() -> missing.read(name))
                .isInstanceOf(IOException.class)
                .isNotInstanceOf(FileNotFoundException.class);
    }

    @Test
    void rejectsTraversalAndFalseImageMagic() {
        assertThatThrownBy(() -> storage.read("../secrets.key"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                        () ->
                                storage.store(
                                        new MockMultipartFile(
                                                "file",
                                                "test.png",
                                                "image/png",
                                                new byte[] {1, 2})))
                .isInstanceOf(com.fishmall.common.BusinessException.class);
    }

    @Test
    void bucketIsNotAnonymous() throws Exception {
        var file = storage.store(new MockMultipartFile("file", "test.png", "image/png", png));
        var response =
                HttpClient.newHttpClient()
                        .send(
                                HttpRequest.newBuilder(
                                                URI.create(
                                                        endpoint()
                                                                + "/"
                                                                + bucket
                                                                + "/products/"
                                                                + file.fileName()))
                                        .GET()
                                        .build(),
                                HttpResponse.BodyHandlers.discarding());
        assertThat(response.statusCode()).isEqualTo(403);
    }
}
