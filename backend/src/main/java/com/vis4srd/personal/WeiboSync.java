package com.vis4srd.personal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.*;
import com.vis4srd.common.exception.BusinessException;
import com.vis4srd.common.result.ApiResponse;
import com.vis4srd.common.security.SecurityUtils;
import org.springframework.beans.factory.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

@RestController @RequestMapping("/api/me/sync/weibo")
class WeiboSync {
    private static final org.slf4j.Logger log=org.slf4j.LoggerFactory.getLogger(WeiboSync.class);
    private final JdbcTemplate db;private final WeiboGateway gateway;private final TransactionTemplate tx;
    @Value("${WEIBO_SYNC_INTERVAL_SECONDS:300}") private int interval=300;
    WeiboSync(@Qualifier("mysqlJdbcTemplate") JdbcTemplate db,WeiboGateway gateway,@Qualifier("mysqlTransactionManager") PlatformTransactionManager manager){this.db=db;this.gateway=gateway;tx=new TransactionTemplate(manager);}
    private static long owner(){var user=SecurityUtils.requireCurrentUser();if(!"user".equals(user.getRole()))throw new BusinessException(HttpStatus.FORBIDDEN,"请使用个人账号");return user.getUserId();}
    @GetMapping ApiResponse<?> state(){return ApiResponse.ok(Map.of("available",gateway.available(),"connection",db.queryForList("SELECT status,last_success AS lastSuccess,page_no AS pageNo FROM weibo_connections WHERE owner_id=?",owner())));}
    @PostMapping("/start") ApiResponse<?> start(){
        long owner=owner(),now=System.currentTimeMillis();if(!gateway.available())throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE,"微博同步尚未配置");
        try{var d=gateway.start();String code=WeiboGateway.required(d,"user_code");URI uri=URI.create(WeiboGateway.required(d,"verification_uri"));
            if(!"https".equals(uri.getScheme())||!"open.weibo.com".equalsIgnoreCase(uri.getHost())||uri.getUserInfo()!=null)throw new IllegalStateException();
            var pending=JsonNodeFactory.instance.objectNode().put("device_code",WeiboGateway.required(d,"device_code"));
            int poll=Math.max(5,d.path("interval").asInt(5));long expires=now+1000L*Math.min(900,d.path("expires_in").asInt(600));
            db.update("""
                    INSERT INTO weibo_connections VALUES(?,?,'','PENDING',?,0,1,'0',false,?,?)
                    ON DUPLICATE KEY UPDATE credentials=VALUES(credentials),uid='',status='PENDING',next_run=VALUES(next_run),last_success=0,
                    page_no=1,page_cursor='0',initial_done=false,auth_expires=VALUES(auth_expires),poll_seconds=VALUES(poll_seconds)
                    """,owner,gateway.seal(owner,pending),now+1000L*poll,expires,poll);
            return ApiResponse.ok(Map.of("url",uri.toASCIIString(),"userCode",code));
        }catch(Exception e){throw new BusinessException(HttpStatus.BAD_GATEWAY,"微博设备授权暂不可用");}
    }
    @DeleteMapping ApiResponse<?> stop(@RequestParam(defaultValue="false") boolean erase){long owner=owner();tx.executeWithoutResult(s->{db.update("DELETE FROM weibo_connections WHERE owner_id=?",owner);if(erase)db.update("DELETE FROM personal_posts WHERE owner_id=? AND source='微博同步'",owner);});return ApiResponse.ok(null);}
    @Scheduled(fixedDelay=3000) public void tick(){if(!gateway.available())return;long now=System.currentTimeMillis();
        for(long id:db.queryForList("SELECT owner_id FROM weibo_connections WHERE next_run<=? ORDER BY next_run LIMIT 4",Long.class,now)){
            try{sync(id,now);}catch(Exception e){log.warn("Weibo sync deferred ({})",e.getClass().getSimpleName());db.update("UPDATE weibo_connections SET next_run=?,status=CASE WHEN status='PENDING' THEN status ELSE 'RETRY' END WHERE owner_id=? AND next_run<=?",now+60_000,id,now);}
        }
    }
    void sync(long id,long now){tx.executeWithoutResult(s->{var rows=db.queryForList("SELECT * FROM weibo_connections WHERE owner_id=? AND next_run<=? FOR UPDATE",id,now);if(rows.isEmpty())return;
        var r=rows.get(0);boolean pending="PENDING".equals(r.get("status"));
        if(pending&&((Number)r.get("auth_expires")).longValue()<=now){reauthorize(id);return;}
        try{var credential=gateway.open(id,(String)r.get("credentials"));String uid=(String)r.get("uid");
            if(pending||credential.path("expires").asLong()<now+60_000){
                String grant=pending?"device_code":"refresh_token";var fresh=gateway.token(grant,WeiboGateway.required(credential,grant));
                String access=WeiboGateway.required(fresh,"access_token");String verified=gateway.uid(access);
                if(!pending&&!uid.equals(verified)){reauthorize(id);return;}
                var updated=JsonNodeFactory.instance.objectNode().put("access_token",access).put("refresh_token",fresh.path("refresh_token").asText(credential.path("refresh_token").asText())).put("expires",now+1000L*fresh.path("expires_in").asLong());
                var flags=updated.putArray("flags");if(!pending&&credential.path("flags").isArray())credential.path("flags").forEach(flags::add);
                db.update("UPDATE weibo_connections SET uid=?,credentials=?,status='SYNCING',next_run=? WHERE owner_id=?",verified,gateway.seal(id,updated),now,id);return;
            }
            String access=WeiboGateway.required(credential,"access_token");Set<String> flags=new HashSet<>();credential.path("flags").forEach(f->flags.add(f.asText()));
            if(flags.isEmpty()){flags.addAll(gateway.flags(access));var a=((ObjectNode)credential).putArray("flags");flags.forEach(a::add);db.update("UPDATE weibo_connections SET credentials=? WHERE owner_id=?",gateway.seal(id,credential),id);}
            int page=((Number)r.get("page_no")).intValue();String cursor=(String)r.get("page_cursor");boolean initial=!Boolean.TRUE.equals(r.get("initial_done"))&&!"1".equals(String.valueOf(r.get("initial_done")));
            Map<String,Object> args=new HashMap<>();if(flags.contains("uid"))args.put("uid",uid);if(flags.contains("count"))args.put("count",20);
            if(flags.contains("cursor"))args.put("cursor",cursor);else if(flags.contains("page"))args.put("page",page);
            if(flags.contains("isGetLongText"))args.put("isGetLongText",1);
            var response=gateway.timeline(access,args);var posts=response.path("statuses");if(!posts.isArray())throw new IllegalStateException("invalid timeline");
            for(var p:posts){var author=p.path("user");if(!uid.equals(author.path("idstr").asText(author.path("id").asText()))||p.path("idstr").asText().isBlank())throw new IllegalStateException("unverified post");}
            int inserted=0;for(var p:posts)if(store(id,uid,p,now))inserted++;
            String next=response.path("next_cursor").asText("0");boolean more=initial&&posts.size()>0&&page<200&&(flags.contains("cursor")?!next.equals("0")&&!next.equals(cursor):flags.contains("page")&&(inserted>0||page==1));
            db.update("UPDATE weibo_connections SET page_no=?,page_cursor=?,initial_done=?,last_success=?,next_run=?,status=? WHERE owner_id=?",more?page+1:1,more?next:"0",!more,now,now+(more?5000:1000L*Math.max(60,interval)),more?"SYNCING":"CURRENT",id);
        }catch(WeiboGateway.Failure e){
            if(pending&&(e.code.equals("authorization_pending")||e.code.equals("slow_down"))){int poll=((Number)r.get("poll_seconds")).intValue()+(e.code.equals("slow_down")?5:0);db.update("UPDATE weibo_connections SET next_run=?,poll_seconds=? WHERE owner_id=?",now+1000L*poll,poll,id);}
            else if(e.status==401||Set.of("access_denied","expired_token","invalid_grant").contains(e.code))reauthorize(id);
            else if(e.status==403&&!pending)db.update("UPDATE weibo_connections SET status='NO_PERMISSION',next_run=? WHERE owner_id=?",Long.MAX_VALUE,id);
            else throw e;
        }catch(Exception e){throw new IllegalStateException("Weibo sync failed",e);}
    });}
    private void reauthorize(long id){db.update("UPDATE weibo_connections SET credentials='',status='REAUTHORIZE',next_run=? WHERE owner_id=?",Long.MAX_VALUE,id);}
    private boolean store(long owner,String uid,JsonNode p,long now)throws Exception{
        String item=WeiboGateway.required(p,"idstr"), hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(("weibo:"+uid+":"+item).getBytes(StandardCharsets.UTF_8)));
        var existing=db.queryForList("SELECT id FROM personal_posts WHERE owner_id=? AND content_hash=?",String.class,owner,hash);
        String id=existing.isEmpty()?UUID.randomUUID().toString():existing.get(0);String text=p.path("longText").path("longTextContent").asText(p.path("text").asText());
        Timestamp published=null;String date=p.path("created_at").asText();try{published=Timestamp.from(ZonedDateTime.parse(date,DateTimeFormatter.ofPattern("EEE MMM dd HH:mm:ss Z yyyy",Locale.ENGLISH)).toInstant());}catch(java.time.format.DateTimeParseException e){try{published=Timestamp.from(Instant.parse(date));}catch(java.time.format.DateTimeParseException ignored){}}
        db.update("INSERT INTO personal_posts VALUES(?,?,?,'微博同步',?,?,?) ON DUPLICATE KEY UPDATE content=VALUES(content),published_at=VALUES(published_at)",id,owner,text,published,new Timestamp(now),hash);
        db.update("INSERT INTO social_post_details VALUES(?,?,?) ON DUPLICATE KEY UPDATE metadata=VALUES(metadata)",id,item,p.toString());
        var metrics=JsonNodeFactory.instance.objectNode();for(String k:List.of("comments_count","reposts_count","attitudes_count")){var n=p.path(k);if(n.isIntegralNumber()&&n.canConvertToLong()&&n.asLong()>=0)metrics.set(k,n);}
        if(!metrics.isEmpty()&&db.queryForList("SELECT sampled_at FROM social_post_metrics WHERE post_id=? AND sampled_at>? LIMIT 1",Long.class,id,now-86400000).isEmpty())db.update("INSERT INTO social_post_metrics VALUES(?,?,?)",id,now,metrics.toString());return existing.isEmpty();
    }
}