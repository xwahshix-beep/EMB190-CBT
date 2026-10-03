package com.wahshi.cryptoexplosionradar;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

public class RestartReceiver extends BroadcastReceiver {
    public static final String ACTION_RESTART = "com.wahshi.cryptoexplosionradar.RESTART";

    @Override public void onReceive(Context context, Intent intent) {
        SharedPreferences p = context.getSharedPreferences("free_whale_radar_v3", Context.MODE_PRIVATE);
        if (!p.getBoolean("radar_enabled", false)) return;

        Intent service = new Intent(context, ScannerService.class).setAction(ScannerService.ACTION_START);
        try {
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(service);
            else context.startService(service);
        } catch (Exception ignored) {
            // START_STICKY remains the primary recovery path; this receiver is an additional watchdog.
        }
    }
}
