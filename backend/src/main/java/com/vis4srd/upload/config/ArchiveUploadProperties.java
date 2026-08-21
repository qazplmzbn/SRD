package com.vis4srd.upload.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 档案上传目录配置；相对路径相对进程工作目录解析。
 */
@Data
@ConfigurationProperties(prefix = "app.upload.archive")
public class ArchiveUploadProperties {

    /** 默认：项目根 uploads/archives */
    private String archivesDir = "../uploads/archives";

    /** 默认：datasets/archives 导入模板 */
    private String templatesDir = "../datasets/archives";

    public Path resolveArchivesDir() {
        return resolve(archivesDir);
    }

    public Path resolveTemplatesDir() {
        return resolve(templatesDir);
    }

    private Path resolve(String configured) {
        Path path = Paths.get(configured == null ? "" : configured.trim());
        if (!path.isAbsolute()) {
            path = Paths.get(System.getProperty("user.dir")).resolve(path);
        }
        return path.toAbsolutePath().normalize();
    }
}
