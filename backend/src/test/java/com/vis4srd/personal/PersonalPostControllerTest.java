package com.vis4srd.personal;

import com.vis4srd.common.exception.GlobalExceptionHandler;
import com.vis4srd.common.security.AuthUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.List;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Exercise HTTP validation and actual SQL on an isolated database, never real user data. */
class PersonalPostControllerTest {
    private MockMvc mvc;
    private JdbcTemplate jdbc;
    private static final String IMPORT = """
            {"consent":true,"posts":[{"content":"今天想回顾一下自己的生活","source":"手动导入","publishedAt":null}]}
            """;

    @BeforeEach
    void setup() {
        var ds = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        new ResourceDatabasePopulator(new ClassPathResource("db/personal-posts.sql")).execute(ds);
        jdbc = new JdbcTemplate(ds);
        mvc = MockMvcBuilders.standaloneSetup(new PersonalPostController(new PersonalPostService(jdbc)))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        login(101, "user");
    }

    @AfterEach
    void cleanup() throws Exception {
        SecurityContextHolder.clearContext();
        // JdbcTemplate's DEBUG warning inspection runs after SHUTDOWN closes the database.
        try (var connection = jdbc.getDataSource().getConnection(); var statement = connection.createStatement()) {
            statement.execute("SHUTDOWN");
        }
    }

    private void login(long id, String role) {
        var user = AuthUser.builder().userId(id).role(role).build();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user, null, List.of()));
    }

    @Test
    void importsDeduplicatesAndPreservesUnknownTime() throws Exception {
        mvc.perform(post("/api/me/posts/import").contentType("application/json").content(IMPORT))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.imported").value(1));
        mvc.perform(post("/api/me/posts/import").contentType("application/json").content(IMPORT))
                .andExpect(jsonPath("$.data.imported").value(0)).andExpect(jsonPath("$.data.skipped").value(1));
        mvc.perform(get("/api/me/posts")).andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].publishedAt").isEmpty());
        login(202, "user");
        mvc.perform(post("/api/me/posts/import").contentType("application/json").content(IMPORT))
                .andExpect(jsonPath("$.data.imported").value(1));
    }

    @Test
    void otherUsersAndAdminsCannotReadOrDeleteOwnersPosts() throws Exception {
        mvc.perform(post("/api/me/posts/import").contentType("application/json").content(IMPORT)).andExpect(status().isOk());
        String id = jdbc.queryForObject("SELECT id FROM personal_posts", String.class);
        for (String role : List.of("user", "admin", "doctor", "researcher")) {
            login(202, role);
            mvc.perform(get("/api/me/posts?owner_id=101")).andExpect(jsonPath("$.data.total").value(0));
            mvc.perform(delete("/api/me/posts/" + id)).andExpect(status().isNotFound());
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM personal_posts", Integer.class)).isEqualTo(1);
        login(101, "user");
        mvc.perform(delete("/api/me/posts/" + id)).andExpect(status().isOk());
        mvc.perform(get("/api/me/posts")).andExpect(jsonPath("$.data.total").value(0));
    }

    @Test
    void anonymousRequestsCannotReadImportOrDelete() throws Exception {
        SecurityContextHolder.clearContext();
        mvc.perform(get("/api/me/posts")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/me/posts/import").contentType("application/json").content(IMPORT)).andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/me/posts/anything")).andExpect(status().isUnauthorized());
    }

    @Test
    void validatesEntireBatchBeforeWritingAndRequiresExplicitConsent() throws Exception {
        for (String invalid : List.of(
                IMPORT.replace("true", "false"), IMPORT.replace("\"consent\":true,", ""),
                "{\"consent\":true,\"posts\":[]}",
                "{\"consent\":true,\"posts\":[{\"content\":\"valid\",\"source\":\"test\"},null]}",
                IMPORT.replace("今天想回顾一下自己的生活", " "),
                IMPORT.replace("null", "\"not a date\""),
                IMPORT.replace("今天想回顾一下自己的生活", "a".repeat(10001)))) {
            mvc.perform(post("/api/me/posts/import").contentType("application/json").content(invalid)).andExpect(status().isBadRequest());
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM personal_posts", Integer.class)).isZero();
    }

    @Test
    void ignoresForgedOwnerAndRejectsInvalidPagination() throws Exception {
        mvc.perform(post("/api/me/posts/import").contentType("application/json").content(IMPORT.replace("\"consent\"", "\"owner_id\":202,\"consent\"")))
                .andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT owner_id FROM personal_posts", Long.class)).isEqualTo(101L);
        mvc.perform(get("/api/me/posts?page=0")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/me/posts?pageSize=101")).andExpect(status().isBadRequest());
    }
}
