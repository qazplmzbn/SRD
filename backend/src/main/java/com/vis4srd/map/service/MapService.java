package com.vis4srd.map.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 心理机构与热线查询，依赖 PostgreSQL（PostGIS）。对齐 Python MapService。
 */
@Slf4j
@Service
public class MapService {

    private final JdbcTemplate pg;

    public MapService(@Qualifier("postgresJdbcTemplate") JdbcTemplate postgresJdbcTemplate) {
        this.pg = postgresJdbcTemplate;
    }

    // ==================== 机构 ====================

    public record InstitutionPage(List<Map<String, Object>> data, long total) {
    }

    public InstitutionPage getInstitutions(
            String city, String typeFilter, String district, int page, int limit) {
        int safePage = Math.max(page, 1);
        int safeLimit = Math.min(Math.max(limit, 1), 10000);
        int offset = (safePage - 1) * safeLimit;

        StringBuilder where = new StringBuilder();
        List<Object> params = new ArrayList<>();
        appendEq(where, params, "city", city);
        appendEq(where, params, "type", typeFilter);
        appendEq(where, params, "district", district);
        String whereSql = where.isEmpty() ? "" : " WHERE " + where;

        try {
            List<Map<String, Object>> rows = pg.queryForList(
                    """
                            SELECT id, name, type, address, phone, rating, hours,
                                   longitude, latitude, city, district, province,
                                   data_source, poi_id
                            FROM institutions
                            """
                            + whereSql
                            + " ORDER BY id LIMIT ? OFFSET ?",
                    appendArgs(params, safeLimit, offset));
            Long total = pg.queryForObject(
                    "SELECT COUNT(*) FROM institutions" + whereSql,
                    Long.class,
                    params.toArray());
            return new InstitutionPage(normalizeRows(rows), total == null ? 0L : total);
        } catch (Exception ex) {
            log.warn("查询机构列表失败（PG 可能不可用）: {}", ex.getMessage());
            return new InstitutionPage(List.of(), 0L);
        }
    }

    public List<Map<String, Object>> getNearbyInstitutions(
            double longitude,
            double latitude,
            double radiusKm,
            int limit,
            String typeFilter) {
        int safeLimit = Math.min(Math.max(limit, 1), 100);
        double radiusMeters = radiusKm * 1000;

        String typeClause = "";
        List<Object> params = new ArrayList<>();
        // ST_MakePoint 出现两次：Distance 与 DWithin 各一对 lng/lat
        params.add(longitude);
        params.add(latitude);
        params.add(longitude);
        params.add(latitude);
        params.add(radiusMeters);
        if (typeFilter != null && !typeFilter.isBlank()) {
            typeClause = " AND type = ?";
            params.add(typeFilter);
        }
        params.add(safeLimit);

        String sql = """
                SELECT id, name, type, address, phone, rating, hours,
                       longitude, latitude, city, district, province,
                       data_source,
                       ST_Distance(
                           location::geography,
                           ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography
                       ) as distance_meters
                FROM institutions
                WHERE ST_DWithin(
                    location::geography,
                    ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography,
                    ?
                )
                """
                + typeClause
                + """
                ORDER BY distance_meters
                LIMIT ?
                """;

        try {
            List<Map<String, Object>> rows = pg.queryForList(sql, params.toArray());
            List<Map<String, Object>> result = new ArrayList<>();
            for (Map<String, Object> row : normalizeRows(rows)) {
                Object meters = row.get("distance_meters");
                if (meters instanceof Number number) {
                    row.put("distance_km", Math.round(number.doubleValue() / 10.0) / 100.0);
                }
                result.add(row);
            }
            return result;
        } catch (Exception ex) {
            log.warn("附近机构查询失败（PG/PostGIS 可能不可用）: {}", ex.getMessage());
            return List.of();
        }
    }

    public Map<String, Object> getInstitutionById(int institutionId) {
        try {
            List<Map<String, Object>> rows = pg.queryForList(
                    "SELECT * FROM institutions WHERE id = ?", institutionId);
            return rows.isEmpty() ? null : normalizeRow(rows.get(0));
        } catch (Exception ex) {
            log.warn("按 ID 查询机构失败: {}", ex.getMessage());
            return null;
        }
    }

    public Map<String, Object> getInstitutionByPoiId(String poiId) {
        try {
            List<Map<String, Object>> rows = pg.queryForList(
                    "SELECT * FROM institutions WHERE poi_id = ?", poiId);
            return rows.isEmpty() ? null : normalizeRow(rows.get(0));
        } catch (Exception ex) {
            log.warn("按 POI 查询机构失败: {}", ex.getMessage());
            return null;
        }
    }

    public List<Map<String, Object>> getCities() {
        try {
            List<Map<String, Object>> rows = pg.queryForList(
                    """
                            SELECT city, COUNT(*) as count
                            FROM institutions
                            WHERE city IS NOT NULL AND city != ''
                            GROUP BY city
                            ORDER BY count DESC, city
                            """);
            List<Map<String, Object>> result = new ArrayList<>();
            for (Map<String, Object> r : rows) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("name", r.get("city"));
                item.put("institution_count", toLong(r.get("count")));
                result.add(item);
            }
            return result;
        } catch (Exception ex) {
            log.warn("查询城市列表失败: {}", ex.getMessage());
            return List.of();
        }
    }

    public List<String> getDistricts(String city) {
        try {
            return pg.query(
                    """
                            SELECT DISTINCT district
                            FROM institutions
                            WHERE city = ? AND district IS NOT NULL AND district != ''
                            ORDER BY district
                            """,
                    (rs, rowNum) -> rs.getString("district"),
                    city);
        } catch (Exception ex) {
            log.warn("查询区县列表失败: {}", ex.getMessage());
            return List.of();
        }
    }

    public List<String> getInstitutionTypes() {
        try {
            return pg.query(
                    """
                            SELECT DISTINCT type
                            FROM institutions
                            WHERE type IS NOT NULL AND type != ''
                            ORDER BY type
                            """,
                    (rs, rowNum) -> rs.getString("type"));
        } catch (Exception ex) {
            log.warn("查询机构类型失败: {}", ex.getMessage());
            return List.of();
        }
    }

    public Map<String, Object> getInstitutionStatistics() {
        Map<String, Object> empty = emptyInstitutionStats();
        try {
            Long total = pg.queryForObject("SELECT COUNT(*) FROM institutions", Long.class);
            long totalVal = total == null ? 0L : total;
            Long withLocation = pg.queryForObject(
                    """
                            SELECT COUNT(*)
                            FROM institutions
                            WHERE longitude IS NOT NULL AND latitude IS NOT NULL
                            """,
                    Long.class);
            long withLoc = withLocation == null ? 0L : withLocation;

            List<Map<String, Object>> byType = pg.queryForList(
                    """
                            SELECT type, COUNT(*) as count
                            FROM institutions
                            WHERE type IS NOT NULL
                            GROUP BY type
                            ORDER BY count DESC
                            """);
            List<Map<String, Object>> byCity = pg.queryForList(
                    """
                            SELECT city, COUNT(*) as count
                            FROM institutions
                            WHERE city IS NOT NULL
                            GROUP BY city
                            ORDER BY count DESC
                            LIMIT 10
                            """);
            List<Map<String, Object>> bySource = pg.queryForList(
                    """
                            SELECT data_source, COUNT(*) as count
                            FROM institutions
                            WHERE data_source IS NOT NULL
                            GROUP BY data_source
                            ORDER BY count DESC
                            """);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("total", totalVal);
            result.put("with_location", withLoc);
            result.put("without_location", totalVal - withLoc);
            result.put("by_type", mapCountPairs(byType, "type", "type"));
            result.put("by_city", mapCountPairs(byCity, "city", "city"));
            result.put("by_source", mapCountPairs(bySource, "data_source", "source"));
            return result;
        } catch (Exception ex) {
            log.warn("机构统计失败: {}", ex.getMessage());
            return empty;
        }
    }

    public Map<String, Object> getCityCoordinates() {
        try {
            List<Map<String, Object>> rows = pg.queryForList(
                    """
                            SELECT city,
                                   ROUND(AVG(longitude)::numeric, 6) as longitude,
                                   ROUND(AVG(latitude)::numeric, 6) as latitude
                            FROM institutions
                            WHERE city IS NOT NULL AND city != ''
                              AND longitude IS NOT NULL
                              AND latitude IS NOT NULL
                            GROUP BY city
                            ORDER BY city
                            """);
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map<String, Object> r : rows) {
                Object city = r.get("city");
                if (city == null) {
                    continue;
                }
                result.put(String.valueOf(city), List.of(
                        toDouble(r.get("longitude")),
                        toDouble(r.get("latitude"))));
            }
            return result;
        } catch (Exception ex) {
            log.warn("城市坐标查询失败: {}", ex.getMessage());
            return Map.of();
        }
    }

    // ==================== 热线 ====================

    public List<Map<String, Object>> getHotlines(
            String region, String city, String province, String hotlineType) {
        StringBuilder where = new StringBuilder();
        List<Object> params = new ArrayList<>();
        if (region != null && !region.isBlank()) {
            appendAnd(where);
            where.append("region = ?");
            params.add(region);
        }
        if (city != null && !city.isBlank()) {
            appendAnd(where);
            where.append("(city = ? OR province = ?)");
            params.add(city);
            params.add(city);
        }
        if (province != null && !province.isBlank()) {
            appendAnd(where);
            where.append("(province = ? OR city LIKE ? || '%')");
            params.add(province);
            params.add(province);
        }
        if (hotlineType != null && !hotlineType.isBlank()) {
            appendAnd(where);
            where.append("hotline_type = ?");
            params.add(hotlineType);
        }
        String whereSql = where.isEmpty() ? "" : " WHERE " + where;
        try {
            List<Map<String, Object>> rows = params.isEmpty()
                    ? pg.queryForList("SELECT * FROM hotlines ORDER BY region, id")
                    : pg.queryForList(
                            "SELECT * FROM hotlines" + whereSql + " ORDER BY region, id",
                            params.toArray());
            return normalizeRows(rows);
        } catch (Exception ex) {
            log.warn("查询热线列表失败: {}", ex.getMessage());
            return List.of();
        }
    }

    public List<Map<String, Object>> getNationalHotlines() {
        try {
            return normalizeRows(pg.queryForList(
                    "SELECT * FROM hotlines WHERE region = '全国' ORDER BY id"));
        } catch (Exception ex) {
            log.warn("查询全国热线失败: {}", ex.getMessage());
            return List.of();
        }
    }

    public List<Map<String, Object>> getLocalHotlines(String city) {
        if (city == null || city.isBlank()) {
            return List.of();
        }
        try {
            return normalizeRows(pg.queryForList(
                    "SELECT * FROM hotlines WHERE city = ? OR province = ? ORDER BY id LIMIT 10",
                    city,
                    city));
        } catch (Exception ex) {
            log.warn("查询本地热线失败: {}", ex.getMessage());
            return List.of();
        }
    }

    public Map<String, Object> getHotlineById(int hotlineId) {
        try {
            List<Map<String, Object>> rows = pg.queryForList(
                    "SELECT * FROM hotlines WHERE id = ?", hotlineId);
            return rows.isEmpty() ? null : normalizeRow(rows.get(0));
        } catch (Exception ex) {
            log.warn("按 ID 查询热线失败: {}", ex.getMessage());
            return null;
        }
    }

    public Map<String, Object> getHotlineByNumber(String hotline) {
        try {
            List<Map<String, Object>> rows = pg.queryForList(
                    "SELECT * FROM hotlines WHERE hotline = ?", hotline);
            return rows.isEmpty() ? null : normalizeRow(rows.get(0));
        } catch (Exception ex) {
            log.warn("按号码查询热线失败: {}", ex.getMessage());
            return null;
        }
    }

    public List<Map<String, Object>> getRegions() {
        try {
            List<Map<String, Object>> rows = pg.queryForList(
                    """
                            SELECT region, COUNT(*) as count
                            FROM hotlines
                            GROUP BY region
                            ORDER BY
                                CASE region
                                    WHEN '全国' THEN 1
                                    WHEN '华北' THEN 2
                                    WHEN '华东' THEN 3
                                    WHEN '华中' THEN 4
                                    WHEN '华南' THEN 5
                                    WHEN '西南' THEN 6
                                    WHEN '西北' THEN 7
                                    ELSE 8
                                END
                            """);
            List<Map<String, Object>> result = new ArrayList<>();
            for (Map<String, Object> r : rows) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("region", r.get("region"));
                item.put("count", toLong(r.get("count")));
                result.add(item);
            }
            return result;
        } catch (Exception ex) {
            log.warn("查询区域列表失败: {}", ex.getMessage());
            return List.of();
        }
    }

    public Map<String, Object> getHotlineStatistics() {
        Map<String, Object> empty = emptyHotlineStats();
        try {
            Long total = pg.queryForObject("SELECT COUNT(*) FROM hotlines", Long.class);
            long totalVal = total == null ? 0L : total;
            Long national = pg.queryForObject(
                    "SELECT COUNT(*) FROM hotlines WHERE region = '全国'", Long.class);
            long nationalVal = national == null ? 0L : national;

            List<Map<String, Object>> regions = pg.queryForList(
                    """
                            SELECT region, COUNT(*) as count
                            FROM hotlines
                            GROUP BY region
                            ORDER BY count DESC
                            """);
            List<Map<String, Object>> sources = pg.queryForList(
                    """
                            SELECT source, COUNT(*) as count
                            FROM hotlines
                            WHERE source IS NOT NULL
                            GROUP BY source
                            ORDER BY count DESC
                            """);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("total", totalVal);
            result.put("national", nationalVal);
            result.put("regional", totalVal - nationalVal);
            result.put("by_region", mapCountPairs(regions, "region", "region"));
            result.put("by_source", mapCountPairs(sources, "source", "source"));
            return result;
        } catch (Exception ex) {
            log.warn("热线统计失败: {}", ex.getMessage());
            return empty;
        }
    }

    // ==================== helpers ====================

    private static void appendEq(StringBuilder where, List<Object> params, String column, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        if (!where.isEmpty()) {
            where.append(" AND ");
        }
        where.append(column).append(" = ?");
        params.add(value);
    }

    private static void appendAnd(StringBuilder where) {
        if (!where.isEmpty()) {
            where.append(" AND ");
        }
    }

    private static Object[] appendArgs(List<Object> params, Object... extra) {
        List<Object> all = new ArrayList<>(params);
        Collections.addAll(all, extra);
        return all.toArray();
    }

    private List<Map<String, Object>> normalizeRows(List<Map<String, Object>> rows) {
        List<Map<String, Object>> result = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            result.add(normalizeRow(row));
        }
        return result;
    }

    private Map<String, Object> normalizeRow(Map<String, Object> row) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : row.entrySet()) {
            out.put(e.getKey(), e.getValue());
        }
        return out;
    }

    private List<Map<String, Object>> mapCountPairs(
            List<Map<String, Object>> rows, String srcKey, String dstKey) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put(dstKey, r.get(srcKey));
            item.put("count", toLong(r.get("count")));
            result.add(item);
        }
        return result;
    }

    private Map<String, Object> emptyInstitutionStats() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", 0);
        result.put("with_location", 0);
        result.put("without_location", 0);
        result.put("by_type", List.of());
        result.put("by_city", List.of());
        result.put("by_source", List.of());
        return result;
    }

    private Map<String, Object> emptyHotlineStats() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", 0);
        result.put("national", 0);
        result.put("regional", 0);
        result.put("by_region", List.of());
        result.put("by_source", List.of());
        return result;
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

    private double toDouble(Object value) {
        if (value == null) {
            return 0.0;
        }
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return 0.0;
        }
    }
}
