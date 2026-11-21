package com.vis4srd.verification;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties="DOCTOR_CERTIFICATION_INVITE_CODE=integration-only")
@AutoConfigureMockMvc
@Transactional(transactionManager="mysqlTransactionManager")
class DoctorVerificationIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired @Qualifier("mysqlJdbcTemplate") JdbcTemplate jdbc;
    record Account(long id, String token, String username) {}

    @Test void careAccessRequiresConsentCertificationAndAcceptedBooking() throws Exception {
        var patient=account("user"); var doctor=account("researcher"); var other=account("doctor");
        mvc.perform(post("/api/care/certification").header("Authorization",doctor.token()).contentType("application/json").content("{\"code\":\"integration-only\"}")).andExpect(status().isOk());
        var booking=new java.util.HashMap<String,Object>(); booking.put("doctorId",doctor.id());booking.put("scheduledAt",java.time.Instant.now().plusSeconds(86400).toString());
        booking.put("snapshot",Map.of("posts",java.util.List.of(Map.of("text","fixture")),"results",java.util.List.of(Map.of("name","fixture","score",3))));
        booking.put("consent",false);
        mvc.perform(post("/api/care/appointments").header("Authorization",patient.token()).contentType("application/json").content(mapper.writeValueAsBytes(booking))).andExpect(status().is4xxClientError());
        booking.put("consent",true);
        String body=mvc.perform(post("/api/care/appointments").header("Authorization",patient.token()).contentType("application/json").content(mapper.writeValueAsBytes(booking))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String path="/api/care/appointments/"+mapper.readTree(body).path("data").asText();
        mvc.perform(get(path+"/records").header("Authorization",doctor.token())).andExpect(status().isNotFound());
        mvc.perform(post(path+"/accept").header("Authorization",doctor.token())).andExpect(status().isOk());
        mvc.perform(get("/api/care/appointments").header("Authorization",doctor.token())).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store")).andExpect(jsonPath("$.data[0].patientId").value((int) patient.id())).andExpect(jsonPath("$.data[0].shared").value(true));
        mvc.perform(get("/api/care/appointments").header("Authorization",other.token())).andExpect(status().isOk()).andExpect(jsonPath("$.data").isEmpty());
        mvc.perform(get("/api/care/appointments").header("Authorization",patient.token())).andExpect(status().isOk()).andExpect(jsonPath("$.data[0].patientId").doesNotExist());
        mvc.perform(get(path+"/records").header("Authorization",other.token())).andExpect(status().isNotFound());
        mvc.perform(get(path+"/records").header("Authorization",doctor.token())).andExpect(status().isOk()).andExpect(jsonPath("$.data.posts[0].text").value("fixture")).andExpect(jsonPath("$.data.results[0].score").value(3));
        mvc.perform(post(path+"/cancel").header("Authorization",patient.token())).andExpect(status().isOk());
        mvc.perform(get(path+"/records").header("Authorization",doctor.token())).andExpect(status().isNotFound());
    }
    private Account account(String role) throws Exception {
        String username = "verify_" + UUID.randomUUID().toString().replace("-", "").substring(0,12);
        String result = mvc.perform(post("/api/auth/register").contentType("application/json").content(mapper.writeValueAsString(Map.of("username",username,"password","VerifyTest42!","nickname","合成测试账号"))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        long id = jdbc.queryForObject("SELECT id FROM app_users WHERE username=?", Long.class, username);
        jdbc.update("UPDATE app_users SET role=? WHERE id=?",role,id);
        return new Account(id,"Bearer " + mapper.readTree(result).path("data").path("token").asText(),username);
    }
    private MockMultipartFile input(boolean consent) throws Exception {
        return new MockMultipartFile("application","","application/json",mapper.writeValueAsBytes(Map.of("realName","合成姓名","institution","合成机构","qualification","测试资质","credentialNumber","TEST-NOT-REAL","notes","仅测试，事务回滚","consent",consent)));
    }
    private MockMultipartFile proof() { return new MockMultipartFile("proof","proof.pdf","application/pdf","%PDF-1.4 synthetic test only".getBytes(StandardCharsets.UTF_8)); }
    private String submit(Account actor) throws Exception {
        String result = mvc.perform(multipart("/api/me/doctor-applications").file(input(true)).file(proof()).header("Authorization",actor.token()))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store")).andReturn().getResponse().getContentAsString();
        return mapper.readTree(result).path("data").path("id").asText();
    }
    private void review(Account admin, String id, String decision, int expected) throws Exception {
        mvc.perform(post("/api/admin/doctor-applications/"+id+"/review").header("Authorization",admin.token()).contentType("application/json")
                .content(mapper.writeValueAsString(Map.of("decision",decision,"reason","测试核查结论"))))
                .andExpect(status().is(expected));
    }
    @Test void fullLifecycleAndOldTokenRoleRefresh() throws Exception {
        var user=account("researcher"); var admin=account("admin");
        String first=submit(user);
        mvc.perform(multipart("/api/me/doctor-applications").file(input(true)).file(proof()).header("Authorization",user.token())).andExpect(status().isConflict());
        review(admin,first,"REJECT",200);
        String second=submit(user);
        review(admin,second,"APPROVE",200);
        mvc.perform(get("/api/auth/me").header("Authorization",user.token())).andExpect(jsonPath("$.data.role").value("doctor"));
        review(admin,second,"APPROVE",409);
        String credentials = mapper.writeValueAsString(Map.of("username",user.username(),"password","VerifyTest42!"));
        mvc.perform(post("/api/auth/login").contentType("application/json").content(credentials)).andExpect(status().isForbidden());
        String login = mvc.perform(post("/api/auth/web/login").contentType("application/json").content(credentials))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String doctorToken="Bearer "+mapper.readTree(login).path("data").path("token").asText();
        review(admin,second,"REVOKE",200);
        mvc.perform(get("/api/auth/me").header("Authorization",doctorToken)).andExpect(jsonPath("$.data.role").value("researcher"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM doctor_application_events WHERE application_id=?",Integer.class,second)).isEqualTo(3);
        submit(new Account(user.id(),doctorToken,user.username()));
    }
    @Test void proofIsOwnerOrAdminOnlyAndDownloadsAreAudited() throws Exception {
        var user=account("researcher"); var other=account("doctor"); var admin=account("admin");
        String id=submit(user);
        mvc.perform(get("/api/doctor-applications/"+id+"/proof")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/doctor-applications/"+id+"/proof").header("Authorization",other.token())).andExpect(status().isNotFound());
        for (var viewer : new Account[]{user,admin}) {
            mvc.perform(get("/api/doctor-applications/"+id+"/proof").header("Authorization",viewer.token()))
                    .andExpect(status().isOk()).andExpect(header().string("X-Content-Type-Options","nosniff"))
                    .andExpect(header().string("Cache-Control","no-store"));
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM doctor_application_events WHERE application_id=? AND action='DOWNLOAD_PROOF'",Integer.class,id)).isEqualTo(2);
        mvc.perform(get("/api/me/doctor-applications").header("Authorization",other.token())).andExpect(jsonPath("$.data").isEmpty());
    }
    @Test void onlyAdminReviewsAndSelfReviewIsRejected() throws Exception {
        var user=account("researcher"); var doctor=account("doctor"); String id=submit(user);
        review(doctor,id,"APPROVE",403);
        mvc.perform(get("/api/admin/doctor-applications").header("Authorization",user.token())).andExpect(status().isForbidden());
        jdbc.update("UPDATE app_users SET role='admin' WHERE id=?",user.id());
        review(user,id,"APPROVE",403);
    }
    @Test void consentFileFormatSizeAndApplicantRoleValidated() throws Exception {
        var user=account("researcher"); var ordinary=account("user"); var doctor=account("doctor");
        for (var forbidden : new Account[]{ordinary,doctor}) {
            mvc.perform(multipart("/api/me/doctor-applications").file(input(true)).file(proof()).header("Authorization",forbidden.token())).andExpect(status().isForbidden());
        }
        mvc.perform(multipart("/api/me/doctor-applications").file(input(false)).file(proof()).header("Authorization",user.token())).andExpect(status().isBadRequest());
        mvc.perform(multipart("/api/me/doctor-applications").file(input(true)).header("Authorization",user.token())).andExpect(status().isBadRequest());
        for (byte[] bytes : new byte[][]{"<html>invalid</html>".getBytes(StandardCharsets.UTF_8),new byte[5*1024*1024+1]}) {
            mvc.perform(multipart("/api/me/doctor-applications").file(input(true)).file(new MockMultipartFile("proof","proof.pdf","application/pdf",bytes)).header("Authorization",user.token())).andExpect(status().isBadRequest());
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM doctor_applications WHERE applicant_id=?",Integer.class,user.id())).isZero();
    }
    @Test void invalidReviewDoesNotPromoteAndDisabledAccountCannotAccess() throws Exception {
        var user=account("researcher"); var admin=account("admin"); String id=submit(user);
        review(admin,id,"INVALID",400);
        mvc.perform(post("/api/admin/doctor-applications/"+id+"/review").header("Authorization",admin.token()).contentType("application/json").content("{\"decision\":\"APPROVE\",\"reason\":\" \"}"))
                .andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT role FROM app_users WHERE id=?",String.class,user.id())).isEqualTo("researcher");
        jdbc.update("UPDATE app_users SET is_active=false WHERE id=?",user.id());
        mvc.perform(get("/api/me/doctor-applications").header("Authorization",user.token())).andExpect(status().isUnauthorized());
    }
}
