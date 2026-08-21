package com.vis4srd.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "app.datasource.postgres")
public class PostgresDataSourceProperties {
    private String jdbcUrl;
    private String username;
    private String password;
    private String driverClassName = "org.postgresql.Driver";
    private int maximumPoolSize = 10;
    private int minimumIdle = 2;
}
