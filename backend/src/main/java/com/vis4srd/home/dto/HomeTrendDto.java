package com.vis4srd.home.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HomeTrendDto {
    private String date;
    private long scaleCount;
    private long detectionCount;
    private long riskLow;
    private long riskMedium;
    private long riskHigh;
}
