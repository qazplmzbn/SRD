package com.vis4srd.system.service;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.lang.management.ManagementFactory;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class HealthService {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final JdbcTemplate mysqlJdbcTemplate;
    private final JdbcTemplate postgresJdbcTemplate;
    private final DataSource mysqlDataSource;
    private final DataSource postgresDataSource;

    public HealthService(
            @Qualifier("mysqlJdbcTemplate") JdbcTemplate mysqlJdbcTemplate,
            @Qualifier("postgresJdbcTemplate") JdbcTemplate postgresJdbcTemplate,
            @Qualifier("mysqlDataSource") DataSource mysqlDataSource,
            @Qualifier("postgresDataSource") DataSource postgresDataSource) {
        this.mysqlJdbcTemplate = mysqlJdbcTemplate;
        this.postgresJdbcTemplate = postgresJdbcTemplate;
        this.mysqlDataSource = mysqlDataSource;
        this.postgresDataSource = postgresDataSource;
    }

    public Map<String, Object> basic() {
        boolean mysqlOk = ping(mysqlJdbcTemplate);
        boolean postgresOk = ping(postgresJdbcTemplate);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", mysqlOk ? "ok" : "degraded");
        body.put("timestamp", LocalDateTime.now().format(TS));
        body.put("version", "1.0.0");
        body.put("database", Map.of("mysql", mysqlOk, "postgres", postgresOk));
        return body;
    }

    public Map<String, Object> detailed() {
        Map<String, Object> mysql = dbStatus("mysql", mysqlJdbcTemplate, mysqlDataSource);
        Map<String, Object> postgres = dbStatus("postgres", postgresJdbcTemplate, postgresDataSource);

        String overall = "ok";
        if (!Boolean.TRUE.equals(mysql.get("healthy"))) {
            overall = "degraded";
        }
        if (!Boolean.TRUE.equals(postgres.get("healthy"))) {
            overall = "ok".equals(overall) ? "degraded" : "critical";
        }

        Runtime rt = Runtime.getRuntime();
        double rssMb = (rt.totalMemory() - rt.freeMemory()) / 1024.0 / 1024.0;
        long uptime = ManagementFactory.getRuntimeMXBean().getUptime() / 1000;

        Map<String, Object> agentPool = new LinkedHashMap<>();
        agentPool.put("size", 1);
        agentPool.put("max_size", 1);
        agentPool.put("healthy", true);
        agentPool.put("backend", "java");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", overall);
        body.put("timestamp", LocalDateTime.now().format(TS));
        body.put("version", "1.0.0");
        body.put("database", Map.of("mysql", mysql, "postgres", postgres));
        body.put("agent_pool", agentPool);
        body.put("memory_usage", Map.of("rss_mb", Math.round(rssMb * 100.0) / 100.0, "percent", 0.0));
        body.put("uptime_seconds", uptime);
        return body;
    }

    public Map<String, Object> reconnect() {
        boolean mysql = evict(mysqlDataSource) && ping(mysqlJdbcTemplate);
        boolean postgres = evict(postgresDataSource) && ping(postgresJdbcTemplate);
        boolean overall = mysql || postgres;

        Map<String, Object> results = new LinkedHashMap<>();
        results.put("mysql", Map.of("success", mysql, "message", mysql ? "重连成功" : "重连失败"));
        results.put("postgres", Map.of("success", postgres, "message", postgres ? "重连成功" : "重连失败"));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", overall ? "success" : "failed");
        body.put("message", overall ? "部分数据库重连成功" : "所有数据库重连失败");
        body.put("results", results);
        return body;
    }

    private Map<String, Object> dbStatus(String name, JdbcTemplate jdbc, DataSource ds) {
        boolean healthy = ping(jdbc);
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("healthy", healthy);
        info.put("name", name);
        if (ds instanceof HikariDataSource hikari) {
            info.put("active", hikari.getHikariPoolMXBean() == null ? 0 : hikari.getHikariPoolMXBean().getActiveConnections());
            info.put("idle", hikari.getHikariPoolMXBean() == null ? 0 : hikari.getHikariPoolMXBean().getIdleConnections());
        }
        return info;
    }

    private boolean ping(JdbcTemplate jdbc) {
        try {
            Integer one = jdbc.queryForObject("SELECT 1", Integer.class);
            return one != null && one == 1;
        } catch (Exception ex) {
            return false;
        }
    }

    private boolean evict(DataSource ds) {
        try {
            if (ds instanceof HikariDataSource hikari && hikari.getHikariPoolMXBean() != null) {
                hikari.getHikariPoolMXBean().softEvictConnections();
            }
            return true;
        } catch (Exception ex) {
            return false;
        }
    }
}
