package com.vis4srd.research;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vis4srd.common.exception.BusinessException;
import com.vis4srd.common.security.SecurityUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DuplicateKeyException;
import java.sql.Timestamp;
import java.time.Instant;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Service
public class ResearchVersionService {
 private final JdbcTemplate jdbc; private final ObjectMapper mapper;
 public ResearchVersionService(@Qualifier("mysqlJdbcTemplate") JdbcTemplate jdbc,ObjectMapper mapper){this.jdbc=jdbc;this.mapper=mapper;}
 long actor(){var user=SecurityUtils.requireCurrentUser();if(user.getUserId()==null||!List.of("researcher","doctor","admin").contains(Objects.toString(user.getRole(),"")))throw new BusinessException(HttpStatus.FORBIDDEN,"需要专业账号权限");return user.getUserId();}
 private static final String ACCESS=" (v.owner_id=? OR EXISTS(SELECT 1 FROM research_version_grants g WHERE g.version_id=v.id AND g.user_id=?))";
 private static final String META="v.id,v.owner_id AS ownerId,v.name,v.version_name AS versionName,v.source,v.usage_scope AS usageScope,v.checksum,v.sample_count AS sampleCount,v.created_at AS createdAt";
 public Map<String,Object> list(int page){long user=actor();if(page<1)throw new BusinessException(HttpStatus.BAD_REQUEST,"页码不正确");
  return Map.of("items",jdbc.queryForList("SELECT "+META+" FROM research_versions v WHERE "+ACCESS+" ORDER BY v.created_at DESC,v.id DESC LIMIT 20 OFFSET ?",user,user,((long)page-1)*20),"total",jdbc.queryForObject("SELECT COUNT(*) FROM research_versions v WHERE "+ACCESS,Long.class,user,user));}
 public Map<String,Object> detail(String id){long user=actor();var rows=jdbc.queryForList("SELECT "+META+",v.samples_json AS samplesJson FROM research_versions v WHERE v.id=? AND "+ACCESS,id,user,user);
  if(rows.isEmpty())throw new BusinessException(HttpStatus.NOT_FOUND,"版本不存在或无权访问");
   var result=rows.get(0);try{var payload=mapper.readTree(result.remove("samplesJson").toString());result.put(payload.isObject()?"table":"samples",payload);}catch(Exception e){throw new IllegalStateException("版本内容读取失败");}return result;}
 @Transactional(transactionManager="mysqlTransactionManager") public String create(ResearchVersionController.Create input){long user=actor();
  Map<String,String> splits=new HashMap<>();Set<String> records=new HashSet<>();
  for(var sample:input.samples()){
   String subject=sample.subjectId().strip(); String previous=splits.putIfAbsent(subject,sample.split());
   if(previous!=null&&!previous.equals(sample.split()))throw new BusinessException(HttpStatus.BAD_REQUEST,"同一账号不能同时出现在训练集与测试集");
   if(!records.add(subject+"\u0000"+sample.text().strip()))throw new BusinessException(HttpStatus.BAD_REQUEST,"同一账号存在重复贴文，请先去重");
  }
  return store(user,input.name(),input.source(),input.usageScope(),input.samples(),input.samples().size());
 }
 @Transactional(transactionManager="mysqlTransactionManager") public String createTable(ResearchVersionController.TableMeta meta,ResearchCsv.Grid grid){
  int width=grid.columns().size();var label=meta.labelColumn();var posts=meta.postColumns();
  if(label==null||label<0||label>=width)throw new BusinessException(HttpStatus.BAD_REQUEST,"请选择有效的标签列");
  if(posts==null||posts.isEmpty()||posts.stream().anyMatch(i->i==null||i<0||i>=width||i.equals(label))||new HashSet<>(posts).size()!=posts.size())
   throw new BusinessException(HttpStatus.BAD_REQUEST,"请至少选择一个贴文列，且不能重复或与标签列相同");
  return store(actor(),meta.name(),meta.source(),meta.usageScope(),Map.of("columns",meta.columns(),"originalColumns",grid.columns(),"rows",grid.rows(),"labelColumn",label,"postColumns",posts),grid.rows().size());
 }
 private String store(long user,String name,String source,String scope,Object payload,int count){
  String json,hash;try{json=mapper.writeValueAsString(payload);hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}
   jdbc.queryForObject("SELECT id FROM app_users WHERE id=? FOR UPDATE",Long.class,user);
   var previous=jdbc.queryForList("SELECT version_name FROM research_versions WHERE owner_id=? AND name=? FOR UPDATE",String.class,user,name.strip());
   var max=previous.stream().filter(v->v.matches("(?i)v[0-9]+" )).map(v->new java.math.BigInteger(v.substring(1))).max(java.math.BigInteger::compareTo).orElse(java.math.BigInteger.ZERO);
   String version="v"+max.add(java.math.BigInteger.ONE);
   String id=UUID.randomUUID().toString();try{jdbc.update("INSERT INTO research_versions(id,owner_id,name,version_name,source,usage_scope,checksum,sample_count,samples_json,created_at) VALUES(?,?,?,?,?,?,?,?,?,?)",id,user,name.strip(),version,source.strip(),scope.strip(),hash,count,json,Timestamp.from(Instant.now()));}catch(DuplicateKeyException e){throw new BusinessException(HttpStatus.CONFLICT,"版本分配冲突，请重试");}
  event(id,user,null,"CREATE");return id;
 }
 private void own(String id,long user,boolean lock){var owners=jdbc.queryForList("SELECT owner_id FROM research_versions WHERE id=?"+(lock?" FOR UPDATE":""),Long.class,id);if(owners.isEmpty()||owners.get(0)!=user)throw new BusinessException(HttpStatus.NOT_FOUND,"版本不存在或无管理权限");}
 public List<Map<String,Object>> grants(String id){long user=actor();own(id,user,false);return jdbc.queryForList("SELECT g.user_id AS userId,u.username FROM research_version_grants g JOIN app_users u ON u.id=g.user_id WHERE g.version_id=?",id);}
 @Transactional(transactionManager="mysqlTransactionManager") public void grant(String id,ResearchVersionController.Grant grant){long user=actor();own(id,user,true);
  if(grant.userId()==user)throw new BusinessException(HttpStatus.BAD_REQUEST,"无需向自己授权");
  if(!grant.revoke()){
   Long valid=jdbc.queryForObject("SELECT COUNT(*) FROM app_users WHERE id=? AND is_active=true AND role IN ('researcher','doctor','admin')",Long.class,grant.userId());
   if(valid==null||valid==0)throw new BusinessException(HttpStatus.BAD_REQUEST,"目标账号不是有效的专业账号");
  }
  jdbc.update("DELETE FROM research_version_grants WHERE version_id=? AND user_id=?",id,grant.userId());
  if(!grant.revoke())jdbc.update("INSERT INTO research_version_grants(version_id,user_id) VALUES(?,?)",id,grant.userId());
  event(id,user,grant.userId(),grant.revoke()?"REVOKE":"GRANT");
 }
 private void event(String id,long actor,Long target,String action){jdbc.update("INSERT INTO research_version_events(id,version_id,actor_id,target_id,action,created_at) VALUES(?,?,?,?,?,?)",UUID.randomUUID().toString(),id,actor,target,action,Timestamp.from(Instant.now()));}
}
