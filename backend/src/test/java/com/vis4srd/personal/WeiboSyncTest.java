package com.vis4srd.personal;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.mock.env.MockEnvironment;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class WeiboSyncTest {
 @Test void duplicatePageStillAdvancesToOlderPosts()throws Exception{
  var f=new Fixture("weibo_duplicate_page","page","count");
  var first=f.page(20,"first",f.now);
  when(f.gateway.timeline("test",Map.of("page",1,"count",20))).thenReturn(first);
  when(f.gateway.timeline("test",Map.of("page",2,"count",20))).thenReturn(first);
  when(f.gateway.timeline("test",Map.of("page",3,"count",20))).thenReturn(f.page(1,"older",f.now-86400000));
  f.sync.sync(1,f.now);f.sync.sync(1,f.now+5000);
  assertThat(f.pageNo()).isEqualTo(3);
  f.sync.sync(1,f.now+10000);
  assertThat(f.db.queryForObject("SELECT COUNT(*) FROM personal_posts",Integer.class)).isEqualTo(21);
  assertThat(f.done()).isTrue();
  assertThat(f.pageNo()).isOne();
 }

 @Test void pageHasMoreTakesPrecedenceOverPageSize()throws Exception{
  var f=new Fixture("weibo_has_more","page","count");
  when(f.gateway.timeline("test",Map.of("page",1,"count",20))).thenReturn(f.page(1,"first",f.now).put("has_more",true));
  when(f.gateway.timeline("test",Map.of("page",2,"count",20))).thenReturn(f.page(20,"end",f.now).put("has_more",false));
  f.sync.sync(1,f.now);
  assertThat(f.pageNo()).isEqualTo(2);
  f.sync.sync(1,f.now+5000);
  assertThat(f.done()).isTrue();
 }

 @Test void cursorModeAdvancesAndStopsWhenCursorRepeats()throws Exception{
  var f=new Fixture("weibo_cursor","cursor","page");
  when(f.gateway.timeline("test",Map.of("cursor","0"))).thenReturn(f.page(1,"first",f.now).put("next_cursor",42));
  when(f.gateway.timeline("test",Map.of("cursor","42"))).thenReturn(f.page(1,"end",f.now).put("next_cursor",42).put("has_more",true));
  f.sync.sync(1,f.now);
  assertThat(f.pageNo()).isEqualTo(2);
  f.sync.sync(1,f.now+5000);
  assertThat(f.done()).isTrue();
  assertThat(f.db.queryForObject("SELECT page_cursor FROM weibo_connections",String.class)).isEqualTo("0");
 }

 @Test void initialSyncFiltersOldAndUndatedPostsAndStopsAfterAnAllOldPage()throws Exception{
  var f=new Fixture("weibo_history","page","count");
  long cutoff=Instant.ofEpochMilli(f.now).minus(90,ChronoUnit.DAYS).toEpochMilli();
  var mixed=f.page(3,"mixed",cutoff).put("has_more",true);
  ((ObjectNode)mixed.path("statuses").get(0)).put("created_at",Instant.ofEpochMilli(cutoff-1).toString());
  ((ObjectNode)mixed.path("statuses").get(2)).remove("created_at");
  when(f.gateway.timeline("test",Map.of("page",1,"count",20))).thenReturn(mixed);
  when(f.gateway.timeline("test",Map.of("page",2,"count",20))).thenReturn(f.page(1,"old",cutoff-1).put("has_more",true));
  f.sync.sync(1,f.now);
  assertThat(f.pageNo()).isEqualTo(2);
  f.sync.sync(1,f.now+5000);
  assertThat(f.done()).isTrue();
  assertThat(f.db.queryForList("SELECT external_id FROM social_post_details",String.class)).containsExactly("mixed1");
 }

 @Test void initialSyncStopsAfterTwentyPagesAndResumesPollingPageOne()throws Exception{
  var f=new Fixture("weibo_page_limit","page");
  when(f.gateway.timeline(eq("test"),anyMap())).thenReturn(f.page(1,"repeat",f.now).put("has_more",true));
  for(int i=0;i<20;i++)f.sync.sync(1,f.now+i*5000L);
  assertThat(f.done()).isTrue();
  f.sync.sync(1,f.now+400000);
  verify(f.gateway,times(2)).timeline("test",Map.of("page",1));
  verify(f.gateway,never()).timeline("test",Map.of("page",21));
  assertThat(f.done()).isTrue();
  assertThat(f.pageNo()).isOne();
 }

 private static class Fixture {
  final ObjectMapper json=new ObjectMapper();
  final WeiboGateway gateway=mock(WeiboGateway.class);
  final long now=Instant.parse("2026-05-01T00:00:00Z").toEpochMilli();
  final JdbcTemplate db;
  final WeiboSync sync;

  Fixture(String name,String... flags)throws Exception{
   var ds=new DriverManagerDataSource("jdbc:h2:mem:"+name+";MODE=MySQL;DB_CLOSE_DELAY=-1","sa","");
   new ResourceDatabasePopulator(new ClassPathResource("db/personal-posts.sql")).execute(ds);
   db=new JdbcTemplate(ds);sync=new WeiboSync(db,gateway,new DataSourceTransactionManager(ds));
   var credentials=json.createObjectNode().put("access_token","test").put("expires",Long.MAX_VALUE);
   var supported=credentials.putArray("flags");for(String flag:flags)supported.add(flag);
   when(gateway.open(1,"sealed")).thenReturn(credentials);
   db.update("INSERT INTO weibo_connections VALUES(1,'sealed','111','SYNCING',0,0,1,'0',false,0,5)");
  }

  ObjectNode page(int count,String prefix,long published){
   var response=json.createObjectNode();var posts=response.putArray("statuses");
   for(int i=0;i<count;i++)posts.addObject().put("idstr",prefix+i).put("created_at",Instant.ofEpochMilli(published).toString()).putObject("user").put("idstr","111");
   return response;
  }

  int pageNo(){return db.queryForObject("SELECT page_no FROM weibo_connections",Integer.class);}
  boolean done(){return db.queryForObject("SELECT initial_done FROM weibo_connections",Boolean.class);}
 }

 @Test void checksAuthorBeforeWritingAndReplaysIds()throws Exception{
  var ds=new DriverManagerDataSource("jdbc:h2:mem:weibo;MODE=MySQL;DB_CLOSE_DELAY=-1","sa","");new ResourceDatabasePopulator(new ClassPathResource("db/personal-posts.sql")).execute(ds);
  var db=new JdbcTemplate(ds);var gateway=mock(WeiboGateway.class);var sync=new WeiboSync(db,gateway,new DataSourceTransactionManager(ds));var json=new ObjectMapper();
  var credentials=json.createObjectNode().put("access_token","test").put("expires",Long.MAX_VALUE);credentials.putArray("flags").add("uid").add("count");
  when(gateway.open(1,"sealed")).thenReturn(credentials);
  db.update("INSERT INTO weibo_connections VALUES(1,'sealed','111','SYNCING',0,0,1,'0',false,0,5)");
  var response=json.createObjectNode();var p=response.putArray("statuses").addObject().put("idstr","90071992547409931").put("text","test").put("comments_count",0).put("created_at",Instant.now().toString());var user=p.putObject("user").put("idstr","222");
  when(gateway.timeline("test",Map.of("uid","111","count",20))).thenReturn(response);
  long now=System.currentTimeMillis();assertThatThrownBy(()->sync.sync(1,now)).isInstanceOf(IllegalStateException.class);assertThat(db.queryForObject("SELECT COUNT(*) FROM personal_posts",Integer.class)).isZero();
  user.put("idstr","111");sync.sync(1,now);sync.sync(1,now+3600000);
  assertThat(db.queryForObject("SELECT COUNT(*) FROM personal_posts",Integer.class)).isOne();assertThat(db.queryForObject("SELECT external_id FROM social_post_details",String.class)).isEqualTo("90071992547409931");assertThat(db.queryForObject("SELECT COUNT(*) FROM social_post_metrics",Integer.class)).isOne();
 }
 @Test void credentialsAreOwnerBound()throws Exception{
  var json=new ObjectMapper();var gateway=new WeiboGateway(json,new MockEnvironment().withProperty("WEIBO_TOKEN_KEY",Base64.getEncoder().encodeToString(new byte[32])));
  var data=json.createObjectNode().put("device_code","test");String sealed=gateway.seal(1,data);assertThat(gateway.open(1,sealed)).isEqualTo(data);assertThatThrownBy(()->gateway.open(2,sealed)).isInstanceOf(javax.crypto.AEADBadTagException.class);
 }
}