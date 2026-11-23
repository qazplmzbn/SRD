package com.vis4srd.personal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.vis4srd.common.exception.BusinessException;
import com.vis4srd.common.result.ApiResponse;
import com.vis4srd.common.security.SecurityUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.util.*;

@RestController @RequestMapping("/api/me/sync/douyin") @EnableScheduling
public class DouyinSync {
    private static final int INITIAL_PAGE_LIMIT=4;
    private static final long METRICS_INTERVAL_MS=86_400_000;
    private static final List<String> DETAILS=List.of("item_id","video_id","title","create_time","share_url","cover","media_type","video_status","is_top");
    private static final List<String> METRICS=List.of("digg_count","comment_count","share_count","play_count","download_count");
    private static final org.slf4j.Logger log=org.slf4j.LoggerFactory.getLogger(DouyinSync.class);
    private final JdbcTemplate db; private final DouyinClient client; private final TransactionTemplate tx;
    @org.springframework.beans.factory.annotation.Value("${DOUYIN_SYNC_INTERVAL_SECONDS:60}")
    private int intervalSeconds=60;
    DouyinSync(@Qualifier("mysqlJdbcTemplate") JdbcTemplate db,DouyinClient client,@Qualifier("mysqlTransactionManager") PlatformTransactionManager manager){this.db=db;this.client=client;tx=new TransactionTemplate(manager);}
    private long owner(){var u=SecurityUtils.requireCurrentUser();if(!"user".equals(u.getRole()))throw new BusinessException(HttpStatus.FORBIDDEN,"请使用个人账号");return u.getUserId();}
    @GetMapping public ApiResponse<?> state(){long id=owner();var rows=db.queryForList("""
            SELECT c.status,c.last_success AS lastSuccess,c.page_cursor AS pageCursor,
                   v.pages AS initialPages,v.item_count AS initialItems,v.oldest_create_time AS oldestCreateTime,
                   v.newest_create_time AS newestCreateTime,v.upstream_has_more AS upstreamHasMore,v.finished AS initialFinished
            FROM douyin_connections c LEFT JOIN douyin_sync_coverage v ON v.owner_id=c.owner_id AND v.generation=c.generation
            WHERE c.owner_id=?
            """,id);return ApiResponse.ok(Map.of("available",client.available(),"connection",rows,"androidRedirect",client.androidRedirect().orElse("")));}
    @PostMapping("/start") public ApiResponse<?> start(@RequestParam(defaultValue="false") boolean android){long id=owner();if(!client.available()||(android&&client.androidRedirect().isEmpty()))throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE,"当前端的抖音授权尚未开放");String nonce=UUID.randomUUID().toString();db.update("INSERT INTO douyin_auth_state(owner_id,nonce,expires) VALUES(?,?,?) ON DUPLICATE KEY UPDATE nonce=VALUES(nonce),expires=VALUES(expires)",id,nonce,System.currentTimeMillis()+600000);return ApiResponse.ok(Map.of("url",client.authorize(nonce,android)));}
    record Callback(String code,String state){}
    @PostMapping("/finish") public ApiResponse<?> finish(@RequestBody Callback form){long id=owner();if(!client.available()||form.code()==null||form.code().isBlank()||form.code().length()>1024)throw new BusinessException(HttpStatus.BAD_REQUEST,"授权无效");
        return tx.execute(t->{if(db.update("DELETE FROM douyin_auth_state WHERE owner_id=? AND nonce=? AND expires>?",id,form.state(),System.currentTimeMillis())!=1)throw new BusinessException(HttpStatus.BAD_REQUEST,"授权已失效，请重新连接");
            try{var tokens=client.exchange(form.code());String encrypted=client.seal(tokens,id);db.update("DELETE FROM douyin_connections WHERE owner_id=?",id);db.update("INSERT INTO douyin_connections(owner_id,open_id,credentials,generation,page_cursor,next_run,status,last_success) VALUES(?,?,?,?,0,0,'SYNCING',0)",id,tokens.account(),encrypted,UUID.randomUUID().toString());}catch(Exception e){throw new BusinessException(HttpStatus.BAD_GATEWAY,"未获得作品读取授权，请检查应用权限并重新连接");}return ApiResponse.ok(null);});}
    @DeleteMapping public ApiResponse<?> disconnect(@RequestParam(defaultValue="false") boolean erase){long id=owner();tx.executeWithoutResult(t->{db.update("DELETE FROM douyin_auth_state WHERE owner_id=?",id);db.update("DELETE FROM douyin_connections WHERE owner_id=?",id);db.update("DELETE FROM douyin_sync_coverage WHERE owner_id=?",id);if(erase)db.update("DELETE FROM personal_posts WHERE owner_id=? AND source='抖音同步'",id);});return ApiResponse.ok(null);}

    @Scheduled(fixedDelay=3000) public void tick(){
        if(!client.available())return;
        long now=System.currentTimeMillis();
        for(long id:db.queryForList("SELECT owner_id FROM douyin_connections WHERE next_run<=? ORDER BY next_run LIMIT 8",Long.class,now)){
            try{sync(id,now);}catch(RuntimeException e){
                log.warn("Douyin sync persistence failed ({})",e.getClass().getSimpleName());
                db.update("UPDATE douyin_connections SET status='RETRY',next_run=? WHERE owner_id=? AND next_run<=?",now+300000,id,now);
            }
        }
    }
    void sync(long id,long now){tx.executeWithoutResult(txStatus->{var rows=db.queryForList("SELECT * FROM douyin_connections WHERE owner_id=? AND next_run<=? FOR UPDATE",id,now);if(rows.isEmpty())return;var row=rows.get(0);
        String generation=(String)row.get("generation");
        var coverage=db.queryForList("SELECT * FROM douyin_sync_coverage WHERE owner_id=? AND generation=?",id,generation);
        var progress=coverage.isEmpty()?Map.<String,Object>of():coverage.get(0);
        Object finished=progress.get("finished");
        boolean initial=!(Boolean.TRUE.equals(finished)||(finished instanceof Number n&&n.intValue()!=0));
        long cursor=progress.isEmpty()||!initial?0:((Number)row.get("page_cursor")).longValue();
        JsonNode page;DouyinClient.Tokens tokens;
        try{tokens=client.unseal((String)row.get("credentials"),id);if(tokens.expires()<now+60000){tokens=client.refresh(tokens.refresh());if(!tokens.account().equals(row.get("open_id")))throw new DouyinClient.Reauthorize();db.update("UPDATE douyin_connections SET credentials=? WHERE owner_id=?",client.seal(tokens,id),id);}page=client.videos(tokens,cursor);
            if(!page.path("list").isArray()||!page.path("has_more").isBoolean()||(page.path("has_more").asBoolean()&&page.path("cursor").asLong(-1)<=cursor))throw new IllegalStateException("invalid page");
            for(var post:page.get("list"))if(post.path("item_id").asText().isBlank()||post.path("item_id").asText().length()>255)throw new IllegalStateException("invalid item identifier");
        }catch(DouyinClient.Reauthorize e){db.update("UPDATE douyin_connections SET status='REAUTHORIZE',next_run=? WHERE owner_id=?",Long.MAX_VALUE,id);return;}catch(Exception e){log.warn("Douyin list sync failed ({})",e.getClass().getSimpleName());db.update("UPDATE douyin_connections SET status='RETRY',next_run=? WHERE owner_id=?",now+300000,id);return;}
        Long oldest=(Long)progress.get("oldest_create_time"),newest=(Long)progress.get("newest_create_time");
        Map<String,String> due=new LinkedHashMap<>();
        for(var post:page.get("list")){
            long time=post.path("create_time").asLong(0);
            String postId=savePost(id,(String)row.get("open_id"),post,time,now);
            if(tokens.metrics()&&due.size()<20&&db.queryForList("SELECT next_check FROM douyin_metric_checks WHERE post_id=? AND next_check>?",Long.class,postId,now).isEmpty())due.put(post.path("item_id").asText(),postId);
            if(time>0){oldest=oldest==null?time:Math.min(oldest,time);newest=newest==null?time:Math.max(newest,time);}
        }
        if(!due.isEmpty())sample(tokens,due,now);
        int pages=((Number)progress.getOrDefault("pages",0)).intValue()+1;
        boolean upstreamMore=page.path("has_more").asBoolean();
        boolean advance=initial&&upstreamMore&&pages<INITIAL_PAGE_LIMIT;
        if(initial)db.update("""
                INSERT INTO douyin_sync_coverage(owner_id,generation,pages,item_count,oldest_create_time,newest_create_time,upstream_has_more,finished)
                VALUES(?,?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE generation=VALUES(generation),pages=VALUES(pages),item_count=VALUES(item_count),
                oldest_create_time=VALUES(oldest_create_time),newest_create_time=VALUES(newest_create_time),upstream_has_more=VALUES(upstream_has_more),finished=VALUES(finished)
                """,id,generation,pages,((Number)progress.getOrDefault("item_count",0)).intValue()+page.get("list").size(),oldest,newest,upstreamMore,!advance);
        db.update("UPDATE douyin_connections SET page_cursor=?,next_run=?,status=?,last_success=? WHERE owner_id=?",advance?page.get("cursor").asLong():0,advance?now+1000:now+1000L*Math.max(60,intervalSeconds),advance?"SYNCING":"CURRENT",now,id);
    });}
    private void sample(DouyinClient.Tokens tokens,Map<String,String> due,long now){
        JsonNode list=null;
        try{list=client.statistics(tokens,List.copyOf(due.keySet()));}catch(Exception ex){log.warn("Douyin video.data unavailable ({})",ex.getClass().getSimpleName());}
        Set<String> ok=new HashSet<>();
        if(list!=null&&list.isArray())for(var v:list){String postId=due.get(v.path("item_id").asText());if(postId==null)continue;
            var counts=JsonNodeFactory.instance.objectNode();for(String k:METRICS){var n=v.path("statistics").path(k);if(n.isIntegralNumber()&&n.canConvertToLong()&&n.asLong()>=0)counts.set(k,n);}
            if(counts.isEmpty())continue;
            db.update("INSERT INTO social_post_metrics(post_id,sampled_at,metrics) VALUES(?,?,?) ON DUPLICATE KEY UPDATE metrics=VALUES(metrics)",postId,now,counts.toString());ok.add(postId);
        }
        for(String postId:due.values())db.update("INSERT INTO douyin_metric_checks VALUES(?,?,?) ON DUPLICATE KEY UPDATE next_check=VALUES(next_check),result=VALUES(result)",postId,now+(ok.contains(postId)?METRICS_INTERVAL_MS:900_000),ok.contains(postId)?"OK":"UNAVAILABLE");
    }
    private String savePost(long owner,String account,JsonNode post,long time,long now){
        String item=post.path("item_id").asText(),hash=identity(account+":"+item);
        db.update("INSERT INTO personal_posts(id,owner_id,content,source,published_at,imported_at,content_hash) VALUES(?,?,?,'抖音同步',?,?,?) ON DUPLICATE KEY UPDATE content=VALUES(content),published_at=VALUES(published_at)",UUID.randomUUID().toString(),owner,post.path("title").asText(""),time>0?new Timestamp(time*1000):null,new Timestamp(now),hash);
        String postId=db.queryForObject("SELECT id FROM personal_posts WHERE owner_id=? AND content_hash=?",String.class,owner,hash);
        var details=JsonNodeFactory.instance.objectNode();
        for(String key:DETAILS)if(post.hasNonNull(key))details.set(key,post.get(key));
        db.update("INSERT INTO social_post_details(post_id,external_id,metadata) VALUES(?,?,?) ON DUPLICATE KEY UPDATE metadata=VALUES(metadata)",postId,item,details.toString());
        var metrics=JsonNodeFactory.instance.objectNode();
        var stats=post.path("statistics");
        for(String key:METRICS){var value=stats.hasNonNull(key)?stats.get(key):post.path(key);if(value.isIntegralNumber()&&value.canConvertToLong()&&value.asLong()>=0)metrics.set(key,value);}
        if(!metrics.isEmpty()&&db.queryForList("SELECT sampled_at FROM social_post_metrics WHERE post_id=? AND sampled_at>? LIMIT 1",Long.class,postId,now-METRICS_INTERVAL_MS).isEmpty())
            db.update("INSERT INTO social_post_metrics(post_id,sampled_at,metrics) VALUES(?,?,?)",postId,now,metrics.toString());
        return postId;
    }
    private static String identity(String key){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(("douyin:"+key).getBytes(StandardCharsets.UTF_8)));}catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
}