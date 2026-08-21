package com.vis4srd.home.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HomeStatsDto {
    private long knowledgeBaseDocs;
    private long totalArchives;
    private long totalPosts;
    private long totalScales;
    private long reportsGenerated;
    private long totalUsers;
    private RiskDistributionDto riskDistribution;
}
