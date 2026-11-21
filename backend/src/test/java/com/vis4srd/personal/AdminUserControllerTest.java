package com.vis4srd.personal;
import com.vis4srd.common.exception.BusinessException;
import com.vis4srd.common.security.AuthUser;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.List;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
class AdminUserControllerTest {
 @Test void onlyAdminsCanReadAndControllerHasNoMutationEndpoints() {
  var jdbc=new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1","sa",""));
  jdbc.execute("CREATE TABLE app_users(id BIGINT PRIMARY KEY, username VARCHAR(80), nickname VARCHAR(80), role VARCHAR(30), is_active BOOLEAN, created_at TIMESTAMP)");
  jdbc.execute("CREATE TABLE personal_posts(id VARCHAR(80), owner_id BIGINT, content TEXT, source VARCHAR(80), published_at TIMESTAMP, imported_at TIMESTAMP)");
  jdbc.update("INSERT INTO app_users VALUES(101,'alice','Alice','user',true,CURRENT_TIMESTAMP),(202,'bob','Bob','user',true,CURRENT_TIMESTAMP)");
  jdbc.update("INSERT INTO personal_posts VALUES('p1',101,'hello','test',NULL,CURRENT_TIMESTAMP)");
  var c=new AdminUserController(jdbc);
  try {
   for(String role:List.of("user","researcher","doctor")){login(role);assertThatThrownBy(()->c.list("",1)).isInstanceOf(BusinessException.class);assertThatThrownBy(()->c.posts(101,1)).isInstanceOf(BusinessException.class);}
   login("admin");assertThat(c.list("alice",1)).isNotNull();assertThat(c.posts(101,1)).isNotNull();
   for(var method:AdminUserController.class.getDeclaredMethods()){assertThat(method.isAnnotationPresent(org.springframework.web.bind.annotation.PatchMapping.class)).isFalse();assertThat(method.isAnnotationPresent(org.springframework.web.bind.annotation.DeleteMapping.class)).isFalse();assertThat(method.isAnnotationPresent(org.springframework.web.bind.annotation.PostMapping.class)).isFalse();}
   assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM personal_posts",Integer.class)).isEqualTo(1);
  }finally{SecurityContextHolder.clearContext();jdbc.execute("SHUTDOWN");}
 }
 private void login(String role){SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(AuthUser.builder().userId(1L).role(role).build(),null,List.of()));}
}
