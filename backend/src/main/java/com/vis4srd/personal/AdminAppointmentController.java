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
@RequestMapping("/api/admin/appointments")
public class AdminAppointmentController {
    private final JdbcTemplate db;

    public AdminAppointmentController(@Qualifier("mysqlJdbcTemplate") JdbcTemplate db) {
        this.db = db;
    }

    @GetMapping
    public ApiResponse<?> list(@RequestParam(defaultValue = "1") int page) {
        if (page < 1) throw new BusinessException(HttpStatus.BAD_REQUEST, "无效页码");
        var items = db.queryForList("""
                SELECT a.id, a.state, a.scheduled_at AS scheduledAt,
                       COALESCE(d.nickname,d.username) AS doctor, COALESCE(p.nickname,p.username) AS patient,
                       r.decision, COALESCE(r.opinion,o.opinion) AS opinion
                FROM care_appointments a LEFT JOIN app_users d ON d.id=a.doctor_id
                LEFT JOIN app_users p ON p.id=a.patient_id
                LEFT JOIN care_appointment_reviews r ON r.appointment_id=a.id
                LEFT JOIN admin_appointment_opinions o ON o.appointment_id=a.id
                ORDER BY CASE WHEN a.state='PENDING' AND r.decision IS NULL THEN 0 ELSE 1 END, a.created_at DESC, a.id DESC LIMIT 20 OFFSET ?
                """, (page - 1L) * 20);
        return ApiResponse.ok(Map.of("items", items, "total", db.queryForObject("SELECT COUNT(*) FROM care_appointments", Long.class)));
    }

    public enum Decision { APPROVED, REJECTED }
    public record Opinion(@jakarta.validation.constraints.NotNull Decision decision, @NotBlank @Size(max=1000) String opinion) {}

    @PostMapping("/{id}/review")
    @org.springframework.transaction.annotation.Transactional(transactionManager="mysqlTransactionManager")
    public ApiResponse<?> recordOpinion(@PathVariable String id, @Valid @RequestBody Opinion body) {
        var states = db.queryForList("SELECT state FROM care_appointments WHERE id=? FOR UPDATE", String.class, id);
        if (states.isEmpty()) throw new BusinessException(HttpStatus.NOT_FOUND, "预约不存在");
        if (!"PENDING".equals(states.get(0))) throw new BusinessException(HttpStatus.CONFLICT, "只能审核待处理预约");
        try {
            db.update("INSERT INTO care_appointment_reviews (appointment_id, reviewer_id, decision, opinion) VALUES (?,?,?,?)",
                    id, SecurityUtils.requireCurrentUser().getUserId(), body.decision().name(), body.opinion().strip());
        } catch (org.springframework.dao.DuplicateKeyException ex) {
            throw new BusinessException(HttpStatus.CONFLICT, "该预约已记录处理意见，请刷新列表");
        }
        if (body.decision()==Decision.REJECTED) db.update("UPDATE care_appointments SET state='REJECTED',snapshot=NULL,shared=false WHERE id=?", id);
        return ApiResponse.ok(true);
    }
}