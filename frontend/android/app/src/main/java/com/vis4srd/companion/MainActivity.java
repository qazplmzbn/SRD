package com.vis4srd.companion;

import com.getcapacitor.BridgeActivity;
import android.os.Bundle;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(TextSharePlugin.class);
        super.onCreate(savedInstanceState);
    }
}
