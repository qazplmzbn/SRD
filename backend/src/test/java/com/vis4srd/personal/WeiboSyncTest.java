package com.vis4srd.personal;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.mock.env.MockEnvironment;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class WeiboSyncTest {
 @Test void checksAuthorBeforeWritingAndReplaysIds()throws Exception{
  var ds=new DriverManagerDataSource("jdbc:h2:mem:weibo;MODE=MySQL;DB_CLOSE_DELAY=-1","sa","");new ResourceDatabasePopulator(new ClassPathResource("db/personal-posts.sql")).execute(ds);
  var db=new JdbcTemplate(ds);var gateway=mock(WeiboGateway.class);var sync=new WeiboSync(db,gateway,new DataSourceTransactionManager(ds));var json=new ObjectMapper();
  var credentials=json.createObjectNode().put("access_token","test").put("expires",Long.MAX_VALUE);credentials.putArray("flags").add("uid").add("count");
  when(gateway.open(1,"sealed")).thenReturn(credentials);
  db.update("INSERT INTO weibo_connections VALUES(1,'sealed','111','SYNCING',0,0,1,'0',false,0,5)");
  var response=json.createObjectNode();var p=response.putArray("statuses").addObject().put("idstr","90071992547409931").put("text","test").put("comments_count",0);var user=p.putObject("user").put("idstr","222");
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