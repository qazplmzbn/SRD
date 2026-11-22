package com.vis4srd.archive.service;

import com.vis4srd.common.exception.BusinessException;
import com.vis4srd.dataset.service.BuiltinCsvDatasetService;
import com.vis4srd.home.service.HomeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ArchiveService {

    private final JdbcTemplate jdbcTemplate;
    private final HomeService homeService;
    private final BuiltinCsvDatasetService builtinCsvDatasetService;

    public ArchiveService(
            @Qualifier("mysqlJdbcTemplate") JdbcTemplate jdbcTemplate,
            HomeService homeService,
            BuiltinCsvDatasetService builtinCsvDatasetService) {
        this.jdbcTemplate = jdbcTemplate;
        this.homeService = homeService;
        this.builtinCsvDatasetService = builtinCsvDatasetService;
    }

    public Map<String, Object> getUsers(
            String dataset, String riskLevel, String keyword, String status, int page, int pageSize) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(pageSize, 1), 100);

        StringBuilder where = new StringBuilder("WHERE 1=1");
        List<Object> params = new ArrayList<>();
        if (StringUtils.hasText(dataset)) {
            where.append(" AND dataset_source = ?");
            params.add(dataset);
        }
        if (StringUtils.hasText(riskLevel)) {
            where.append(" AND risk_level = ?");
            params.add(riskLevel);
        }
        if (StringUtils.hasText(keyword)) {
            where.append(" AND user_id LIKE ?");
            params.add("%" + keyword + "%");
        }
        if (StringUtils.hasText(status)) {
            where.append(" AND status = ?");
            params.add(status);
        }

        long total = 0;
        try {
            Long cnt = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM psychological_archives " + where,
                    Long.class,
                    params.toArray());
            total = cnt == null ? 0 : cnt;
        } catch (Exception ex) {
            log.warn("统计用户档案失败: {}", ex.getMessage());
        }

        List<Map<String, Object>> archives = new ArrayList<>();
        if (total > 0) {
            try {
                List<Object> queryParams = new ArrayList<>(params);
                queryParams.add(safeSize);
                queryParams.add((safePage - 1) * safeSize);
                List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                        """
                                SELECT id, user_id, dataset_source, post_count, risk_value, import_timestamp,
                                       risk_level, status, has_timestamp, has_emojis
                                FROM psychological_archives
                                """ + where + """
                                 ORDER BY import_timestamp DESC, id DESC
                                LIMIT ? OFFSET ?
                                """,
                        queryParams.toArray());
                archives = packArchiveRows(rows);
            } catch (Exception ex) {
                log.warn("查询用户档案失败: {}", ex.getMessage());
            }
        }

        // MySQL 无档案时回退内置 CSV（风险检测选人）
        if (total <= 0 && StringUtils.hasText(dataset) && builtinCsvDatasetService.supports(dataset)
                && (!StringUtils.hasText(status) || "active".equalsIgnoreCase(status))) {
            return builtinCsvDatasetService.listArchives(dataset, riskLevel, keyword, safePage, safeSize);
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("archives", archives);
        data.put("total", total);
        data.put("page", safePage);
        data.put("pageSize", safeSize);
        data.put("totalPages", total == 0 ? 0 : (total + safeSize - 1) / safeSize);
        return data;
    }

    public Map<String, Object> getUserDetail(String userHash, String datasetSource) {
        StringBuilder sql = new StringBuilder(
                """
                        SELECT id, user_id, dataset_source, post_count, risk_value, risk_level, import_timestamp
                        FROM psychological_archives
                        WHERE user_id = ?
                        """);
        List<Object> params = new ArrayList<>();
        params.add(userHash);
        if (StringUtils.hasText(datasetSource)) {
            sql.append(" AND dataset_source = ?");
            params.add(datasetSource);
        }
        sql.append(" ORDER BY import_timestamp DESC, id DESC LIMIT 1");

        List<Map<String, Object>> archives;
        try {
            archives = jdbcTemplate.queryForList(sql.toString(), params.toArray());
        } catch (Exception ex) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "用户不存在");
        }
        if (archives.isEmpty()) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "用户不存在");
        }
        Map<String, Object> archive = archives.get(0);
        Long archiveId = toLongObj(archive.get("id"));
        List<Map<String, Object>> postsRows = List.of();
        if (archiveId != null) {
            try {
                postsRows = jdbcTemplate.queryForList(
                        """
                                SELECT post_index, content, fine_risk_value, post_timestamp, emoji_sequence
                                FROM user_posts
                                WHERE archive_id = ?
                                ORDER BY post_index ASC
                                """,
                        archiveId);
            } catch (Exception ex) {
                log.debug("读取贴文失败: {}", ex.getMessage());
            }
        }

        String riskLevel = String.valueOf(archive.get("risk_level"));
        List<Map<String, Object>> posts = new ArrayList<>();
        for (Map<String, Object> p : postsRows) {
            String content = p.get("content") == null ? "" : String.valueOf(p.get("content"));
            Map<String, Object> post = new LinkedHashMap<>();
            post.put("id", userHash + "_" + p.get("post_index"));
            post.put("text", content);
            post.put("label", p.get("fine_risk_value"));
            post.put("timestamp", formatTsSpace(p.get("post_timestamp")));
            post.put("hasEmojis", p.get("emoji_sequence") != null
                    && StringUtils.hasText(String.valueOf(p.get("emoji_sequence"))));
            post.put("emojiSequence", p.get("emoji_sequence"));
            posts.add(post);
        }

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("userId", archive.get("user_id"));
        detail.put("source", archive.get("dataset_source"));
        detail.put("postCount", archive.get("post_count"));
        detail.put("avgLabel", toDouble(archive.get("risk_value")));
        detail.put("maxLabel", archive.get("risk_value"));
        detail.put("riskLevel", riskLevel);
        detail.put("riskScore", riskScore(riskLevel));
        detail.put("posts", posts);
        detail.put("assessmentTime", formatTs(archive.get("import_timestamp")));
        return detail;
    }

    @Transactional
    public int deleteUser(String userHash) {
        int deleted = deleteUsers(List.of(userHash));
        if (deleted == 0) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "用户不存在");
        }
        refreshHomeStats();
        return deleted;
    }

    @Transactional
    public int deleteUsers(List<String> userHashes) {
        List<String> normalized = userHashes == null ? List.of() : userHashes.stream()
                .filter(StringUtils::hasText)
                .distinct()
                .collect(Collectors.toList());
        if (normalized.isEmpty()) {
            return 0;
        }
        String placeholders = normalized.stream().map(h -> "?").collect(Collectors.joining(","));
        Long cnt = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM psychological_archives WHERE user_id IN (" + placeholders + ")",
                Long.class,
                normalized.toArray());
        int deletedCount = cnt == null ? 0 : cnt.intValue();
        if (deletedCount == 0) {
            return 0;
        }
        jdbcTemplate.update(
                "DELETE FROM user_posts WHERE user_id IN (" + placeholders + ")",
                normalized.toArray());
        jdbcTemplate.update(
                "DELETE FROM psychological_archives WHERE user_id IN (" + placeholders + ")",
                normalized.toArray());
        refreshHomeStats();
        return deletedCount;
    }

    private void refreshHomeStats() {
        try {
            homeService.refreshCache();
        } catch (Exception ex) {
            log.warn("刷新首页统计失败: {}", ex.getMessage());
        }
    }

    private List<Map<String, Object>> packArchiveRows(List<Map<String, Object>> rows) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> a : rows) {
            String riskLevel = String.valueOf(a.get("risk_level"));
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", a.get("user_id"));
            item.put("userId", a.get("user_id"));
            item.put("datasetSource", a.get("dataset_source"));
            item.put("postCount", a.get("post_count"));
            item.put("riskValue", a.get("risk_value"));
            item.put("riskLevel", riskLevel);
            item.put("riskScore", riskScore(riskLevel));
            item.put("importTime", formatTs(a.get("import_timestamp")));
            item.put("status", a.get("status") == null ? "ready" : a.get("status"));
            item.put("hasTimestamp", toBool(a.get("has_timestamp")));
            item.put("hasEmojis", toBool(a.get("has_emojis")));
            out.add(item);
        }
        return out;
    }

    private static double riskScore(String riskLevel) {
        if ("high".equalsIgnoreCase(riskLevel)) {
            return 0.9;
        }
        if ("medium".equalsIgnoreCase(riskLevel)) {
            return 0.6;
        }
        return 0.1;
    }

    private static boolean toBool(Object v) {
        if (v instanceof Boolean b) {
            return b;
        }
        if (v instanceof Number n) {
            return n.intValue() != 0;
        }
        return "true".equalsIgnoreCase(String.valueOf(v)) || "1".equals(String.valueOf(v));
    }

    private static Long toLongObj(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(v));
        } catch (Exception ex) {
            return null;
        }
    }

    private static double toDouble(Object v) {
        if (v == null) {
            return 0.0;
        }
        if (v instanceof Number n) {
            return n.doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(v));
        } catch (Exception ex) {
            return 0.0;
        }
    }

    private static String formatTs(Object v) {
        if (v == null) {
            return "";
        }
        if (v instanceof Timestamp ts) {
            return ts.toLocalDateTime().toString();
        }
        if (v instanceof LocalDateTime ldt) {
            return ldt.toString();
        }
        return String.valueOf(v);
    }

    private static String formatTsSpace(Object v) {
        if (v == null) {
            return null;
        }
        String s = formatTs(v);
        return s.isEmpty() ? null : s.replace('T', ' ');
    }
}
