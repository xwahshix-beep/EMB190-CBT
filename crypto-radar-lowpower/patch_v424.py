from pathlib import Path
import re

base = Path('app/src/main/java/com/wahshi/cryptoexplosionradar')

# 1) HybridEngine: main-screen summary contains ONLY coin, state, and buy range.
p = base / 'HybridEngine.java'
s = p.read_text(encoding='utf-8')
start = s.index('    private static String formatSummary(List<Candidate> candidates, int armedCount, int buyCount) {')
end = s.index('    private static void ensureChannel(Context context) {', start)
new_method = r'''    private static String formatSummary(List<Candidate> candidates, int armedCount, int buyCount) {
        StringBuilder sb = new StringBuilder();
        int shown = 0;
        final int MAX_SIMPLE = 7;

        for (Candidate c : candidates) {
            if (shown >= MAX_SIMPLE) break;
            if (c.liveSignal && "INVALIDATED".equals(c.liveState)) continue;

            boolean buy = c.liveSignal && "ACTIVE".equals(c.liveState);
            boolean wait = (c.liveSignal && "WAIT".equals(c.liveState)) || c.armed;
            if (!buy && !wait) continue;

            if (shown > 0) sb.append("\n────────────────\n\n");
            sb.append(c.base).append("/USDT\n");
            sb.append(buy ? "🟢 شراء\n" : "🟡 انتظار\n");

            if (c.liveSignal && c.entryLow > 0 && c.entryHigh > 0) {
                sb.append("نطاق الشراء: ")
                        .append(fmt(c.entryLow)).append(" – ").append(fmt(c.entryHigh));
            } else {
                sb.append("نطاق الشراء: بانتظار التأكيد");
            }
            shown++;
        }

        if (shown == 0) {
            sb.append("لا توجد فرصة جاهزة الآن");
        }
        return sb.toString();
    }

'''
s = s[:start] + new_method + s[end:]
p.write_text(s, encoding='utf-8')

# 2) ScannerService: keep technical layers internal; UI receives simple candidate summary only.
p = base / 'ScannerService.java'
sc = p.read_text(encoding='utf-8')
sc = sc.replace('            sendUi(hybrid.status, details);\n', '            sendUi(hybrid.status, hybrid.summary);\n', 1)
p.write_text(sc, encoding='utf-8')

# 3) MainActivity: completely replace cluttered dashboard with a glance-first screen.
p = base / 'MainActivity.java'
main = r'''package com.wahshi.cryptoexplosionradar;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity {
    private TextView health;
    private TextView details;

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (!ScannerService.ACTION_UI.equals(intent.getAction())) return;
            String d = intent.getStringExtra("details");
            if (d != null && !d.isEmpty()) details.setText(styleDetails(d));
            refreshHealth();
        }
    };

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 7);
        }

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(18), dp(18), dp(16));
        root.setBackgroundColor(Color.rgb(9, 12, 17));

        TextView title = new TextView(this);
        title.setText("🎯 Explosion Radar");
        title.setTextColor(Color.WHITE);
        title.setTextSize(27);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        root.addView(title);

        TextView sub = new TextView(this);
        sub.setText("BINANCE SPOT • LIVE RADAR");
        sub.setTextColor(Color.rgb(245, 183, 43));
        sub.setTextSize(12);
        sub.setGravity(Gravity.CENTER);
        sub.setPadding(0, dp(2), 0, dp(8));
        root.addView(sub);

        health = new TextView(this);
        health.setText("● متوقف");
        health.setTextSize(14);
        health.setGravity(Gravity.CENTER);
        health.setPadding(0, 0, 0, dp(10));
        root.addView(health);

        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        controls.setGravity(Gravity.CENTER);

        Button start = new Button(this);
        start.setText("▶ تشغيل");
        start.setTextSize(15);
        start.setTextColor(Color.WHITE);
        start.setAllCaps(false);
        start.setBackground(roundRect(Color.rgb(35, 139, 84), 14));
        start.setOnClickListener(v -> {
            Intent i = new Intent(this, ScannerService.class).setAction(ScannerService.ACTION_START);
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(i); else startService(i);
            health.setTextColor(Color.rgb(118, 210, 154));
            health.setText("● جارٍ تشغيل الرادار…");
        });
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(0, dp(48), 1);
        bp.setMarginEnd(dp(6));
        controls.addView(start, bp);

        Button stop = new Button(this);
        stop.setText("■ إيقاف");
        stop.setTextSize(15);
        stop.setTextColor(Color.WHITE);
        stop.setAllCaps(false);
        stop.setBackground(roundRect(Color.rgb(65, 70, 80), 14));
        stop.setOnClickListener(v -> {
            startService(new Intent(this, ScannerService.class).setAction(ScannerService.ACTION_STOP));
            health.setTextColor(Color.rgb(145, 157, 175));
            health.setText("● متوقف");
        });
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(0, dp(48), 1);
        sp.setMarginStart(dp(6));
        controls.addView(stop, sp);
        root.addView(controls);

        TextView listTitle = new TextView(this);
        listTitle.setText("الفرص الآن");
        listTitle.setTextColor(Color.WHITE);
        listTitle.setTextSize(21);
        listTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        listTitle.setPadding(dp(4), dp(18), 0, dp(8));
        root.addView(listTitle);

        details = new TextView(this);
        details.setText("لا توجد بيانات بعد");
        details.setTextColor(Color.rgb(235, 239, 245));
        details.setTextSize(19);
        details.setLineSpacing(dp(5), 1.05f);
        details.setPadding(dp(18), dp(18), dp(18), dp(24));
        details.setBackground(roundRect(Color.rgb(20, 25, 34), 18));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.addView(details);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1);
        root.addView(scroll, lp);

        setContentView(root);
    }

    private SpannableString styleDetails(String text) {
        SpannableString sp = new SpannableString(text);
        styleAll(sp, "🟢 شراء", Color.rgb(89, 220, 135), true);
        styleAll(sp, "🟡 انتظار", Color.rgb(245, 183, 43), true);
        styleAll(sp, "نطاق الشراء:", Color.rgb(160, 172, 190), false);

        int lineStart = 0;
        while (lineStart < text.length()) {
            int lineEnd = text.indexOf('\n', lineStart);
            if (lineEnd < 0) lineEnd = text.length();
            String line = text.substring(lineStart, lineEnd).trim();
            if (line.endsWith("/USDT")) {
                sp.setSpan(new StyleSpan(Typeface.BOLD), lineStart, lineEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                sp.setSpan(new RelativeSizeSpan(1.18f), lineStart, lineEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            lineStart = lineEnd + 1;
        }
        return sp;
    }

    private void styleAll(SpannableString sp, String needle, int color, boolean bold) {
        String text = sp.toString();
        int at = 0;
        while ((at = text.indexOf(needle, at)) >= 0) {
            int end = at + needle.length();
            sp.setSpan(new ForegroundColorSpan(color), at, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            if (bold) sp.setSpan(new StyleSpan(Typeface.BOLD), at, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            at = end;
        }
    }

    private SharedPreferences prefs() {
        return getSharedPreferences("free_whale_radar_v3", MODE_PRIVATE);
    }

    private void restoreSavedState() {
        SharedPreferences p = prefs();
        String savedDetails = p.getString("last_details", p.getString("ui_details", ""));
        if (savedDetails != null && !savedDetails.isEmpty()) details.setText(styleDetails(savedDetails));
        refreshHealth();
    }

    private void refreshHealth() {
        SharedPreferences p = prefs();
        boolean enabled = p.getBoolean("radar_enabled", false);
        long t = p.getLong("last_scan_at", 0L);
        if (!enabled) {
            health.setTextColor(Color.rgb(145, 157, 175));
            health.setText("● متوقف");
            return;
        }
        if (t <= 0L) {
            health.setTextColor(Color.rgb(245, 183, 43));
            health.setText("● يعمل • أول فحص قيد التنفيذ");
            return;
        }
        long ageMin = Math.max(0L, (System.currentTimeMillis() - t) / 60_000L);
        String when = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date(t));
        boolean healthy = ageMin <= 15;
        health.setTextColor(healthy ? Color.rgb(118, 210, 154) : Color.rgb(245, 183, 43));
        health.setText((healthy ? "● يعمل" : "● متأخر") + " • آخر فحص " + when);
    }

    private GradientDrawable roundRect(int color, int radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radiusDp));
        return g;
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    @Override protected void onStart() {
        super.onStart();
        IntentFilter f = new IntentFilter(ScannerService.ACTION_UI);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(receiver, f, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(receiver, f);
        restoreSavedState();
    }

    @Override protected void onStop() {
        try { unregisterReceiver(receiver); } catch (Exception ignored) {}
        super.onStop();
    }
}
'''
p.write_text(main, encoding='utf-8')

# 4) App label + version metadata.
manifest = Path('app/src/main/AndroidManifest.xml')
ms = manifest.read_text(encoding='utf-8')
ms = re.sub(r'android:label="[^"]+"', 'android:label="Explosion Radar"', ms, count=1)
manifest.write_text(ms, encoding='utf-8')

gradle = Path('app/build.gradle')
g = gradle.read_text(encoding='utf-8')
g = re.sub(r'versionCode\s+\d+', 'versionCode 8', g, count=1)
g = re.sub(r'versionName\s+"[^"]+"', 'versionName "4.2.4-simple"', g, count=1)
gradle.write_text(g, encoding='utf-8')

print('V4.2.4 Simple Radar applied: coin + BUY/WAIT + entry range only')
