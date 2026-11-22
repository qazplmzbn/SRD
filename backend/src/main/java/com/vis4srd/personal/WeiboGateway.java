package com.vis4srd.personal;

import com.fasterxml.jackson.databind.*;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import javax.crypto.Cipher;
import javax.crypto.spec.*;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.*;

/** Wire contract of @weibo-ai/weibo-cli 0.9.9. No ambient CLI/keychain credentials. */
@Component
class WeiboGateway {
    private final ObjectMapper json;
    private final byte[] key;
    private final boolean enabled;
    private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    WeiboGateway(ObjectMapper json, Environment env){
        this.json=json;key=Base64.getDecoder().decode(env.getProperty("WEIBO_TOKEN_KEY",""));
        enabled=env.getProperty("WEIBO_SYNC_ENABLED",Boolean.class,false)&&key.length==32;
    }
    boolean available(){return enabled;}
    static class Failure extends RuntimeException {
        final int status;final String code;
        Failure(int status,String code){super("Weibo request failed");this.status=status;this.code=code;}
    }
    private JsonNode call(String path,String access,Object body)throws Exception{
        var b=HttpRequest.newBuilder(URI.create("https://open.weibo.com/cli/api"+path)).timeout(Duration.ofSeconds(12)).header("Accept","application/json");
        if(access!=null)b.header("Authorization","Bearer "+access);
        if(body!=null)b.header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
        var r=http.send(b.build(),HttpResponse.BodyHandlers.ofInputStream());
        try(var stream=r.body()){
            var bytes=stream.readNBytes(4_194_305);if(bytes.length>4_194_304)throw new IllegalStateException("oversize response");
            if(r.statusCode()/100!=2){String code="";try{var err=json.readTree(bytes).path("error");code=err.isTextual()?err.textValue():err.path("code").asText();}catch(Exception ignored){}throw new Failure(r.statusCode(),code);}
            return json.readTree(bytes);
        }
    }
    JsonNode start()throws Exception{return call("/oauth/device/code",null,Map.of("client_id","weibo-cli","device_name","VIS4SRD"));}
    JsonNode token(String grant,String secret)throws Exception{
        boolean device=grant.equals("device_code");return call("/oauth/token",null,Map.of("client_id","weibo-cli","grant_type",device?"urn:ietf:params:oauth:grant-type:device_code":"refresh_token",grant,secret));
    }
    String uid(String access)throws Exception{return required(call("/oauth/userinfo",access,null),"user_id");}
    Set<String> flags(String access)throws Exception{
        var flags=call("/cli/commands/statuses/user_timeline%2Fbiz",access,null).path("command").path("flags");
        if(!flags.isArray())throw new IllegalStateException("unknown command schema");
        Set<String> names=new HashSet<>();flags.forEach(f->names.add(f.path("name").asText()));return names;
    }
    JsonNode timeline(String access,Map<String,Object> args)throws Exception{
        var result=call("/cli/invoke",access,Map.of("group","statuses","action","user_timeline/biz","args",args)).path("result");
        return result.has("statuses")?result:result.path("data");
    }
    static String required(JsonNode n,String k){String s=n.path(k).asText("");if(s.isBlank())throw new IllegalStateException("missing field");return s;}
    String seal(long owner,JsonNode value)throws Exception{
        byte[] iv=new byte[12];new SecureRandom().nextBytes(iv);var e=Base64.getEncoder();return e.encodeToString(iv)+":"+e.encodeToString(cipher(owner,iv,Cipher.ENCRYPT_MODE).doFinal(json.writeValueAsBytes(value)));
    }
    JsonNode open(long owner,String value)throws Exception{
        var pair=value.split(":");var d=Base64.getDecoder();return json.readTree(cipher(owner,d.decode(pair[0]),Cipher.DECRYPT_MODE).doFinal(d.decode(pair[1])));
    }
    private Cipher cipher(long owner,byte[] iv,int mode)throws Exception{
        var c=Cipher.getInstance("AES/GCM/NoPadding");c.init(mode,new SecretKeySpec(key,"AES"),new GCMParameterSpec(128,iv));c.updateAAD(("weibo:"+owner).getBytes(StandardCharsets.UTF_8));return c;
    }
}