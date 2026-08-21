package com.vis4srd.home.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefreshStatsDto {
    private long totalArchives;
    private RiskDistributionDto riskDistribution;
}
