package com.vis4srd.research;

import com.vis4srd.common.exception.BusinessException;
import com.vis4srd.common.result.ApiResponse;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.vis4srd.dataset.util.DatasetTextAnalysis;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/research/archives")
public class ResearchArchiveController {
    private final JdbcTemplate jdbc;
    private final ResearchVersionService versions;

    public ResearchArchiveController(@Qualifier("mysqlJdbcTemplate") JdbcTemplate jdbc, ResearchVersionService versions) {
        this.jdbc = jdbc;
        this.versions = versions;
    }

    @GetMapping
    public ApiResponse<?> list() {
        return ApiResponse.ok(jdbc.queryForList("SELECT kind,source_id AS id,name FROM research_archive_sources WHERE owner_id=? ORDER BY created_at DESC,name", versions.actor()));
    }

    @PutMapping("/{kind}/{id}")
    @Transactional(transactionManager="mysqlTransactionManager")
    public ApiResponse<?> generate(@PathVariable String kind, @PathVariable String id, @RequestParam(required=false) Integer subjectColumn) {
        long owner = versions.actor();
        List<String> names = switch (kind) {
            case "platform" -> jdbc.queryForList("SELECT display_name FROM dataset_profile WHERE dataset_key=? AND is_builtin=true AND is_active=true", String.class, id);
            case "version" -> jdbc.queryForList("SELECT CONCAT(name,' · ',version_name) FROM research_versions WHERE id=? AND owner_id=?", String.class, id, owner);
            default -> List.of();
        };
        if (names.isEmpty()) throw new BusinessException(HttpStatus.NOT_FOUND,"数据集不存在或不属于可归档范围");
        jdbc.update("INSERT INTO research_archive_sources(owner_id,kind,source_id,name) VALUES(?,?,?,?) ON DUPLICATE KEY UPDATE name=VALUES(name)", owner,kind,id,names.get(0));
        if (kind.equals("version")) {
            Map<String,ArrayNode> subjects = new LinkedHashMap<>();
            var source=versions.detail(id);
            if(source.containsKey("table")) collectTable(subjects,(JsonNode)source.get("table"),subjectColumn);
            else for (JsonNode sample : (JsonNode)source.get("samples")) {
                var post = sample.deepCopy();
                ((com.fasterxml.jackson.databind.node.ObjectNode)post).put("importance",DatasetTextAnalysis.calculateImportanceScore(sample.path("text").asText()));
                subjects.computeIfAbsent(sample.path("subjectId").asText().strip(), k -> JsonNodeFactory.instance.arrayNode()).add(post);
            }
            jdbc.update("DELETE FROM research_archive_subjects WHERE owner_id=? AND version_id=?",owner,id);
            subjects.forEach((subject,posts)->jdbc.update("INSERT INTO research_archive_subjects(owner_id,version_id,subject,posts_json) VALUES(?,?,?,?)",owner,id,subject,posts.toString()));
        }
        return ApiResponse.ok(null);
    }

    @GetMapping("/version/{id}")
    public ApiResponse<?> subjects(@PathVariable String id) {
        var mapper=new com.fasterxml.jackson.databind.ObjectMapper();
        return ApiResponse.ok(jdbc.query("SELECT subject,posts_json FROM research_archive_subjects WHERE owner_id=? AND version_id=? ORDER BY subject", (rs,i)->{
            try {return Map.of("subject",rs.getString(1),"posts",mapper.readTree(rs.getString(2)));}
            catch(java.io.IOException e){throw new IllegalStateException("档案读取失败",e);}
        },versions.actor(),id));
    }

    private void collectTable(Map<String,ArrayNode> subjects, JsonNode table, Integer subjectColumn) {
        var posts = table.path("postColumns");
        if (!table.path("labelColumn").isInt() || !posts.isArray() || posts.isEmpty())
            throw new BusinessException(HttpStatus.BAD_REQUEST,"此版本没有列映射，请重新导入并指定标签和贴文列");
        int label = table.get("labelColumn").intValue();
        if (subjectColumn != null && (subjectColumn < 0 || subjectColumn >= table.path("columns").size() || subjectColumn == label))
            throw new BusinessException(HttpStatus.BAD_REQUEST,"账号列无效");
        for (var col : posts) if (subjectColumn != null && subjectColumn == col.intValue())
            throw new BusinessException(HttpStatus.BAD_REQUEST,"账号列不能与贴文列相同");
        int n = 0;
        for (var row : table.path("rows")) {
            n++;
            String subject = subjectColumn == null ? "row-"+n : row.path(subjectColumn).asText("").strip();
            for (var col : posts) {
                String text = row.path(col.intValue()).asText("");
                if (text.isBlank()) continue;
                if (subject.isEmpty() || subject.length()>100) throw new BusinessException(HttpStatus.BAD_REQUEST,"第 "+n+" 行账号为空或超过 100 字符，请检查账号列或按行建档");
                var post = JsonNodeFactory.instance.objectNode().put("text",text).put("label",row.path(label).asText(""))
                    .put("importance",DatasetTextAnalysis.calculateImportanceScore(text)).put("sourceRow",n).put("sourceColumn",col.intValue());
                subjects.computeIfAbsent(subject,k->JsonNodeFactory.instance.arrayNode()).add(post);
            }
        }
        if (subjects.isEmpty()) throw new BusinessException(HttpStatus.BAD_REQUEST,"所选贴文列没有非空正文");
    }

    @DeleteMapping("/{kind}/{id}")
    @Transactional(transactionManager="mysqlTransactionManager")
    public ApiResponse<?> remove(@PathVariable String kind, @PathVariable String id) {
        jdbc.update("DELETE FROM research_archive_sources WHERE owner_id=? AND kind=? AND source_id=?",versions.actor(),kind,id);
        if(kind.equals("version")) jdbc.update("DELETE FROM research_archive_subjects WHERE owner_id=? AND version_id=?",versions.actor(),id);
        return ApiResponse.ok(null);
    }
}