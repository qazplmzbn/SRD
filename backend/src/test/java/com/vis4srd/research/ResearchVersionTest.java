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
 @Test void arbitraryColumnsAndLabelsRoundTrip() throws Exception {
  String path=System.getProperty("featureCsv");
  byte[] bytes=path==null?"'', '',class_A,class_B\n0001,002,多类,0.375\n".replace(", ''",",''").getBytes(java.nio.charset.StandardCharsets.UTF_8):java.nio.file.Files.readAllBytes(java.nio.file.Path.of(path));
  var grid=ResearchCsv.table(new org.springframework.mock.web.MockMultipartFile("file","features.csv","text/csv",bytes));
  if(path!=null){assertThat(grid.columns()).hasSize(100);assertThat(grid.rows()).hasSize(500);assertThat(grid.columns().get(34)).isEqualTo(grid.columns().get(63));}
  var names=new ArrayList<>(grid.columns());names.set(0,"自定义字段");
  for(var invalid:List.of(List.<Integer>of(),List.of(2),List.of(1,1),List.of(names.size()))) {
   assertThatThrownBy(()->service.createTable(new ResearchVersionController.TableMeta("feature","csv","research",true,names,2,invalid),grid)).isInstanceOf(BusinessException.class);
  }
  String id=service.createTable(new ResearchVersionController.TableMeta("feature","csv","research",true,names,2,List.of(1,0)),grid);
  var saved=(com.fasterxml.jackson.databind.JsonNode)service.detail(id).get("table");var mapper=new ObjectMapper();
  assertThat(saved.get("labelColumn").intValue()).isEqualTo(2);assertThat(saved.get("postColumns")).isEqualTo(mapper.valueToTree(List.of(1,0)));
  assertThat(saved.get("columns")).isEqualTo(mapper.valueToTree(names));assertThat(saved.get("originalColumns")).isEqualTo(mapper.valueToTree(grid.columns()));assertThat(saved.get("rows")).isEqualTo(mapper.valueToTree(grid.rows()));
 }
 @Test void archiveGenerationIsPrivateAndRepeatable(){
  var archives=new ResearchArchiveController(jdbc,service);
  String id=service.create(input(List.of(new ResearchVersionController.Sample("a","first",0,"TEST"),new ResearchVersionController.Sample("a","second",1,"TEST"))));
  archives.generate("version",id,null);archives.generate("version",id,null);
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM research_archive_subjects",Long.class)).isEqualTo(1L);
  service.grant(id,new ResearchVersionController.Grant(2,false));login(2,"doctor");
  assertThatThrownBy(()->archives.generate("version",id,null)).isInstanceOf(BusinessException.class);
  assertThat((List<?>)archives.subjects(id).getData()).isEmpty();archives.remove("version",id);
  login(1,"researcher");assertThat((List<?>)archives.subjects(id).getData()).hasSize(1);archives.remove("version",id);
  assertThat((List<?>)archives.subjects(id).getData()).isEmpty();assertThat(service.detail(id)).containsKey("samples");
 }
 @Test void mappedArchiveKeepsAllPostsAndFullText() throws Exception {
  var columns=List.of("账号","标签","正文1","正文2");String text="长文".repeat(150)+"\n结尾";
  var grid=new ResearchCsv.Grid(columns,List.of(List.of("001","A",text,text),List.of("001","B","末条","")));
  String id=service.createTable(new ResearchVersionController.TableMeta("table","csv","research",true,columns,1,List.of(2,3)),grid);
  var archives=new ResearchArchiveController(jdbc,service);
  archives.generate("version",id,null);assertThat((List<?>)archives.subjects(id).getData()).hasSize(2);
  archives.generate("version",id,0);var mapper=new ObjectMapper();var groups=mapper.valueToTree(archives.subjects(id).getData());
  assertThat(groups.size()).isEqualTo(1);assertThat(groups.get(0).get("subject").asText()).isEqualTo("001");
  var posts=groups.get(0).get("posts");assertThat(posts.size()).isEqualTo(3);
  assertThat(posts.get(0).get("text").asText()).isEqualTo(text);assertThat(posts.get(1).get("text").asText()).isEqualTo(text);
  assertThat(posts.get(2).get("label").asText()).isEqualTo("B");assertThat(posts.get(2).has("importance")).isTrue();
 }
 void login(long id,String role){SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(AuthUser.builder().userId(id).role(role).build(),null,List.of()));}
 ResearchVersionController.Create input(List<ResearchVersionController.Sample> samples){return new ResearchVersionController.Create("test","v1","synthetic","research only",true,samples);}
 @Test void isolationGrantsRevocationAndImmutableNames(){String id=service.create(input(List.of(new ResearchVersionController.Sample("a","text",0,"TEST"))));assertThat(service.detail(id).get("checksum").toString()).hasSize(64);
  assertThat(service.detail(id).get("versionName")).isEqualTo("v1");
  String next=service.create(input(List.of(new ResearchVersionController.Sample("a","changed",1,"TEST"))));assertThat(service.detail(next).get("versionName")).isEqualTo("v2");
  login(3,"admin");assertThatThrownBy(()->service.detail(id)).isInstanceOf(BusinessException.class);
  login(2,"doctor");assertThat(service.list(1).get("total")).isEqualTo(0L);assertThatThrownBy(()->service.detail(id)).isInstanceOf(BusinessException.class);
  login(1,"researcher");service.grant(id,new ResearchVersionController.Grant(2,false));login(2,"doctor");assertThat(service.detail(id).get("name")).isEqualTo("test");assertThatThrownBy(()->service.grant(id,new ResearchVersionController.Grant(3,false))).isInstanceOf(BusinessException.class);
  login(1,"researcher");service.grant(id,new ResearchVersionController.Grant(2,true));login(2,"doctor");assertThatThrownBy(()->service.detail(id)).isInstanceOf(BusinessException.class);
 }
 @Test void splitLeakageAndDuplicateSamplesRejected(){assertThatThrownBy(()->service.create(input(List.of(new ResearchVersionController.Sample("a","one",0,"TRAIN"),new ResearchVersionController.Sample("a","two",1,"TEST"))))).isInstanceOf(BusinessException.class);var row=new ResearchVersionController.Sample("a","one",0,"TEST");assertThatThrownBy(()->service.create(input(List.of(row,row)))).isInstanceOf(BusinessException.class);assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM research_versions",Long.class)).isZero();}
}
