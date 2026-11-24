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
 @Test void onlyAdminsCanListAccountsAndChangeRoles() {
  var jdbc=new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1","sa",""));
  jdbc.execute("CREATE TABLE app_users(id BIGINT PRIMARY KEY, username VARCHAR(80), role VARCHAR(30), is_active BOOLEAN)");
  jdbc.update("INSERT INTO app_users VALUES(101,'alice','user',true),(102,'bob','researcher',true)");
  var c=new AdminUserController(jdbc);
  try {
   var change = new AdminUserController.RoleChange("researcher","admin");
   for(String role:List.of("user","researcher","doctor")) {
    login(role);
    assertThatThrownBy(()->c.list("",1,"mobile")).isInstanceOf(BusinessException.class);
    assertThatThrownBy(()->c.changeStatus(101,new AdminUserController.StatusChange(false))).isInstanceOf(BusinessException.class);
    assertThatThrownBy(()->c.changeRole(101,change)).isInstanceOf(BusinessException.class);
   }
   login("admin");
   assertThatThrownBy(()->c.changeRole(101,change)).isInstanceOf(BusinessException.class);
   assertThatThrownBy(()->c.changeStatus(102,new AdminUserController.StatusChange(false))).isInstanceOf(BusinessException.class);
   c.changeStatus(101,new AdminUserController.StatusChange(false));
   assertThat(jdbc.queryForObject("SELECT is_active FROM app_users WHERE id=101",Boolean.class)).isFalse();
   c.changeStatus(101,new AdminUserController.StatusChange(true));
   assertThat(jdbc.queryForObject("SELECT is_active FROM app_users WHERE id=101",Boolean.class)).isTrue();
   c.changeRole(102,change);
   assertThat(jdbc.queryForObject("SELECT role FROM app_users WHERE id=102",String.class)).isEqualTo("admin");
  }finally{SecurityContextHolder.clearContext();jdbc.execute("SHUTDOWN");}
 }
 private void login(String role){SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(AuthUser.builder().userId(1L).role(role).build(),null,List.of()));}
}
