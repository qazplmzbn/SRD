package com.vis4srd.home.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HomeOverviewDto {
    private long totalUsers;
    private long totalDatasets;
    private long highRiskUsers;
    private List<Object> recentAssessments;
}
