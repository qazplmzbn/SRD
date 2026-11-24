package com.vis4srd.personal;

import com.vis4srd.common.exception.BusinessException;
import com.vis4srd.common.result.ApiResponse;
import com.vis4srd.common.security.SecurityUtils;
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
            throw new BusinessException(HttpStatus.FORBIDDEN, "仅管理员可管理用户角色");
    }
    @GetMapping
    public ApiResponse<?> list(@RequestParam(defaultValue="") String query, @RequestParam(defaultValue="1") int page,
                               @RequestParam(defaultValue="mobile") String group) {
        admin();
        if (page < 1 || query.length() > 100) throw new BusinessException(HttpStatus.BAD_REQUEST, "查询参数无效");
        String match = "%" + query.strip() + "%";
        String where = switch (group) {
            case "mobile" -> " WHERE role='user' AND username LIKE ?";
            case "professional" -> " WHERE role IN ('researcher','doctor','admin') AND username LIKE ?";
            default -> throw new BusinessException(HttpStatus.BAD_REQUEST, "未知账号类别");
        };
        var items = jdbc.queryForList("SELECT id, username, role, is_active AS active FROM app_users" + where + " ORDER BY id DESC LIMIT 20 OFFSET ?", match, ((long)page-1)*20);
        var total = jdbc.queryForObject("SELECT COUNT(*) FROM app_users" + where, Long.class, match);
        return ApiResponse.ok(Map.of("items", items, "total", total, "page", page));
    }
    public record RoleChange(String currentRole, String role) {}
    public record StatusChange(Boolean active) {}

    @PutMapping("/{id}/status")
    public ApiResponse<?> changeStatus(@PathVariable long id, @RequestBody StatusChange change) {
        admin();
        if (change.active() == null) throw new BusinessException(HttpStatus.BAD_REQUEST, "请选择账号状态");
        if (jdbc.update("UPDATE app_users SET is_active=? WHERE id=? AND role='user' AND is_active=?",
                change.active(), id, !change.active()) != 1)
            throw new BusinessException(HttpStatus.CONFLICT, "普通账号状态已变化，请刷新后重试");
        return ApiResponse.ok(true);
    }

    @PutMapping("/{id}/role")
    public ApiResponse<?> changeRole(@PathVariable long id, @RequestBody RoleChange change) {
        admin();
        var roles = java.util.Set.of("researcher", "admin");
        if (change.role()==null || change.currentRole()==null || !roles.contains(change.role()) || !roles.contains(change.currentRole()))
            throw new BusinessException(HttpStatus.BAD_REQUEST, "仅可调整专业账号的管理权限，医生身份请通过认证审核处理");
        if (SecurityUtils.requireCurrentUser().getUserId()==id)
            throw new BusinessException(HttpStatus.CONFLICT, "请由其他管理员调整本账号角色");
        if (jdbc.update("UPDATE app_users SET role=? WHERE id=? AND role=?", change.role(), id, change.currentRole()) != 1)
            throw new BusinessException(HttpStatus.CONFLICT, "账号角色已变化，请刷新后重试");
        return ApiResponse.ok(true);
    }
}

