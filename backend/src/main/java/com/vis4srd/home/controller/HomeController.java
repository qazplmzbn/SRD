package com.vis4srd.home.controller;

import com.vis4srd.common.result.ApiResponse;
import com.vis4srd.home.dto.FunctionCardDto;
import com.vis4srd.home.dto.HomeOverviewDto;
import com.vis4srd.home.dto.HomeStatsDto;
import com.vis4srd.home.dto.HomeTrendDto;
import com.vis4srd.home.dto.RefreshStatsDto;
import com.vis4srd.home.service.HomeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/home")
@RequiredArgsConstructor
public class HomeController {

    private final HomeService homeService;

    @GetMapping("/cards")
    public ApiResponse<List<FunctionCardDto>> getHomeCards() {
        return ApiResponse.ok(homeService.getHomeCards());
    }

    @GetMapping("/stats")
    public ApiResponse<HomeStatsDto> getHomeStats(
            @RequestParam(name = "force_refresh", defaultValue = "false") boolean forceRefresh) {
        return ApiResponse.ok(homeService.getHomeStatsDto(forceRefresh));
    }

    @GetMapping("/overview")
    public ApiResponse<HomeOverviewDto> getHomeOverview() {
        return ApiResponse.ok(homeService.getHomeOverview());
    }

    @GetMapping("/trend")
    public Map<String, Object> getHomeTrend(
            @RequestParam(defaultValue = "30") int days) {
        int safeDays = Math.min(Math.max(days, 1), 365);
        List<HomeTrendDto> trendData = homeService.getHomeTrend(safeDays);
        Map<String, Object> body = new HashMap<>();
        body.put("success", true);
        body.put("code", 200);
        body.put("data", trendData);
        return body;
    }

    @PostMapping("/refresh-stats")
    public ApiResponse<RefreshStatsDto> refreshStats() {
        return ApiResponse.ok(homeService.refreshStats());
    }
}
