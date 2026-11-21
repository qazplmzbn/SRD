package com.vis4srd.research;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vis4srd.common.security.*;
import com.vis4srd.common.exception.BusinessException;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
class ResearchVersionTest {
 JdbcTemplate jdbc; ResearchVersionService service;
 @BeforeEach void setup(){var ds=new DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1","sa","");jdbc=new JdbcTemplate(ds);new ResourceDatabasePopulator(new ClassPathResource("db/research-versions.sql")).execute(ds);jdbc.execute("CREATE TABLE app_users(id BIGINT PRIMARY KEY,username VARCHAR(30),role VARCHAR(30),is_active BOOLEAN)");jdbc.update("INSERT INTO app_users VALUES(1,'owner','researcher',true),(2,'reader','doctor',true),(3,'admin','admin',true)");service=new ResearchVersionService(jdbc,new ObjectMapper());login(1,"researcher");}
 @AfterEach void cleanup() throws Exception {SecurityContextHolder.clearContext();try(var c=jdbc.getDataSource().getConnection();var s=c.createStatement()){s.execute("SHUTDOWN");}}
 void login(long id,String role){SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(AuthUser.builder().userId(id).role(role).build(),null,List.of()));}
 ResearchVersionController.Create input(List<ResearchVersionController.Sample> samples){return new ResearchVersionController.Create("test","v1","synthetic","research only",true,samples);}
 @Test void isolationGrantsRevocationAndImmutableNames(){String id=service.create(input(List.of(new ResearchVersionController.Sample("a","text",0,"TEST"))));assertThat(service.detail(id).get("checksum").toString()).hasSize(64);
  assertThatThrownBy(()->service.create(input(List.of(new ResearchVersionController.Sample("a","changed",1,"TEST"))))).isInstanceOf(BusinessException.class);
  login(3,"admin");assertThatThrownBy(()->service.detail(id)).isInstanceOf(BusinessException.class);
  login(2,"doctor");assertThat(service.list(1).get("total")).isEqualTo(0L);assertThatThrownBy(()->service.detail(id)).isInstanceOf(BusinessException.class);
  login(1,"researcher");service.grant(id,new ResearchVersionController.Grant(2,false));login(2,"doctor");assertThat(service.detail(id).get("name")).isEqualTo("test");assertThatThrownBy(()->service.grant(id,new ResearchVersionController.Grant(3,false))).isInstanceOf(BusinessException.class);
  login(1,"researcher");service.grant(id,new ResearchVersionController.Grant(2,true));login(2,"doctor");assertThatThrownBy(()->service.detail(id)).isInstanceOf(BusinessException.class);
 }
 @Test void splitLeakageAndDuplicateSamplesRejected(){assertThatThrownBy(()->service.create(input(List.of(new ResearchVersionController.Sample("a","one",0,"TRAIN"),new ResearchVersionController.Sample("a","two",1,"TEST"))))).isInstanceOf(BusinessException.class);var row=new ResearchVersionController.Sample("a","one",0,"TEST");assertThatThrownBy(()->service.create(input(List.of(row,row)))).isInstanceOf(BusinessException.class);assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM research_versions",Long.class)).isZero();}
}
