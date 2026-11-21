package com.vis4srd.verification;

import com.vis4srd.common.exception.BusinessException;
import com.vis4srd.common.security.AuthUser;
import com.vis4srd.common.security.SecurityUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

@Service
public class DoctorVerificationService {
    private final JdbcTemplate jdbc;
    public DoctorVerificationService(@Qualifier("mysqlJdbcTemplate") JdbcTemplate jdbc) { this.jdbc = jdbc; }
    private static final String FIELDS = "id, applicant_id, real_name, institution, qualification, credential_number, notes, status, submitted_at, reviewed_at, reviewer_id, review_reason";
    private static final Set<String> STATUSES = Set.of("ALL", "PENDING", "APPROVED", "REJECTED", "REVOKED");
    public record Proof(String type, byte[] content) {}

    private AuthUser actor() {
        var actor = SecurityUtils.requireCurrentUser();
        if (actor.getUserId() == null) throw new BusinessException(HttpStatus.UNAUTHORIZED, "请重新登录");
        return actor;
    }
    private AuthUser admin() {
        var actor = actor();
        if (!"admin".equals(actor.getRole())) throw new BusinessException(HttpStatus.FORBIDDEN, "需要管理员审核权限");
        return actor;
    }
    private List<Map<String,Object>> rows(String sql, Object... args) {
        return jdbc.query(sql, (rs, n) -> {
            Map<String,Object> row = new LinkedHashMap<>();
            row.put("id", rs.getString("id")); row.put("applicantId", rs.getLong("applicant_id"));
            row.put("realName", rs.getString("real_name")); row.put("institution", rs.getString("institution"));
            row.put("qualification", rs.getString("qualification")); row.put("credentialNumber", rs.getString("credential_number"));
            row.put("notes", rs.getString("notes")); row.put("status", rs.getString("status"));
            row.put("submittedAt", rs.getTimestamp("submitted_at").toInstant().toString());
            row.put("reviewedAt", rs.getTimestamp("reviewed_at") == null ? null : rs.getTimestamp("reviewed_at").toInstant().toString());
            row.put("reviewerId", rs.getObject("reviewer_id")); row.put("reviewReason", rs.getString("review_reason"));
            return row;
        }, args);
    }
    public List<Map<String,Object>> mine() {
        return rows("SELECT " + FIELDS + " FROM doctor_applications WHERE applicant_id=? ORDER BY submitted_at DESC, id DESC", actor().getUserId());
    }
    public Map<String,Object> list(int page, String status) {
        admin();
        if (page < 1 || !STATUSES.contains(status)) throw new BusinessException(HttpStatus.BAD_REQUEST, "分页或状态参数错误");
        String condition = status.equals("ALL") ? "" : " WHERE status=?";
        List<Object> params = new ArrayList<>(); if (!status.equals("ALL")) params.add(status);
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM doctor_applications" + condition, Long.class, params.toArray());
        params.add(20); params.add(((long)page - 1) * 20);
        return Map.of("items", rows("SELECT " + FIELDS + " FROM doctor_applications" + condition + " ORDER BY submitted_at DESC, id DESC LIMIT ? OFFSET ?", params.toArray()), "total", count == null ? 0 : count);
    }

    @Transactional(transactionManager="mysqlTransactionManager")
    public String submit(DoctorVerificationController.ApplicationInput input, MultipartFile file) {
        var actor = actor();
        // Lock the account first for both submissions and decisions to prevent parallel pending applications.
        String role = lockRole(actor.getUserId());
        if (!role.equals("researcher")) throw new BusinessException(HttpStatus.FORBIDDEN, "仅研究者可提交医生认证申请");
        Long active = jdbc.queryForObject("SELECT COUNT(*) FROM doctor_applications WHERE applicant_id=? AND status IN ('PENDING','APPROVED')", Long.class, actor.getUserId());
        if (active != null && active > 0) throw new BusinessException(HttpStatus.CONFLICT, "已有待审核或已通过的申请");
        if (file.isEmpty() || file.getSize() > 5 * 1024 * 1024) throw new BusinessException(HttpStatus.BAD_REQUEST, "证明材料须为不超过5MB的PDF、PNG或JPEG文件");
        byte[] bytes;
        try { bytes = file.getBytes(); } catch (IOException e) { throw new BusinessException(HttpStatus.BAD_REQUEST, "无法读取证明材料"); }
        String type = detectType(bytes);
        String id = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO doctor_applications (id,applicant_id,real_name,institution,qualification,credential_number,notes,status,submitted_at,proof_type,proof_content,consent_version) VALUES (?,?,?,?,?,?,?,'PENDING',?,?,?,?)",
                id, actor.getUserId(), input.realName().strip(), input.institution().strip(), input.qualification().strip(), input.credentialNumber().strip(), input.notes().strip(), Timestamp.from(Instant.now()), type, bytes, "doctor-review-v1");
        event(id, actor.getUserId(), "SUBMIT", "");
        return id;
    }

    @Transactional(transactionManager="mysqlTransactionManager")
    public void review(String id, DoctorVerificationController.ReviewInput input) {
        var actor = admin();
        var owners = jdbc.queryForList("SELECT applicant_id FROM doctor_applications WHERE id=?", Long.class, id);
        if (owners.isEmpty()) throw new BusinessException(HttpStatus.NOT_FOUND, "申请不存在");
        long owner = owners.get(0);
        if (owner == actor.getUserId()) throw new BusinessException(HttpStatus.FORBIDDEN, "不能审核自己的申请");
        String role = lockRole(owner);
        String status = jdbc.queryForObject("SELECT status FROM doctor_applications WHERE id=? FOR UPDATE", String.class, id);
        String next;
        if (input.decision().equals("REVOKE")) {
            if (!"APPROVED".equals(status) || !role.equals("doctor")) throw new BusinessException(HttpStatus.CONFLICT, "仅可撤销有效的医生认证");
            next = "REVOKED";
            jdbc.update("UPDATE app_users SET role='researcher' WHERE id=? AND role='doctor'", owner);
        } else {
            if (!"PENDING".equals(status) || !role.equals("researcher")) throw new BusinessException(HttpStatus.CONFLICT, "申请状态已变化，请刷新后处理");
            next = input.decision().equals("APPROVE") ? "APPROVED" : "REJECTED";
            if (next.equals("APPROVED")) jdbc.update("UPDATE app_users SET role='doctor' WHERE id=? AND role='researcher'", owner);
        }
        jdbc.update("UPDATE doctor_applications SET status=?,reviewed_at=?,reviewer_id=?,review_reason=? WHERE id=?",
                next, Timestamp.from(Instant.now()), actor.getUserId(), input.reason().strip(), id);
        event(id, actor.getUserId(), input.decision(), input.reason().strip());
    }

    @Transactional(transactionManager="mysqlTransactionManager")
    public Proof proof(String id) {
        var actor = actor();
        var proofs = jdbc.query("SELECT proof_type, proof_content FROM doctor_applications WHERE id=? AND (applicant_id=? OR ?='admin')",
                (rs, n) -> new Proof(rs.getString(1), rs.getBytes(2)), id, actor.getUserId(), actor.getRole());
        if (proofs.isEmpty()) throw new BusinessException(HttpStatus.NOT_FOUND, "材料不存在或无权访问");
        event(id, actor.getUserId(), "DOWNLOAD_PROOF", "");
        return proofs.get(0);
    }

    private String lockRole(long id) {
        var roles = jdbc.queryForList("SELECT role FROM app_users WHERE id=? AND is_active=true FOR UPDATE", String.class, id);
        if (roles.isEmpty()) throw new BusinessException(HttpStatus.CONFLICT, "账号不存在或已停用");
        return roles.get(0);
    }
    private void event(String id, long actor, String action, String reason) {
        jdbc.update("INSERT INTO doctor_application_events (id,application_id,actor_id,action,reason,created_at) VALUES (?,?,?,?,?,?)",
                UUID.randomUUID().toString(), id, actor, action, reason, Timestamp.from(Instant.now()));
    }
    private String detectType(byte[] b) {
        if (b.length >= 5 && b[0]=='%' && b[1]=='P' && b[2]=='D' && b[3]=='F' && b[4]=='-') return "application/pdf";
        if (b.length >= 8 && Arrays.equals(Arrays.copyOf(b, 8), new byte[]{(byte)137,80,78,71,13,10,26,10})) return "image/png";
        if (b.length >= 3 && b[0]==(byte)255 && b[1]==(byte)216 && b[2]==(byte)255) return "image/jpeg";
        throw new BusinessException(HttpStatus.BAD_REQUEST, "材料格式不支持，请使用PDF、PNG或JPEG");
    }
}
