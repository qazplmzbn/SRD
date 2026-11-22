package com.vis4srd.personal;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class DouyinSyncTest {
 @Test void unavailableStatisticsDoNotBlockListsAndSuccessfulChecksAreThrottled()throws Exception{
  var ds=new DriverManagerDataSource("jdbc:h2:mem:douyinmetrics;MODE=MySQL;DB_CLOSE_DELAY=-1","sa","");new ResourceDatabasePopulator(new ClassPathResource("db/personal-posts.sql")).execute(ds);
  var db=new JdbcTemplate(ds);var client=mock(DouyinClient.class);var t=new DouyinClient.Tokens("oid","test","test",Long.MAX_VALUE,true);when(client.unseal("sealed",1)).thenReturn(t);
  var json=new ObjectMapper();var page=json.readTree("{\"has_more\":false,\"list\":[{\"item_id\":\"1\"}]}");when(client.videos(t,0)).thenReturn(page);
  when(client.statistics(t,java.util.List.of("1"))).thenThrow(new java.io.IOException()).thenReturn(json.readTree("[{\"item_id\":\"1\",\"statistics\":{\"play_count\":123}}]"));
  db.update("INSERT INTO douyin_connections VALUES(1,'oid','sealed','g',0,0,'SYNCING',0)");var sync=new DouyinSync(db,client,new DataSourceTransactionManager(ds));long now=System.currentTimeMillis();sync.sync(1,now);
  assertThat(db.queryForObject("SELECT status FROM douyin_connections",String.class)).isEqualTo("CURRENT");assertThat(db.queryForObject("SELECT result FROM douyin_metric_checks",String.class)).isEqualTo("UNAVAILABLE");
  sync.sync(1,now+901000);sync.sync(1,now+1000000);assertThat(db.queryForObject("SELECT result FROM douyin_metric_checks",String.class)).isEqualTo("OK");assertThat(db.queryForObject("SELECT COUNT(*) FROM social_post_metrics",Integer.class)).isOne();verify(client,times(2)).statistics(t,java.util.List.of("1"));
 }
 @Test void capsInitialCoverageAndReplaysLatestWithoutDuplicates() throws Exception {
  var ds=new DriverManagerDataSource("jdbc:h2:mem:douyintest;MODE=MySQL;DB_CLOSE_DELAY=-1","sa","");
  new ResourceDatabasePopulator(new ClassPathResource("db/personal-posts.sql")).execute(ds);
  var db=new JdbcTemplate(ds);var client=mock(DouyinClient.class);var tokens=new DouyinClient.Tokens("oid","test","test",Long.MAX_VALUE);
  when(client.unseal("sealed",1)).thenReturn(tokens);var json=new ObjectMapper();
  when(client.videos(eq(tokens),anyLong())).thenAnswer(call->{long offset=call.getArgument(1);var data=json.createObjectNode();var list=data.putArray("list");for(long i=offset;i<offset+20;i++)list.addObject().put("item_id","i"+i).put("title","p"+i).put("create_time",1_700_000_000L-i).putObject("statistics").put("digg_count",2);return data.put("has_more",true).put("cursor",offset+20);});
  db.update("INSERT INTO douyin_connections VALUES(1,'oid','sealed','g',0,0,'SYNCING',0)");
  var sync=new DouyinSync(db,client,new DataSourceTransactionManager(ds));
  long now=System.currentTimeMillis();
  for(int i=0;i<4;i++)sync.sync(1,now+i*5000);
  assertThat(db.queryForObject("SELECT pages FROM douyin_sync_coverage",Integer.class)).isEqualTo(4);
  assertThat(db.queryForObject("SELECT finished AND upstream_has_more FROM douyin_sync_coverage",Boolean.class)).isTrue();
  assertThat(db.queryForObject("SELECT oldest_create_time FROM douyin_sync_coverage",Long.class)).isEqualTo(1_699_999_921L);
  assertThat(db.queryForObject("SELECT COUNT(*) FROM personal_posts",Long.class)).isEqualTo(80);
  assertThat(db.queryForObject("SELECT COUNT(*) FROM social_post_details",Long.class)).isEqualTo(80);
  sync.sync(1,now+7200000);
  assertThat(db.queryForObject("SELECT COUNT(*) FROM personal_posts",Long.class)).isEqualTo(80);
  assertThat(db.queryForObject("SELECT COUNT(*) FROM social_post_metrics",Long.class)).isEqualTo(80);
  sync.sync(1,now+90_000_000);
  assertThat(db.queryForObject("SELECT COUNT(*) FROM social_post_metrics",Long.class)).isEqualTo(100);
  verify(client,never()).videos(tokens,80L);
  verify(client,times(3)).videos(tokens,0L);
  db.update("DELETE FROM personal_posts WHERE owner_id=1");
  assertThat(db.queryForObject("SELECT COUNT(*) FROM social_post_details",Long.class)).isZero();
  assertThat(db.queryForObject("SELECT COUNT(*) FROM social_post_metrics",Long.class)).isZero();
 }
}