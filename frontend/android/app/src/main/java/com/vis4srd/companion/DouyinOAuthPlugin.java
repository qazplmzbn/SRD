package com.vis4srd.companion;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

@CapacitorPlugin(name = "DouyinOAuth")
public class DouyinOAuthPlugin extends Plugin {
    private static final String PATH = "/mobile/douyin/android-callback";

    private String redirectUrl() {
        return "https://" + getContext().getString(R.string.douyin_callback_host) + PATH;
    }

    @PluginMethod
    public void getRedirect(PluginCall call) {
        JSObject result = new JSObject();
        result.put("url", redirectUrl());
        call.resolve(result);
    }

    @PluginMethod
    public void open(PluginCall call) {
        Uri uri = Uri.parse(call.getString("url", ""));
        if (!uri.isHierarchical() || uri.getFragment() != null
                || !"https://open.douyin.com/platform/oauth/connect/".equals(uri.buildUpon().clearQuery().build().toString())
                || !redirectUrl().equals(uri.getQueryParameter("redirect_uri"))) {
            call.reject("抖音授权链接无效");
            return;
        }
        try {
            getActivity().startActivity(new Intent(Intent.ACTION_VIEW, uri));
            call.resolve();
        } catch (ActivityNotFoundException e) {
            call.reject("找不到可打开抖音授权的浏览器");
        }
    }

    @Override
    protected void handleOnNewIntent(Intent intent) {
        if (intent == null || !Intent.ACTION_VIEW.equals(intent.getAction())) return;
        Uri uri = intent.getData();
        if (uri == null || !uri.isHierarchical() || uri.getFragment() != null
                || !redirectUrl().equals(uri.buildUpon().clearQuery().build().toString())) return;
        JSObject result = new JSObject();
        result.put("url", uri.toString());
        notifyListeners("callback", result, true);
    }
}