package com.vis4srd.personal;

import com.vis4srd.common.exception.BusinessException;
import com.vis4srd.common.security.SecurityUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/** Personal content is deliberately isolated from research archives and inference inputs. */
@Service
public class PersonalPostService {
    private final JdbcTemplate jdbc;
    public PersonalPostService(@Qualifier("mysqlJdbcTemplate") JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public record PostView(String id, String content, String source, Instant publishedAt, Instant importedAt) {}
    public record PostPage(List<PostView> items, long total, int page, int pageSize) {}
    public record ImportResult(int imported, int skipped) {}

    @Transactional(transactionManager = "mysqlTransactionManager")
    public ImportResult importPosts(List<PersonalPostController.PostInput> inputs) {
        long owner = owner();
        int imported = 0;
        for (var input : inputs) {
            String content = input.content().strip().replace("\r\n", "\n").replace('\r', '\n');
            String source = input.source().strip();
            // Database stores seconds; normalize before hashing so retries remain idempotent.
            Instant published = input.publishedAt() == null ? null : input.publishedAt().truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
            String hash = hash(source.length() + ":" + source + ":" + published + ":" + content);
            try {
                jdbc.update("INSERT INTO personal_posts (id, owner_id, content, source, published_at, imported_at, content_hash) VALUES (?, ?, ?, ?, ?, ?, ?)",
                        UUID.randomUUID().toString(), owner, content, source,
                        published == null ? null : Timestamp.from(published), Timestamp.from(Instant.now()), hash);
                imported++;
            } catch (DuplicateKeyException ignored) {
                // The same owner's identical source/time/content is retained once.
            }
        }
        return new ImportResult(imported, inputs.size() - imported);
    }

    public PostPage list(int page, int pageSize) {
        long owner = owner();
        if (page < 1 || pageSize < 1 || pageSize > 100) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "页码须大于0，每页数量须为1至100");
        }
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM personal_posts WHERE owner_id = ?", Long.class, owner);
        var items = jdbc.query("SELECT id, content, source, published_at, imported_at FROM personal_posts WHERE owner_id = ? ORDER BY imported_at DESC, id DESC LIMIT ? OFFSET ?",
                (rs, row) -> new PostView(rs.getString("id"), rs.getString("content"), rs.getString("source"),
                        rs.getTimestamp("published_at") == null ? null : rs.getTimestamp("published_at").toInstant(),
                        rs.getTimestamp("imported_at").toInstant()), owner, pageSize, ((long) page - 1) * pageSize);
        return new PostPage(items, total == null ? 0 : total, page, pageSize);
    }

    public void delete(String id) {
        if (jdbc.update("DELETE FROM personal_posts WHERE id = ? AND owner_id = ?", id, owner()) == 0) {
            // Do not reveal whether another account owns this ID.
            throw new BusinessException(HttpStatus.NOT_FOUND, "贴文不存在或已删除");
        }
    }

    private long owner() {
        Long id = SecurityUtils.requireCurrentUser().getUserId();
        if (id == null) throw new BusinessException(HttpStatus.UNAUTHORIZED, "请重新登录");
        return id;
    }

    private String hash(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }
}
