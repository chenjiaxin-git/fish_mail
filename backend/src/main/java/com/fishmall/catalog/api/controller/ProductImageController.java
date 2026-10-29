package com.fishmall.catalog.api.controller;

import com.fishmall.catalog.infrastructure.MinioFileStorageService;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.io.*;
import java.util.concurrent.TimeUnit;

@RestController
@ConditionalOnProperty(name = "storage.type", havingValue = "minio")
public class ProductImageController {
    private final MinioFileStorageService storage;

    public ProductImageController(MinioFileStorageService storage) {
        this.storage = storage;
    }

    @GetMapping("${product.image-base-url:/uploads/products}/{name}")
    public ResponseEntity<byte[]> image(@PathVariable String name) {
        try {
            return ResponseEntity.ok()
                    .header("X-Content-Type-Options", "nosniff")
                    .cacheControl(CacheControl.maxAge(365, TimeUnit.DAYS).cachePublic())
                    .contentType(
                            MediaType.parseMediaType(MinioFileStorageService.contentType(name)))
                    .body(storage.read(name));
        } catch (FileNotFoundException | IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (IOException e) {
            return ResponseEntity.status(503).build();
        }
    }
}
