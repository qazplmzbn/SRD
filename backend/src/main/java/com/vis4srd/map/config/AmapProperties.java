package com.vis4srd.map.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "app.amap")
public class AmapProperties {

    /** 高德 Web 服务 Key，对应环境变量 AMAP_WEB_SERVICE_KEY。 */
    private String key = "";
}
