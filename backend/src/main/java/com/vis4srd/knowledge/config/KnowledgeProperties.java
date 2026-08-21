package com.vis4srd.knowledge.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 知识库路径配置（纯 Java 部署，不再依赖 Python backend/）。
 * <p>
 * 默认相对 backend 工作目录：
 * <ul>
 *   <li>RAG 语料：{@code ../knowledge}</li>
 *   <li>上传文档：{@code ../uploads/knowledge}</li>
 * </ul>
 */
@Data
@ConfigurationProperties(prefix = "app.knowledge")
public class KnowledgeProperties {

    /** RAG 知识根目录（含 knowledge_catalog.json） */
    private String rootDir = "../knowledge";

    /** 用户上传知识文档落盘目录 */
    private String uploadDir = "../uploads/knowledge";

    public Path resolveProjectRoot() {
        Path knowledge = resolveKnowledgeRoot();
        if (Files.isDirectory(knowledge) && knowledge.getParent() != null) {
            return knowledge.getParent();
        }
        Path cursor = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize();
        for (int i = 0; i < 6 && cursor != null; i++) {
            if (Files.isDirectory(cursor.resolve("knowledge"))
                    || Files.isDirectory(cursor.resolve("datasets"))
                    || Files.isDirectory(cursor.resolve("backend"))) {
                return cursor;
            }
            cursor = cursor.getParent();
        }
        return Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize().getParent();
    }

    public Path resolveUploadDir() {
        Path configured = resolve(uploadDir);
        if (Files.isDirectory(configured) || (configured.getParent() != null && Files.isDirectory(configured.getParent()))) {
            return configured;
        }
        return resolveProjectRoot().resolve("uploads").resolve("knowledge");
    }

    public Path resolveKnowledgeRoot() {
        Path configured = resolve(rootDir);
        if (Files.isDirectory(configured) && Files.isRegularFile(configured.resolve("knowledge_catalog.json"))) {
            return configured;
        }
        // 启动目录不一定是 backend：向上查找 knowledge/
        Path cursor = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize();
        for (int i = 0; i < 6 && cursor != null; i++) {
            Path candidate = cursor.resolve("knowledge");
            if (Files.isDirectory(candidate)) {
                return candidate.toAbsolutePath().normalize();
            }
            cursor = cursor.getParent();
        }
        return configured;
    }

    private Path resolve(String configured) {
        Path path = Paths.get(configured == null ? "" : configured.trim());
        if (!path.isAbsolute()) {
            path = Paths.get(System.getProperty("user.dir")).resolve(path);
        }
        return path.toAbsolutePath().normalize();
    }
}
