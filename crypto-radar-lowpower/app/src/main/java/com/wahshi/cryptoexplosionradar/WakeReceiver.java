package com.wahshi.cryptoexplosionradar;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

public class WakeReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        SharedPreferences prefs = context.getSharedPreferences("free_whale_radar_v3", Context.MODE_PRIVATE);
        if (!prefs.getBoolean("radar_enabled", false)) return;

        Intent service = new Intent(context, ScannerService.class).setAction(ScannerService.ACTION_WAKE);
        try {
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(service);
            else context.startService(service);
        } catch (Exception first) {
            try { context.startService(service); } catch (Exception ignored) {}
        }
    }
}
