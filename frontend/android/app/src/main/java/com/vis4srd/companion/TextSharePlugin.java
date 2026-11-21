package com.vis4srd.companion;

import android.content.Intent;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.annotation.CapacitorPlugin;
import java.util.UUID;

@CapacitorPlugin(name = "TextShare")
public class TextSharePlugin extends Plugin {
    @Override
    protected void handleOnNewIntent(Intent intent) {
        if (intent == null || !Intent.ACTION_SEND.equals(intent.getAction()) || !"text/plain".equals(intent.getType())) return;
        JSObject payload = new JSObject();
        payload.put("id", UUID.randomUUID().toString());
        try {
            CharSequence text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT);
            if (text == null || text.length() == 0) payload.put("error", "分享没有文字或链接，请在微博复制内容后粘贴。");
            else if (text.length() > 20000) payload.put("error", "分享超过 20000 字，请缩短后重试。");
            else payload.put("text", text.toString());
        } catch (RuntimeException error) {
            payload.put("error", "无法读取分享内容，请复制后粘贴。");
        }
        // Retain until the web layer subscribes, including cold launch.
        notifyListeners("sharedText", payload, true);
    }
}
