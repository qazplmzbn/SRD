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
    @Autowired private com.vis4srd.risk.service.RiskService riskService;
    @Autowired private com.vis4srd.risk.service.PostReviewService reviews;
    @Value("${CARE_ALLOW_EXTERNAL_MODELS:false}") private boolean allowExternalModels;
    public CareController(@Qualifier("mysqlJdbcTemplate") JdbcTemplate db, ObjectMapper json,
                          @Value("${DOCTOR_CERTIFICATION_INVITE_CODE:}") String invite) {
        this.db=db; this.json=json; this.invite=invite;
    }
    public record Invite(@NotBlank @Size(max=256) String code) {}
    public record Post(@NotBlank @Size(max=8000) String text, @Size(max=80) String date, @Size(max=80) String platform) {}
    public record Result(@NotBlank @Size(max=200) String name, @NotNull @Min(0) Integer score, @Size(max=80) String date) {}
    public record Snapshot(@NotNull @Size(max=100) List<@NotNull @Valid Post> posts, @NotNull @Size(max=100) List<@NotNull @Valid Result> results) {}
    public record Booking(@Positive long doctorId, @NotNull @Future Instant scheduledAt, @NotNull @Valid Snapshot snapshot) {}
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
    @PostMapping("/appointments") @Transactional(transactionManager="mysqlTransactionManager")
    public ApiResponse<String> book(@Valid @RequestBody Booking b) throws Exception {
        var u=actor("user"); String id=UUID.randomUUID().toString();
        // Serialize new bookings for this patient, including their first booking.
        db.queryForObject("SELECT id FROM app_users WHERE id=? FOR UPDATE",Long.class,u.getUserId());
        if(!db.queryForList("SELECT id FROM care_appointments WHERE patient_id=? AND doctor_id<>? AND state IN ('PENDING','ACCEPTED') LIMIT 1 FOR UPDATE",String.class,u.getUserId(),b.doctorId()).isEmpty())
            throw new BusinessException(HttpStatus.CONFLICT,"你与其他医生还有待接受或已接受的预约，结束或取消后才能更换医生");
        int count=db.update("INSERT INTO care_appointments(id,patient_id,doctor_id,state,shared,scheduled_at,snapshot) SELECT ?,?,u.id,'PENDING',true,?,?"+DIRECTORY+" AND u.id=?",id,u.getUserId(),Timestamp.from(b.scheduledAt()),json.writeValueAsString(b.snapshot()),b.doctorId());
        if(count!=1) throw unavailable();
        return ApiResponse.ok(id);
    }
    @GetMapping("/appointments") public ApiResponse<?> list(jakarta.servlet.http.HttpServletResponse response) {
        var u=actor("user","doctor"); boolean patient="user".equals(u.getRole());
        response.setHeader("Cache-Control","no-store");
        return ApiResponse.ok(db.queryForList("SELECT a.id,a.state,(a.state='ACCEPTED') AS shared,a.scheduled_at AS scheduledAt, COALESCE(u.nickname,u.username) AS name"+(patient?"":", a.patient_id AS patientId, CASE WHEN a.state='ACCEPTED' THEN SHA2(a.snapshot,256) END AS snapshotVersion")+" FROM care_appointments a JOIN app_users u ON u.id="+(patient?"a.doctor_id WHERE a.patient_id=?":"a.patient_id WHERE u.role='user' AND a.doctor_id=? AND EXISTS(SELECT 1 FROM care_doctors d WHERE d.doctor_id=a.doctor_id)")+" ORDER BY a.created_at DESC",u.getUserId()));
    }
    private String reviewInput(String id,long doctor){var rows=db.queryForList("SELECT COALESCE(a.snapshot,JSON_OBJECT('posts',JSON_ARRAY(),'results',JSON_ARRAY())) FROM care_appointments a JOIN care_doctors d ON d.doctor_id=a.doctor_id JOIN app_users p ON p.id=a.patient_id AND p.role='user' WHERE a.id=? AND a.doctor_id=? AND a.state='ACCEPTED'",String.class,id,doctor);if(rows.isEmpty())throw unavailable();return rows.get(0);}
    public record ReviewSelection(@Positive long modelId, Long templateId,
                                  @NotBlank @Pattern(regexp="api|emocc", message="未知模型类别") String category,
                                  @NotBlank @Pattern(regexp="(?i)[a-f0-9]{64}") String snapshotVersion) {}
    @GetMapping("/review-policy") public ApiResponse<?> reviewPolicy() {
        actor("doctor");
        return ApiResponse.ok(Map.of("allowExternalModels", allowExternalModels));
    }
    @PostMapping("/appointments/{id}/review") public ApiResponse<?> review(@PathVariable String id,@Valid @RequestBody ReviewSelection selection,jakarta.servlet.http.HttpServletResponse response)throws Exception{
        response.setHeader("Cache-Control","no-store");long doctor=actor("doctor").getUserId();String before=reviewInput(id,doctor);
        String version=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(before.getBytes(StandardCharsets.UTF_8)));
        if(!version.equalsIgnoreCase(selection.snapshotVersion()))throw new BusinessException(HttpStatus.CONFLICT,"资料已更新，请重新创建任务");
        var snapshot=json.readValue(before,Snapshot.class);
        boolean detection="emocc".equals(selection.category());
        var posts=snapshot.posts().stream().limit(detection?32:20).toList();
        if(posts.isEmpty())throw new BusinessException(HttpStatus.BAD_REQUEST,"本次预约没有共享贴文");
        long start=System.nanoTime();
        var texts=posts.stream().map(Post::text).toList();
        var output=detection
                ? reviewEmocc(selection.modelId(),texts)
                : json.<com.fasterxml.jackson.databind.node.ObjectNode>valueToTree(riskService.analyzeAppointment(texts,selection.modelId(),
                        selection.templateId()==null?0:selection.templateId(),selection.category(),allowExternalModels));
        if(!before.equals(reviewInput(id,doctor)))throw new BusinessException(HttpStatus.CONFLICT,"资料已更新，请重新分析");
        output.set("posts",json.valueToTree(posts));output.put("total",snapshot.posts().size());
        output.put("postCount",posts.size());output.put("snapshotVersion",version);
        output.put("processingTimeMs",(System.nanoTime()-start)/1_000_000);
        return ApiResponse.ok(output);
    }

    private com.fasterxml.jackson.databind.node.ObjectNode reviewEmocc(long modelId,List<String> texts)throws Exception {
        var detector=riskService.requireAppointmentDetector(modelId);
        return json.valueToTree(reviews.review(texts,detector.family(),detector.name(),allowExternalModels));
    }
    @PutMapping("/appointments/{id}/live/sync")
    public ApiResponse<Boolean> sync(@PathVariable String id,@Valid @RequestBody Snapshot snapshot) throws Exception {
        long patient=actor("user").getUserId();
        if(db.update("UPDATE care_appointments SET shared=true,snapshot=? WHERE id=? AND patient_id=? AND state='ACCEPTED'",json.writeValueAsString(snapshot),id,patient)!=1)throw unavailable();
        return ApiResponse.ok(true);
    }
    @PostMapping("/appointments/{id}/{action}") public ApiResponse<Boolean> transition(@PathVariable String id,@PathVariable String action) {
        String sql; AuthUser u;
        switch(action) {
            case "accept" -> {u=actor("doctor"); sql="SET state='ACCEPTED' WHERE id=? AND doctor_id=? AND state='PENDING' AND EXISTS(SELECT 1 FROM care_doctors d WHERE d.doctor_id=care_appointments.doctor_id)";}
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
        var rows=db.queryForList("SELECT COALESCE(a.snapshot,JSON_OBJECT('posts',JSON_ARRAY(),'results',JSON_ARRAY())) FROM care_appointments a JOIN care_doctors d ON d.doctor_id=a.doctor_id JOIN app_users p ON p.id=a.patient_id AND p.role='user' WHERE a.id=? AND a.doctor_id=? AND a.state='ACCEPTED'",String.class,id,u.getUserId());
        if(rows.isEmpty()) throw unavailable(); return ApiResponse.ok(json.readValue(rows.get(0),Snapshot.class));
    }
}