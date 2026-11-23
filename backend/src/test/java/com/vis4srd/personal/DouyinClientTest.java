package com.vis4srd.personal;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class DouyinClientTest {
    private static final String WEB = "https://example.com/mobile/sync";
    private static final String ANDROID = "https://example.com/mobile/douyin/android-callback";

    private MockEnvironment config() {
        return new MockEnvironment().withProperty("DOUYIN_CLIENT_KEY", "test")
                .withProperty("DOUYIN_CLIENT_SECRET", "test")
                .withProperty("DOUYIN_REDIRECT_URI", WEB)
                .withProperty("DOUYIN_TOKEN_KEY", Base64.getEncoder().encodeToString(new byte[32]));
    }

    @Test void keepsWebRedirectAndSelectsAndroidRedirectOnlyForAndroid() {
        var client = new DouyinClient(config().withProperty("DOUYIN_ANDROID_REDIRECT_URI", ANDROID), new ObjectMapper());
        assertThat(client.androidRedirect()).contains(ANDROID);
        assertThat(query(client.authorize("nonce", false))).contains("redirect_uri=" + WEB + "&state=nonce");
        assertThat(query(client.authorize("nonce", true))).contains("redirect_uri=" + ANDROID + "&state=nonce");
    }

    @Test void missingOrInvalidAndroidRedirectDoesNotDisableWeb() {
        for (String redirect : new String[]{"", "http://example.com/mobile/douyin/android-callback", WEB, ANDROID + "?next=1"}) {
            var client = new DouyinClient(config().withProperty("DOUYIN_ANDROID_REDIRECT_URI", redirect), new ObjectMapper());
            assertThat(client.available()).isTrue();
            assertThat(client.androidRedirect()).isEmpty();
        }
    }

    private String query(String url) {
        return URLDecoder.decode(URI.create(url).getRawQuery(), StandardCharsets.UTF_8);
    }
}