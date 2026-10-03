package com.wahshi.cryptoexplosionradar;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class MainActivity extends Activity {
    private TextView status;
    private TextView details;

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (!ScannerService.ACTION_UI.equals(intent.getAction())) return;
            String s = intent.getStringExtra("status");
            String d = intent.getStringExtra("details");
            status.setText(s == null ? "" : s);
            details.setText(d == null ? "" : d);
        }
    };

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 7);
        }

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(32, 38, 32, 26);
        root.setBackgroundColor(Color.rgb(9,12,17));

        TextView title = new TextView(this);
        title.setText("🐋⚡ Whale + Catalyst Radar V4");
        title.setTextColor(Color.WHITE);
        title.setTextSize(24);
        title.setGravity(Gravity.CENTER_HORIZONTAL);
        title.setPadding(0,0,0,12);
        root.addView(title);

        TextView mode = new TextView(this);
        mode.setText("FREE • Binance Spot / USDT\nOn-chain whales + official Binance catalysts • لا يحتاج API key");
        mode.setTextColor(Color.rgb(245,183,43));
        mode.setTextSize(14);
        mode.setGravity(Gravity.CENTER_HORIZONTAL);
        mode.setPadding(0,0,0,20);
        root.addView(mode);

        status = new TextView(this);
        status.setText("الرادار متوقف");
        status.setTextColor(Color.rgb(230,235,242));
        status.setTextSize(18);
        status.setPadding(0,16,0,12);
        root.addView(status);

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setGravity(Gravity.CENTER);

        Button start = new Button(this);
        start.setText("تشغيل الرادار");
        start.setOnClickListener(v -> {
            Intent i = new Intent(this, ScannerService.class).setAction(ScannerService.ACTION_START);
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(i); else startService(i);
            status.setText("بدء Whale + Catalyst Radar…");
        });
        buttons.addView(start, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        Button stop = new Button(this);
        stop.setText("إيقاف");
        stop.setOnClickListener(v -> startService(new Intent(this, ScannerService.class).setAction(ScannerService.ACTION_STOP)));
        buttons.addView(stop, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        root.addView(buttons);

        TextView note = new TextView(this);
        note.setText("\n🐋 WHALE RADAR: يرصد صافي زيادة كبيرة في عدة محافظ مستقلة على البلوكشين بينما السعر ما زال هادئًا.\n\n⚡ CATALYST RADAR: يراقب الإعلانات الرسمية العامة من Binance ويصنف Listing / Migration / Mainnet / Airdrop / Burn / Delisting وغيرها.\n\n🐋⚡ عند اجتماع التجميع والمحـفز لنفس العملة يصدر تنبيه موحّد. لا يعطي التطبيق أمر شراء.\n\nCatalyst scan كل 5 دقائق تقريبًا، وWhale scan الثقيل كل 20 دقيقة لتقليل البطارية والحرارة.");
        note.setTextColor(Color.rgb(145,157,175));
        note.setTextSize(13);
        note.setPadding(0,8,0,10);
        root.addView(note);

        details = new TextView(this);
        details.setText("في انتظار التشغيل…");
        details.setTextColor(Color.rgb(220,225,232));
        details.setTextSize(14);
        details.setLineSpacing(5,1.0f);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(details);
        root.addView(scroll, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));
        setContentView(root);
    }

    @Override protected void onStart() {
        super.onStart();
        IntentFilter f = new IntentFilter(ScannerService.ACTION_UI);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(receiver, f, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(receiver, f);
    }

    @Override protected void onStop() {
        try { unregisterReceiver(receiver); } catch (Exception ignored) {}
        super.onStop();
    }
}
