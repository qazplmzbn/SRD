package com.vis4srd.home.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FunctionCardDto {
    private Long id;
    private String cardKey;
    private String cardLabel;
    private String cardIcon;
    private String cardColor;
    private String cardBg;
    private String cardRoute;
    private String cardDescription;
    private Integer cardOrder;
    private Boolean isActive;
    private Boolean isNew;
}
