package com.fishmall.catalog.infrastructure;

import com.fishmall.catalog.config.ProductStorageProperties;

import io.minio.*;
import io.minio.errors.ErrorResponseException;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.*;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

@Component
@ConditionalOnProperty(name = "delivery.command")
public class ImageTransferCommand implements ApplicationRunner {
    private final MinioFileStorageService storage;
    private final ProductStorageProperties images;

    public ImageTransferCommand(MinioFileStorageService storage, ProductStorageProperties images) {
        this.storage = storage;
        this.images = images;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        String command = one(args, "delivery.command"), directory = one(args, "delivery.directory");
        if (!Set.of("migrate", "export", "import").contains(command))
            throw new IllegalArgumentException("不支持的图片命令");
        var root = Path.of(directory).toAbsolutePath().normalize();
        if (command.equals("export")) exportObjects(root);
        else
            importFiles(
                    command.equals("migrate")
                            ? Path.of(images.imageDir()).toAbsolutePath().normalize()
                            : root,
                    command.equals("import"));
        System.out.println("图片命令完成：" + command);
        // CLI only: the JVM shutdown hook closes Spring after a successful transfer.
        System.exit(0);
    }

    private String one(ApplicationArguments args, String key) {
        var values = args.getOptionValues(key);
        if (values == null || values.size() != 1 || values.get(0).isBlank())
            throw new IllegalArgumentException("缺少参数：" + key);
        return values.get(0);
    }

    private String digest(InputStream input) throws Exception {
        try (input) {
            var hash = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int count;
            long total = 0;
            while ((count = input.read(buffer)) != -1) {
                total += count;
                if (total > 5 * 1024 * 1024) throw new IOException("图片超出5MB");
                hash.update(buffer, 0, count);
            }
            return HexFormat.of().formatHex(hash.digest());
        }
    }

    private void importFiles(Path root, boolean verifyManifest) throws Exception {
        if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) throw new IOException("源目录不存在");
        var manifest = new HashMap<String, String>();
        if (verifyManifest) {
            for (String line : Files.readAllLines(root.resolve("images.sha256"))) {
                var pair = line.split("  ", 2);
                if (pair.length != 2 || !pair[0].matches("[a-f0-9]{64}"))
                    throw new IOException("图片校验清单无效");
                MinioFileStorageService.validateName(pair[1]);
                if (manifest.put(pair[1], pair[0]) != null) throw new IOException("校验清单存在重复文件");
            }
        }
        List<Path> files;
        try (var list = Files.list(root)) {
            files =
                    list.filter(p -> !p.getFileName().toString().equals("images.sha256"))
                            .sorted()
                            .toList();
        }
        var seen = new HashSet<String>();
        for (Path path : files) {
            String name = path.getFileName().toString();
            MinioFileStorageService.validateName(name);
            if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                throw new IOException("源文件必须是普通图片");
            String hash = digest(Files.newInputStream(path));
            if (verifyManifest && !hash.equals(manifest.get(name)))
                throw new IOException("源图片校验失败：" + name);
            seen.add(name);
            String key = storage.settings().prefix() + name;
            boolean exists = true;
            try {
                String remote =
                        digest(
                                storage.client()
                                        .getObject(
                                                GetObjectArgs.builder()
                                                        .bucket(storage.settings().bucket())
                                                        .object(key)
                                                        .build()));
                if (!hash.equals(remote)) throw new IOException("目标图片内容冲突：" + name);
            } catch (ErrorResponseException e) {
                if (!"NoSuchKey".equals(e.errorResponse().code())) throw e;
                exists = false;
            }
            if (!exists) {
                try (var input = Files.newInputStream(path)) {
                    storage.client()
                            .putObject(
                                    PutObjectArgs.builder()
                                            .bucket(storage.settings().bucket())
                                            .object(key)
                                            .stream(input, Files.size(path), -1)
                                            .contentType(MinioFileStorageService.contentType(name))
                                            .build());
                }
                if (!hash.equals(
                        digest(
                                storage.client()
                                        .getObject(
                                                GetObjectArgs.builder()
                                                        .bucket(storage.settings().bucket())
                                                        .object(key)
                                                        .build()))))
                    throw new IOException("迁移后校验失败：" + name);
            }
        }
        if (verifyManifest && !manifest.keySet().equals(seen))
            throw new IOException("校验清单与图片文件不一致");
        System.out.println("已验证图片数量：" + seen.size());
    }

    private void exportObjects(Path root) throws Exception {
        Files.createDirectories(root);
        try (var files = Files.list(root)) {
            if (files.findAny().isPresent()) throw new IOException("导出目录必须为空");
        }
        var manifest = new ArrayList<String>();
        for (var result :
                storage.client()
                        .listObjects(
                                ListObjectsArgs.builder()
                                        .bucket(storage.settings().bucket())
                                        .prefix(storage.settings().prefix())
                                        .recursive(true)
                                        .build())) {
            String key = result.get().objectName(),
                    name = key.substring(storage.settings().prefix().length());
            MinioFileStorageService.validateName(name);
            Path target = root.resolve(name);
            try (var input =
                            storage.client()
                                    .getObject(
                                            GetObjectArgs.builder()
                                                    .bucket(storage.settings().bucket())
                                                    .object(key)
                                                    .build());
                    var output = Files.newOutputStream(target, StandardOpenOption.CREATE_NEW)) {
                long total = 0;
                byte[] buffer = new byte[8192];
                int n;
                while ((n = input.read(buffer)) != -1) {
                    total += n;
                    if (total > 5 * 1024 * 1024) throw new IOException("图片过大");
                    output.write(buffer, 0, n);
                }
            }
            manifest.add(digest(Files.newInputStream(target)) + "  " + name);
        }
        Files.write(root.resolve("images.sha256"), manifest, StandardOpenOption.CREATE_NEW);
    }
}
