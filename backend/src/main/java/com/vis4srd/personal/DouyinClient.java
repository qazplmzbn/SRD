package com.vis4srd.personal;

import com.fasterxml.jackson.databind.*;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;
import javax.crypto.Cipher;
import javax.crypto.spec.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

@Component
class DouyinClient {
    private static final String BASE="https://open.douyin.com";
    private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final String key,secret,redirect,androidRedirect;
    private final byte[] encryptionKey;
    private final boolean requestMetrics;
    private final ObjectMapper json;
    DouyinClient(Environment env,ObjectMapper json){
        this.json=json;key=env.getProperty("DOUYIN_CLIENT_KEY","");secret=env.getProperty("DOUYIN_CLIENT_SECRET","");redirect=env.getProperty("DOUYIN_REDIRECT_URI","");
        androidRedirect=env.getProperty("DOUYIN_ANDROID_REDIRECT_URI","");
        encryptionKey=Base64.getDecoder().decode(env.getProperty("DOUYIN_TOKEN_KEY",""));
        requestMetrics=env.getProperty("DOUYIN_VIDEO_DATA_ENABLED",Boolean.class,false);
    }
    boolean available(){return !key.isBlank()&&!secret.isBlank()&&redirect.startsWith("https://")&&encryptionKey.length==32;}
    Optional<String> androidRedirect(){
        try {
            URI uri=URI.create(androidRedirect);
            if(available()&&"https".equals(uri.getScheme())&&uri.getHost()!=null&&uri.getPort()==-1&&uri.getUserInfo()==null
                &&"/mobile/douyin/android-callback".equals(uri.getRawPath())&&uri.getRawQuery()==null&&uri.getRawFragment()==null) return Optional.of(androidRedirect);
        } catch(IllegalArgumentException ignored) {}
        return Optional.empty();
    }
    String authorize(String nonce,boolean android){return UriComponentsBuilder.fromHttpUrl(BASE+"/platform/oauth/connect/").queryParam("client_key",key).queryParam("response_type","code").queryParam("scope",requestMetrics?"video.list,video.data":"video.list").queryParam("redirect_uri","{redirect}").queryParam("state",nonce).encode().buildAndExpand(android?androidRedirect().orElseThrow():redirect).toUriString();}
    record Tokens(String account,String access,String refresh,long expires,boolean metrics){Tokens(String account,String access,String refresh,long expires){this(account,access,refresh,expires,false);}}
    Tokens exchange(String code)throws Exception{return token("access_token",Map.of("client_key",key,"client_secret",secret,"code",code,"grant_type","authorization_code"));}
    Tokens refresh(String token)throws Exception{return token("refresh_token",Map.of("client_key",key,"refresh_token",token,"grant_type","refresh_token"));}
    private Tokens token(String path,Map<String,String> params)throws Exception{
        String body=params.entrySet().stream().map(e->enc(e.getKey())+"="+enc(e.getValue())).collect(Collectors.joining("&"));
        var data=send(HttpRequest.newBuilder(URI.create(BASE+"/oauth/"+path+"/")).header("Content-Type","application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString(body)));
        if(!Arrays.asList(data.path("scope").asText().split(",")).contains("video.list"))throw new Reauthorize();
        for(String field:List.of("open_id","access_token","refresh_token"))if(data.path(field).asText().isBlank())throw new Reauthorize();
        return new Tokens(data.get("open_id").asText(),data.get("access_token").asText(),data.get("refresh_token").asText(),System.currentTimeMillis()+1000L*data.path("expires_in").asLong(),requestMetrics&&Arrays.asList(data.path("scope").asText().split(",")).contains("video.data"));
    }
    JsonNode videos(Tokens t,long cursor)throws Exception{return send(HttpRequest.newBuilder(URI.create(BASE+"/video/list/?open_id="+enc(t.account())+"&cursor="+cursor+"&count=20")).header("access-token",t.access()).GET());}
    static class Reauthorize extends Exception {}
    JsonNode statistics(Tokens t,List<String> ids)throws Exception{
        if(!requestMetrics||!t.metrics()||ids.isEmpty()||ids.size()>20)throw new IllegalStateException();
        return send(HttpRequest.newBuilder(URI.create(BASE+"/video/data/?open_id="+enc(t.account())))
                .header("access-token",t.access()).header("Content-Type","application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(Map.of("item_ids",ids))))).path("list");
    }
    private JsonNode send(HttpRequest.Builder request)throws Exception{
        var response=http.send(request.timeout(Duration.ofSeconds(10)).build(),HttpResponse.BodyHandlers.ofString());
        if(response.statusCode()==401)throw new Reauthorize();
        if(response.statusCode()!=200)throw new java.io.IOException("平台暂不可用");
        var data=json.readTree(response.body()).path("data");int error=data.path("error_code").asInt(-1);
        if(Set.of(2190002,2190008,10008).contains(error))throw new Reauthorize();
        if(error!=0)throw new java.io.IOException("平台未返回成功结果");return data;
    }
    private static String enc(String s){return URLEncoder.encode(s,StandardCharsets.UTF_8);}
    String seal(Tokens tokens,long owner)throws Exception{
        byte[] iv=new byte[12];new SecureRandom().nextBytes(iv);var c=cipher(Cipher.ENCRYPT_MODE,iv,owner);
        return Base64.getEncoder().encodeToString(iv)+":"+Base64.getEncoder().encodeToString(c.doFinal(json.writeValueAsBytes(tokens)));
    }
    Tokens unseal(String value,long owner)throws Exception{String[] p=value.split(":");return json.readValue(cipher(Cipher.DECRYPT_MODE,Base64.getDecoder().decode(p[0]),owner).doFinal(Base64.getDecoder().decode(p[1])),Tokens.class);}
    private Cipher cipher(int mode,byte[] iv,long owner)throws Exception{var c=Cipher.getInstance("AES/GCM/NoPadding");c.init(mode,new SecretKeySpec(encryptionKey,"AES"),new GCMParameterSpec(128,iv));c.updateAAD(Long.toString(owner).getBytes(StandardCharsets.UTF_8));return c;}
}