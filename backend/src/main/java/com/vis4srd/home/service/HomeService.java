package com.vis4srd.home.service;

import com.vis4srd.home.dto.FunctionCardDto;
import com.vis4srd.home.dto.HomeOverviewDto;
import com.vis4srd.home.dto.HomeStatsDto;
import com.vis4srd.home.dto.HomeTrendDto;
import com.vis4srd.home.dto.RefreshStatsDto;
import com.vis4srd.home.dto.RiskDistributionDto;
import com.vis4srd.home.dto.RiskLevelStatDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class HomeService {

    private static final int CACHE_EXPIRE_HOURS = 1;

    private final JdbcTemplate jdbcTemplate;

    public HomeService(@Qualifier("mysqlJdbcTemplate") JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<FunctionCardDto> getHomeCards() {
        try {
            return jdbcTemplate.query(
                    """
                            SELECT
                                id,
                                card_key AS cardKey,
                                card_label AS cardLabel,
                                card_icon AS cardIcon,
                                card_color AS cardColor,
                                card_bg AS cardBg,
                                card_route AS cardRoute,
                                card_description AS cardDescription,
                                card_order AS cardOrder,
                                is_active AS isActive,
                                is_new AS isNew
                            FROM app_homepage_cards
                            WHERE is_active = TRUE
                            ORDER BY card_order ASC
                            """,
                    new BeanPropertyRowMapper<>(FunctionCardDto.class));
        } catch (Exception ex) {
            log.warn("读取首页功能卡片失败: {}", ex.getMessage());
            return List.of();
        }
    }

    public HomeStatsDto getHomeStatsDto(boolean forceRefresh) {
        HomeStatsSnapshot snapshot = getHomeStats(forceRefresh);
        Map<String, Long> statMap = snapshot.statMap();
        return HomeStatsDto.builder()
                .knowledgeBaseDocs(statMap.getOrDefault("knowledge_base_docs", 0L))
                .totalArchives(statMap.getOrDefault("total_archives", 0L))
                .totalPosts(statMap.getOrDefault("total_posts", 0L))
                .totalScales(statMap.getOrDefault("total_scales", 0L))
                .reportsGenerated(statMap.getOrDefault("reports_generated", 0L))
                .totalUsers(statMap.getOrDefault("total_archives", 0L))
                .riskDistribution(snapshot.riskDistribution())
                .build();
    }

    public HomeOverviewDto getHomeOverview() {
        HomeStatsSnapshot snapshot = getHomeStats(false);
        long totalArchives = snapshot.statMap().getOrDefault("total_archives", 0L);
        long highRisk = snapshot.riskDistribution().getHigh().getCount();
        return HomeOverviewDto.builder()
                .totalUsers(totalArchives)
                .totalDatasets(countDatasets())
                .highRiskUsers(highRisk)
                .recentAssessments(Collections.emptyList())
                .build();
    }

    public RefreshStatsDto refreshStats() {
        HomeStatsSnapshot snapshot = getHomeStats(true);
        return RefreshStatsDto.builder()
                .totalArchives(snapshot.statMap().getOrDefault("total_archives", 0L))
                .riskDistribution(snapshot.riskDistribution())
                .build();
    }

    public List<HomeTrendDto> getHomeTrend(int days) {
        int safeDays = Math.min(Math.max(days, 1), 365);
        int windowDays = Math.max(safeDays - 1, 0);

        List<Map<String, Object>> scaleRows = queryTrendRows(
                """
                        SELECT DATE(created_at) AS date, COUNT(*) AS scaleCount
                        FROM scale_tasks
                        WHERE created_at >= DATE_SUB(CURDATE(), INTERVAL ? DAY)
                          AND status = 'completed'
                        GROUP BY DATE(created_at)
                        ORDER BY date ASC
                        """,
                windowDays);

        List<Map<String, Object>> detectionRows = queryTrendRows(
                """
                        SELECT DATE(created_at) AS date, COUNT(*) AS detectionCount
                        FROM risk_detection_tasks
                        WHERE created_at >= DATE_SUB(CURDATE(), INTERVAL ? DAY)
                          AND status = 'completed'
                        GROUP BY DATE(created_at)
                        ORDER BY date ASC
                        """,
                windowDays);

        List<Map<String, Object>> riskRows = queryTrendRows(
                """
                        SELECT DATE(import_timestamp) AS date, risk_level, COUNT(*) AS cnt
                        FROM psychological_archives
                        WHERE import_timestamp >= DATE_SUB(CURDATE(), INTERVAL ? DAY)
                        GROUP BY DATE(import_timestamp), risk_level
                        ORDER BY date ASC
                        """,
                windowDays);

        Map<String, HomeTrendDto> dateMap = new LinkedHashMap<>();
        LocalDate today = LocalDate.now();
        for (int offset = 0; offset < safeDays; offset++) {
            LocalDate current = today.minusDays(safeDays - 1L - offset);
            String dateKey = current.toString();
            dateMap.put(dateKey, HomeTrendDto.builder()
                    .date(dateKey)
                    .scaleCount(0)
                    .detectionCount(0)
                    .riskLow(0)
                    .riskMedium(0)
                    .riskHigh(0)
                    .build());
        }

        for (Map<String, Object> row : scaleRows) {
            String d = String.valueOf(row.get("date"));
            HomeTrendDto item = dateMap.get(d);
            if (item != null) {
                item.setScaleCount(toLong(row.get("scaleCount")));
            }
        }

        for (Map<String, Object> row : detectionRows) {
            String d = String.valueOf(row.get("date"));
            HomeTrendDto item = dateMap.get(d);
            if (item != null) {
                item.setDetectionCount(toLong(row.get("detectionCount")));
            }
        }

        Map<String, String> riskLevelMap = Map.of(
                "low", "riskLow",
                "medium", "riskMedium",
                "high", "riskHigh",
                "risk_low", "riskLow",
                "risk_medium", "riskMedium",
                "risk_high", "riskHigh");

        for (Map<String, Object> row : riskRows) {
            String d = String.valueOf(row.get("date"));
            HomeTrendDto item = dateMap.get(d);
            if (item == null) {
                continue;
            }
            String levelKey = riskLevelMap.get(String.valueOf(row.get("risk_level")).toLowerCase());
            long cnt = toLong(row.get("cnt"));
            if ("riskLow".equals(levelKey)) {
                item.setRiskLow(cnt);
            } else if ("riskMedium".equals(levelKey)) {
                item.setRiskMedium(cnt);
            } else if ("riskHigh".equals(levelKey)) {
                item.setRiskHigh(cnt);
            }
        }

        return new ArrayList<>(dateMap.values());
    }

    private HomeStatsSnapshot getHomeStats(boolean forceRefresh) {
        if (!forceRefresh && !isCacheStale()) {
            return readStatsFromTable();
        }
        try {
            refreshCache();
        } catch (Exception ex) {
            log.warn("刷新首页统计缓存失败: {}", ex.getMessage());
        }
        return readStatsFromTable();
    }

    private boolean isCacheStale() {
        try {
            LocalDateTime latest = jdbcTemplate.queryForObject(
                    "SELECT MAX(updated_at) FROM homepage_summary_stats LIMIT 1",
                    LocalDateTime.class);
            if (latest == null) {
                return true;
            }
            double ageHours = Duration.between(latest, LocalDateTime.now()).toSeconds() / 3600.0;
            return ageHours > CACHE_EXPIRE_HOURS;
        } catch (Exception ex) {
            return true;
        }
    }

    public void refreshCache() {
        long totalArchives = countSafe("SELECT COUNT(*) FROM psychological_archives");
        long totalPosts = countSafe("SELECT COALESCE(SUM(post_count), 0) FROM psychological_archives");
        long totalScales = countSafe(
                "SELECT COUNT(*) FROM scale_tasks WHERE status = 'completed'");
        long reportsGenerated = countSafe(
                "SELECT COUNT(*) FROM risk_detection_tasks WHERE status = 'completed'");
        long knowledgeDocs = countSafe(
                """
                        SELECT COUNT(*) FROM knowledge_documents
                        WHERE upload_status = 'uploaded' AND is_deleted = FALSE
                        """);

        Map<String, Long> riskDist = new HashMap<>();
        riskDist.put("low", 0L);
        riskDist.put("medium", 0L);
        riskDist.put("high", 0L);
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    """
                            SELECT COALESCE(risk_level, 'unknown') AS level, COUNT(*) AS cnt
                            FROM psychological_archives
                            GROUP BY risk_level
                            """);
            for (Map<String, Object> row : rows) {
                String level = String.valueOf(row.get("level")).toLowerCase();
                if (riskDist.containsKey(level)) {
                    riskDist.put(level, riskDist.get(level) + toLong(row.get("cnt")));
                }
            }
        } catch (Exception ex) {
            log.debug("读取风险分布失败: {}", ex.getMessage());
        }

        long total = riskDist.values().stream().mapToLong(Long::longValue).sum();
        Map<String, Long> statMap = new LinkedHashMap<>();
        statMap.put("knowledge_base_docs", knowledgeDocs);
        statMap.put("total_archives", totalArchives);
        statMap.put("total_posts", totalPosts);
        statMap.put("total_scales", totalScales);
        statMap.put("reports_generated", reportsGenerated);
        statMap.put("risk_low_count", riskDist.get("low"));
        statMap.put("risk_low_percentage", percentage(riskDist.get("low"), total));
        statMap.put("risk_medium_count", riskDist.get("medium"));
        statMap.put("risk_medium_percentage", percentage(riskDist.get("medium"), total));
        statMap.put("risk_high_count", riskDist.get("high"));
        statMap.put("risk_high_percentage", percentage(riskDist.get("high"), total));

        for (Map.Entry<String, Long> entry : statMap.entrySet()) {
            jdbcTemplate.update(
                    """
                            UPDATE homepage_summary_stats
                            SET stat_value = ?, updated_at = NOW()
                            WHERE stat_key = ?
                            """,
                    entry.getValue(), entry.getKey());
        }
    }

    private HomeStatsSnapshot readStatsFromTable() {
        Map<String, Long> statMap = new HashMap<>();
        RiskDistributionDto riskDistribution = RiskDistributionDto.builder()
                .low(RiskLevelStatDto.builder().count(0).percentage(0).build())
                .medium(RiskLevelStatDto.builder().count(0).percentage(0).build())
                .high(RiskLevelStatDto.builder().count(0).percentage(0).build())
                .build();

        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    """
                            SELECT stat_key, stat_value, stat_category
                            FROM homepage_summary_stats
                            ORDER BY stat_order
                            """);
            for (Map<String, Object> row : rows) {
                String statKey = String.valueOf(row.get("stat_key"));
                long statValue = toLong(row.get("stat_value"));
                String category = String.valueOf(row.get("stat_category"));

                if ("core_stats".equals(category)) {
                    statMap.put(statKey, statValue);
                } else if ("risk_distribution".equals(category)) {
                    applyRiskDistribution(riskDistribution, statKey, statValue);
                }
            }
        } catch (Exception ex) {
            log.warn("读取 homepage_summary_stats 失败: {}", ex.getMessage());
        }

        return new HomeStatsSnapshot(statMap, riskDistribution);
    }

    private void applyRiskDistribution(RiskDistributionDto dist, String statKey, long statValue) {
        if (statKey.contains("low")) {
            if (statKey.contains("count")) {
                dist.getLow().setCount(statValue);
            } else if (statKey.contains("percentage")) {
                dist.getLow().setPercentage(statValue);
            }
        } else if (statKey.contains("medium")) {
            if (statKey.contains("count")) {
                dist.getMedium().setCount(statValue);
            } else if (statKey.contains("percentage")) {
                dist.getMedium().setPercentage(statValue);
            }
        } else if (statKey.contains("high")) {
            if (statKey.contains("count")) {
                dist.getHigh().setCount(statValue);
            } else if (statKey.contains("percentage")) {
                dist.getHigh().setPercentage(statValue);
            }
        }
    }

    private long countDatasets() {
        return countSafe("SELECT COUNT(*) FROM dataset_profile");
    }

    private long countSafe(String sql) {
        try {
            Long value = jdbcTemplate.queryForObject(sql, Long.class);
            return value == null ? 0L : value;
        } catch (Exception ex) {
            return 0L;
        }
    }

    private List<Map<String, Object>> queryTrendRows(String sql, int windowDays) {
        try {
            return jdbcTemplate.queryForList(sql, windowDays);
        } catch (Exception ex) {
            log.debug("趋势查询失败: {}", ex.getMessage());
            return List.of();
        }
    }

    private long percentage(long value, long total) {
        if (total <= 0) {
            return 0L;
        }
        return Math.round(value * 100.0 / total);
    }

    private long toLong(Object value) {
        if (value == null) {
            return 0L;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return 0L;
        }
    }

    private record HomeStatsSnapshot(Map<String, Long> statMap, RiskDistributionDto riskDistribution) {
    }
}
