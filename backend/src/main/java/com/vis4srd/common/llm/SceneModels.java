package com.vis4srd.common.llm;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties("app.scenes")
@Data
public class SceneModels {
    private String reportModel, petModel, mobileModel;
    private int reportMaxTokens, petMaxTokens, mobileMaxTokens;
    private double reportTemperature, petTemperature, mobileTemperature;
}