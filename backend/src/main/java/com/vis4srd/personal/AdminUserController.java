package com.vis4srd.personal;

import com.vis4srd.common.exception.BusinessException;
import com.vis4srd.common.result.ApiResponse;
import com.vis4srd.common.security.SecurityUtils;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {
    private final JdbcTemplate jdbc;
    public AdminUserController(@Qualifier("mysqlJdbcTemplate") JdbcTemplate jdbc) { this.jdbc = jdbc; }
    private void admin() {
        if (!"admin".equals(SecurityUtils.requireCurrentUser().getRole()))
            throw new BusinessException(HttpStatus.FORBIDDEN, "仅管理员可管理用户资料");
    }
    private void exists(long id) {
        if (jdbc.queryForObject("SELECT COUNT(*) FROM app_users WHERE id = ?", Long.class, id) == 0)
            throw new BusinessException(HttpStatus.NOT_FOUND, "用户不存在");
    }
    @GetMapping
    public ApiResponse<?> list(@RequestParam(defaultValue="") String query, @RequestParam(defaultValue="1") int page) {
        admin();
        if (page < 1 || query.length() > 100) throw new BusinessException(HttpStatus.BAD_REQUEST, "查询参数无效");
        String match = "%" + query.strip() + "%";
        String where = " WHERE username LIKE ? OR nickname LIKE ?";
        var items = jdbc.queryForList("SELECT id, username, nickname, role, is_active AS active, created_at AS createdAt FROM app_users" + where + " ORDER BY id DESC LIMIT 20 OFFSET ?", match, match, ((long)page-1)*20);
        var total = jdbc.queryForObject("SELECT COUNT(*) FROM app_users" + where, Long.class, match, match);
        return ApiResponse.ok(Map.of("items", items, "total", total, "page", page));
    }
    @GetMapping("/{id}/posts")
    public ApiResponse<?> posts(@PathVariable long id, @RequestParam(defaultValue="1") int page) {
        admin(); exists(id);
        if(page < 1) throw new BusinessException(HttpStatus.BAD_REQUEST,"页码无效");
        var items = jdbc.queryForList("SELECT id, content, source, published_at AS publishedAt, imported_at AS importedAt FROM personal_posts WHERE owner_id = ? ORDER BY imported_at DESC, id DESC LIMIT 10 OFFSET ?", id, ((long)page-1)*10);
        var total = jdbc.queryForObject("SELECT COUNT(*) FROM personal_posts WHERE owner_id = ?", Long.class, id);
        return ApiResponse.ok(Map.of("items",items,"total",total));
    }
}

