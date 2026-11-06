package com.qa.autotest.application.importing;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.springframework.stereotype.Service;

/**
 * 导入沙箱（IMP-02）：zip / 目录落盘前的路径校验、限额、只读落盘、校验和。
 *
 * <p>zip slip 是最容易漏的一条：只检查 `..` 字面量不够，`a/../../etc` 规范化后
 * 一样能逃出目标目录，所以判定统一基于 {@code normalize().startsWith(target)}。
 */
@Service
public class ImportSandbox {

    public static final int MAX_ENTRIES = 1000;
    public static final long MAX_TOTAL_BYTES = 64L * 1024 * 1024;
    public static final long MAX_FILE_BYTES = 8L * 1024 * 1024;
    public static final int MAX_DEPTH = 12;

    /** 路径白名单校验：拒绝绝对路径、父目录逃逸、盘符、控制字符、过深路径。 */
    public static boolean isSafeRelativePath(String raw) {
        if (raw == null || raw.isBlank() || raw.length() > 400) {
            return false;
        }
        if (raw.indexOf('\0') >= 0 || raw.chars().anyMatch(c -> c < 32)) {
            return false;
        }
        if (raw.startsWith("/") || raw.startsWith("\\") || raw.startsWith("~")) {
            return false;
        }
        if (raw.matches("^[A-Za-z]:.*")) {
            return false;
        }
        String normalized = raw.replace('\\', '/');
        if (normalized.contains("..") || normalized.contains("//")) {
            return false;
        }
        String[] parts = normalized.split("/");
        return parts.length <= MAX_DEPTH;
    }

    /** 解压到 target，返回相对路径清单；任何一条不合规即整批拒绝（不是跳过）。 */
    public List<String> unpack(InputStream zipStream, Path target) throws IOException {
        List<String> files = new ArrayList<>();
        long total = 0L;
        int entries = 0;
        Files.createDirectories(target);
        try (ZipInputStream in = new ZipInputStream(zipStream, StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                entries++;
                if (entries > MAX_ENTRIES) {
                    throw new IllegalArgumentException("条目数超过上限 " + MAX_ENTRIES);
                }
                String name = entry.getName();
                if (entry.isDirectory()) {
                    if (!isSafeRelativePath(name.endsWith("/") ? name : name + "/")) {
                        throw new IllegalArgumentException("不安全的目录路径: " + name);
                    }
                    Path dir = target.resolve(name).normalize();
                    if (!dir.startsWith(target)) {
                        throw new IllegalArgumentException("目录逃逸: " + name);
                    }
                    Files.createDirectories(dir);
                    continue;
                }
                if (!isSafeRelativePath(name)) {
                    throw new IllegalArgumentException("不安全的文件路径: " + name);
                }
                Path resolved = target.resolve(name).normalize();
                if (!resolved.startsWith(target)) {
                    throw new IllegalArgumentException("路径逃逸: " + name);
                }
                Files.createDirectories(resolved.getParent());
                long size = 0L;
                MessageDigest digest = sha256();
                try (var out = Files.newOutputStream(resolved)) {
                    byte[] buffer = new byte[8192];
                    int read;
                    while ((read = in.read(buffer)) >= 0) {
                        size += read;
                        total += read;
                        if (size > MAX_FILE_BYTES || total > MAX_TOTAL_BYTES) {
                            throw new IllegalArgumentException("文件 / 总大小超过限额: " + name);
                        }
                        digest.update(buffer, 0, read);
                        out.write(buffer, 0, read);
                    }
                }
                if (!resolved.toFile().setReadOnly()) {
                    throw new IllegalStateException("只读落盘失败: " + name);
                }
                files.add(name + "|" + HexFormat.of().formatHex(digest.digest()));
            }
        }
        return List.copyOf(files);
    }

    public static String checksum(List<String> files) {
        MessageDigest digest = sha256();
        for (String file : files) {
            digest.update(file.getBytes(StandardCharsets.UTF_8));
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }
}
