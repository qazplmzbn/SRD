package com.vis4srd.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path uploads = resolveUploadsDir();
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations(uploads.toUri().toString());
    }

    private static Path resolveUploadsDir() {
        Path cwd = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize();
        Path[] candidates = new Path[]{
                cwd.resolve("../uploads").normalize(),
                cwd.resolve("uploads"),
                cwd.getParent() != null ? cwd.getParent().resolve("uploads") : null
        };
        for (Path candidate : candidates) {
            if (candidate != null && Files.isDirectory(candidate)) {
                return candidate.toAbsolutePath().normalize();
            }
        }
        Path fallback = cwd.resolve("../uploads").toAbsolutePath().normalize();
        return fallback;
    }
}
