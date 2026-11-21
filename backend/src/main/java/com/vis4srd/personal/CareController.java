package com.vis4srd.personal;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vis4srd.common.exception.BusinessException;
import com.vis4srd.common.result.ApiResponse;
import com.vis4srd.common.security.AuthUser;
import com.vis4srd.common.security.SecurityUtils;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

@RestController
@RequestMapping("/api/care")
public class CareController {
    private final JdbcTemplate db;
    private final ObjectMapper json;
    private final String invite;
    @Value("${app.python-inference.base-url:http://127.0.0.1:8001}") private String inferenceBase;
    public CareController(@Qualifier("mysqlJdbcTemplate") JdbcTemplate db, ObjectMapper json,
                          @Value("${DOCTOR_CERTIFICATION_INVITE_CODE:}") String invite) {
        this.db=db; this.json=json; this.invite=invite;
    }
    public record Invite(@NotBlank @Size(max=256) String code) {}
    public record Post(@NotBlank @Size(max=8000) String text, @Size(max=80) String date) {}
    public record Result(@NotBlank @Size(max=200) String name, @NotNull @Min(0) Integer score, @Size(max=80) String date) {}
    public record Snapshot(@NotNull @Size(max=100) List<@NotNull @Valid Post> posts, @NotNull @Size(max=100) List<@NotNull @Valid Result> results) {}
    public record Booking(@Positive long doctorId, @NotNull @Future Instant scheduledAt, boolean consent, @NotNull @Valid Snapshot snapshot) {}
    private AuthUser actor(String... roles) {
        var u=SecurityUtils.requireCurrentUser();
        if(!Arrays.asList(roles).contains(u.getRole())) throw new BusinessException(HttpStatus.FORBIDDEN,"此账号无权执行该操作");
        return u;
    }
    private static BusinessException unavailable() { return new BusinessException(HttpStatus.NOT_FOUND,"预约不存在或当前不可访问"); }
    @PostMapping("/certification") @Transactional(transactionManager="mysqlTransactionManager")
    public ApiResponse<Boolean> certify(@Valid @RequestBody Invite input) {
        var u=actor("researcher","doctor");
        if(invite.isBlank() || !MessageDigest.isEqual(invite.getBytes(StandardCharsets.UTF_8), input.code().getBytes(StandardCharsets.UTF_8)))
            throw new BusinessException(HttpStatus.FORBIDDEN,"医生邀请码无效或认证尚未开放");
        db.update("UPDATE app_users SET role='doctor' WHERE id=? AND role IN ('researcher','doctor')",u.getUserId());
        db.update("INSERT INTO care_doctors(doctor_id) VALUES (?) ON DUPLICATE KEY UPDATE doctor_id=VALUES(doctor_id)",u.getUserId());
        return ApiResponse.ok(true);
    }
    private static final String DIRECTORY=" FROM care_doctors d JOIN app_users u ON d.doctor_id=u.id WHERE u.role='doctor' AND u.is_active=true";
    @GetMapping("/doctors") public ApiResponse<?> doctors() {
        actor("user"); return ApiResponse.ok(db.queryForList("SELECT u.id, COALESCE(u.nickname,u.username) AS name"+DIRECTORY+" ORDER BY u.id"));
    }
    @PostMapping("/appointments") public ApiResponse<String> book(@Valid @RequestBody Booking b) throws Exception {
        var u=actor("user"); String id=UUID.randomUUID().toString();
        boolean hasRecords=!b.snapshot().posts().isEmpty()||!b.snapshot().results().isEmpty();
        if(hasRecords&&!b.consent())throw new BusinessException(HttpStatus.BAD_REQUEST,"共享记录须先确认授权");
        int count=db.update("INSERT INTO care_appointments(id,patient_id,doctor_id,state,shared,scheduled_at,snapshot) SELECT ?,?,u.id,'PENDING',?,?,?"+DIRECTORY+" AND u.id=?",id,u.getUserId(),hasRecords,Timestamp.from(b.scheduledAt()),json.writeValueAsString(b.snapshot()),b.doctorId());
        if(count!=1) throw unavailable(); return ApiResponse.ok(id);
    }
    @GetMapping("/appointments") public ApiResponse<?> list(jakarta.servlet.http.HttpServletResponse response) {
        var u=actor("user","doctor"); boolean patient="user".equals(u.getRole());
        response.setHeader("Cache-Control","no-store");
        return ApiResponse.ok(db.queryForList("SELECT a.id,a.state,a.scheduled_at AS scheduledAt, COALESCE(u.nickname,u.username) AS name, EXISTS(SELECT 1 FROM care_analysis_grants g WHERE g.appointment_id=a.id) AS analysisAllowed"+(patient?", EXISTS(SELECT 1 FROM care_live_grants g WHERE g.appointment_id=a.id AND a.state='ACCEPTED') AS liveSharing":", a.patient_id AS patientId, a.shared AS shared")+" FROM care_appointments a JOIN app_users u ON u.id="+(patient?"a.doctor_id WHERE a.patient_id=?":"a.patient_id WHERE u.role='user' AND a.doctor_id=? AND EXISTS(SELECT 1 FROM care_doctors d WHERE d.doctor_id=a.doctor_id)")+" ORDER BY a.created_at DESC",u.getUserId()));
    }
    @PostMapping("/appointments/{id}/analysis-grant/{enabled}") @Transactional(transactionManager="mysqlTransactionManager")
    public ApiResponse<Boolean> analysisGrant(@PathVariable String id,@PathVariable boolean enabled){
        lockAccepted(id,actor("user").getUserId());if(enabled)db.update("INSERT INTO care_analysis_grants(appointment_id) VALUES (?) ON DUPLICATE KEY UPDATE appointment_id=VALUES(appointment_id)",id);
        else db.update("DELETE FROM care_analysis_grants WHERE appointment_id=?",id);return ApiResponse.ok(true);
    }
    private String reviewInput(String id,long doctor){var rows=db.queryForList("SELECT a.snapshot FROM care_appointments a JOIN care_analysis_grants g ON g.appointment_id=a.id JOIN care_doctors d ON d.doctor_id=a.doctor_id WHERE a.id=? AND a.doctor_id=? AND a.state='ACCEPTED' AND a.shared=true",String.class,id,doctor);if(rows.isEmpty())throw unavailable();return rows.get(0);}
    @PostMapping("/appointments/{id}/review") public ApiResponse<?> review(@PathVariable String id,jakarta.servlet.http.HttpServletResponse response)throws Exception{
        response.setHeader("Cache-Control","no-store");long doctor=actor("doctor").getUserId();String before=reviewInput(id,doctor);
        var posts=json.readValue(before,Snapshot.class).posts().stream().limit(32).toList();if(posts.isEmpty())throw new BusinessException(HttpStatus.BAD_REQUEST,"本次预约没有共享贴文");
        var req=java.net.http.HttpRequest.newBuilder(java.net.URI.create(inferenceBase+"/infer/appointment-review")).timeout(java.time.Duration.ofSeconds(90)).header("Content-Type","application/json").POST(java.net.http.HttpRequest.BodyPublishers.ofString(json.writeValueAsString(Map.of("posts",posts.stream().map(Post::text).toList())))).build();
        java.net.http.HttpResponse<String> result;try{result=java.net.http.HttpClient.newHttpClient().send(req,java.net.http.HttpResponse.BodyHandlers.ofString());}catch(java.io.IOException e){throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE,"本机推理服务不可用");}
        if(result.statusCode()!=200)throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE,"请核对本机编码器和模型权重");
        if(!before.equals(reviewInput(id,doctor)))throw new BusinessException(HttpStatus.CONFLICT,"资料已更新，请重新分析");return ApiResponse.ok(json.readTree(result.body()));
    }
    public record LivePost(@NotBlank @Size(max=20000) String text,@Size(max=200) String date) {}
    public record LiveSnapshot(@NotNull @Size(max=500) List<@NotNull @Valid LivePost> posts,@NotNull @Size(max=100) List<@NotNull @Valid Result> results) {}
    public record LiveUpdate(@NotNull UUID source, @NotNull @Valid LiveSnapshot snapshot) {}
    private void lockAccepted(String id,long patient){
        if(db.queryForList("SELECT id FROM care_appointments WHERE id=? AND patient_id=? AND state='ACCEPTED' FOR UPDATE",String.class,id,patient).isEmpty())throw unavailable();
    }
    @PutMapping("/appointments/{id}/live/{operation}") @Transactional(transactionManager="mysqlTransactionManager")
    public ApiResponse<Boolean> live(@PathVariable String id,@PathVariable String operation,@Valid @RequestBody LiveUpdate input) throws Exception {
        lockAccepted(id,actor("user").getUserId());String source=input.source().toString();
        if(operation.equals("enable"))db.update("INSERT INTO care_live_grants(appointment_id,source_key) VALUES (?,?) ON DUPLICATE KEY UPDATE source_key=VALUES(source_key)",id,source);
        else if(!operation.equals("sync")||db.queryForList("SELECT appointment_id FROM care_live_grants WHERE appointment_id=? AND source_key=?",String.class,id,source).isEmpty())throw unavailable();
        db.update("UPDATE care_appointments SET shared=true,snapshot=? WHERE id=?",json.writeValueAsString(input.snapshot()),id);return ApiResponse.ok(true);
    }
    @DeleteMapping("/appointments/{id}/live") @Transactional(transactionManager="mysqlTransactionManager")
    public ApiResponse<Boolean> stopLive(@PathVariable String id){
        lockAccepted(id,actor("user").getUserId());db.update("DELETE FROM care_live_grants WHERE appointment_id=?",id);
        db.update("UPDATE care_appointments SET shared=false,snapshot=NULL WHERE id=?",id);return ApiResponse.ok(true);
    }
    @PostMapping("/appointments/{id}/{action}") public ApiResponse<Boolean> transition(@PathVariable String id,@PathVariable String action) {
        String sql; AuthUser u;
        switch(action) {
            case "accept" -> {u=actor("doctor"); sql="SET state='ACCEPTED' WHERE id=? AND doctor_id=? AND shared=true AND state='PENDING' AND EXISTS(SELECT 1 FROM care_doctors d WHERE d.doctor_id=care_appointments.doctor_id)";}
            case "cancel" -> {u=actor("user"); sql="SET state='CANCELLED', snapshot=NULL WHERE id=? AND patient_id=? AND state IN ('PENDING','ACCEPTED')";}
            case "end" -> {u=actor("doctor"); sql="SET state='ENDED', snapshot=NULL WHERE id=? AND doctor_id=? AND state='ACCEPTED'";}
            case "reject" -> {u=actor("doctor"); sql="SET state='REJECTED', snapshot=NULL WHERE id=? AND doctor_id=? AND state='PENDING'";}
            default -> throw unavailable();
        }
        if(db.update("UPDATE care_appointments "+sql,id,u.getUserId())!=1) throw unavailable();
        return ApiResponse.ok(true);
    }
    @GetMapping("/appointments/{id}/records") public ApiResponse<?> records(@PathVariable String id, jakarta.servlet.http.HttpServletResponse response) throws Exception {
        var u=actor("doctor"); response.setHeader("Cache-Control","no-store");
        var rows=db.queryForList("SELECT a.snapshot FROM care_appointments a JOIN care_doctors d ON d.doctor_id=a.doctor_id JOIN app_users p ON p.id=a.patient_id AND p.role='user' WHERE a.id=? AND a.doctor_id=? AND a.state='ACCEPTED' AND a.shared=true",String.class,id,u.getUserId());
        if(rows.isEmpty()) throw unavailable(); return ApiResponse.ok(json.readValue(rows.get(0),Snapshot.class));
    }
}