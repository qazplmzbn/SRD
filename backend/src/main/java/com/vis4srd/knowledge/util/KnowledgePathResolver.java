package com.vis4srd.knowledge.util;

import com.vis4srd.knowledge.config.KnowledgeProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 解析知识文档相对路径：兼容历史 {@code /rag-skill/knowledge/...} 与 {@code /uploads/...}。
 */
@Component
@RequiredArgsConstructor
public class KnowledgePathResolver {

    private final KnowledgeProperties properties;

    public Path resolve(String filePath) {
        if (!StringUtils.hasText(filePath)) {
            return null;
        }

        Path knowledgeRoot = properties.resolveKnowledgeRoot();
        Path projectRoot = properties.resolveProjectRoot();
        String normalized = filePath.replace('\\', '/').replaceAll("^/+", "");

        if (normalized.startsWith("rag-skill/knowledge/")) {
            String relative = normalized.substring("rag-skill/knowledge/".length());
            return knowledgeRoot.resolve(relative).normalize();
        }

        int idx = normalized.indexOf("/rag-skill/knowledge/");
        if (idx >= 0) {
            String relative = normalized.substring(idx + "/rag-skill/knowledge/".length());
            return knowledgeRoot.resolve(relative).normalize();
        }

        if (normalized.startsWith("knowledge/")) {
            return projectRoot.resolve(normalized).normalize();
        }

        if (normalized.startsWith("uploads/")) {
            Path projectUpload = projectRoot.resolve(normalized).normalize();
            if (Files.exists(projectUpload)) {
                return projectUpload;
            }
            return projectUpload;
        }

        Path underKnowledge = knowledgeRoot.resolve(normalized).normalize();
        if (Files.exists(underKnowledge)) {
            return underKnowledge;
        }
        return projectRoot.resolve(normalized).normalize();
    }
}
