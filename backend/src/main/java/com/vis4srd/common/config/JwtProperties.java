package com.vis4srd.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "app.jwt")
public class JwtProperties {
    private String secret = "dev_secret_key_change_in_production";
    private String algorithm = "HS256";
    private long expireMinutes = 1440;
}
