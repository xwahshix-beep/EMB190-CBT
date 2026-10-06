package com.wahshi.cryptoexplosionradar;

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
import android.app.AlertDialog;
import android.widget.EditText;
import android.text.InputType;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity {
    private TextView health;
    private Button startButton;
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
        title.setText("🎯 Explosion Radar V5.4");
        title.setTextColor(Color.WHITE);
        title.setTextSize(27);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        root.addView(title);

        TextView sub = new TextView(this);
        sub.setText("1m EARLY RADAR • BINANCE SPOT");
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

        startButton = new Button(this);
        startButton.setText("▶ تشغيل");
        startButton.setTextSize(15);
        startButton.setTextColor(Color.WHITE);
        startButton.setAllCaps(false);
        startButton.setBackground(roundRect(Color.rgb(35, 139, 84), 14));
        startButton.setOnClickListener(v -> {
            Intent i = new Intent(this, ScannerService.class).setAction(ScannerService.ACTION_START);
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(i); else startService(i);
            health.setTextColor(Color.rgb(118, 210, 154));
            health.setText("● جارٍ تشغيل الرادار…");
            updateStartButton(true);
            showNotificationHint();
        });
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(0, dp(48), 1);
        bp.setMarginEnd(dp(6));
        controls.addView(startButton, bp);

        Button stop = new Button(this);
        stop.setText("■ إيقاف");
        stop.setTextSize(15);
        stop.setTextColor(Color.WHITE);
        stop.setAllCaps(false);
        stop.setBackground(roundRect(Color.rgb(65, 70, 80), 14));
        stop.setOnClickListener(v -> {
            startService(new Intent(this, ScannerService.class).setAction(ScannerService.ACTION_STOP));
            updateStartButton(false);
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
        details.setOnClickListener(v -> chooseBuyToFollow());
        details.setOnLongClickListener(v -> { chooseFollowToStop(); return true; });

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
        styleAll(sp, "🔵 احتفاظ", Color.rgb(94, 168, 255), true);
        styleAll(sp, "🟣 بيع جزئي", Color.rgb(190, 125, 255), true);
        styleAll(sp, "💰 خذ ربحًا إضافيًا", Color.rgb(89, 220, 135), true);
        styleAll(sp, "🔴 خروج", Color.rgb(255, 105, 105), true);
        styleAll(sp, "نطاق الشراء:", Color.rgb(160, 172, 190), false);
        styleAll(sp, "الهدف الأول:", Color.rgb(160, 172, 190), false);
        styleAll(sp, "وقف الحماية الآن:", Color.rgb(160, 172, 190), false);
        styleAll(sp, "كسر مستوى الحماية:", Color.rgb(160, 172, 190), false);

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

    private void chooseBuyToFollow() {
        EditText coin=new EditText(this);coin.setSingleLine(true);coin.setHint("BTCUSDT");
        new AlertDialog.Builder(this).setTitle("⭐ العملة المختارة").setMessage("تنبيهات الشراء تشمل جميع العملات المفحوصة. اختر مراقبة لهذه العملة، أو سعر الشراء إذا اشتريتها.")
        .setView(coin).setNegativeButton("إلغاء",null)
        .setNeutralButton("سعر الشراء",(d,w)->{String sym=coin.getText().toString().trim().toUpperCase(java.util.Locale.US).replace("/","");if(!sym.endsWith("USDT"))sym+="USDT";if(sym.matches("[A-Z0-9]{2,20}USDT"))askEntry(sym);})
        .setPositiveButton("مراقبة",(d,w)->{String sym=coin.getText().toString().trim().toUpperCase(java.util.Locale.US).replace("/","");if(!sym.endsWith("USDT"))sym+="USDT";if(sym.matches("[A-Z0-9]{2,20}USDT"))FollowGate.select(this,prefs(),sym,0);}).show();
    }

    private void askEntry(String label) {
        final String symbol=label.replace("/", "");
        EditText e=new EditText(this); e.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL); e.setHint("سعر شرائك الفعلي");
        new AlertDialog.Builder(this).setTitle("⭐ "+symbol).setMessage("أدخل سعر الشراء. سيبدأ التطبيق بمتابعة الصفقة وتنبيه الخروج لهذه العملة فقط.")
                .setView(e).setNegativeButton("إلغاء",null).setNeutralButton("السعر الحالي",(d,w)->followCurrent(symbol))
                .setPositiveButton("متابعة",(d,w)->{ try { double x=Double.parseDouble(e.getText().toString()); startFollow(symbol,x); } catch(Exception ex){} }).show();
    }

    private void followCurrent(String symbol) { new Thread(() -> { try { double x=TradeManager.currentPrice(symbol); runOnUiThread(()->startFollow(symbol,x)); } catch(Exception ignored){} }).start(); }
    private void startFollow(String symbol,double entry) {
        if(!Double.isFinite(entry)||entry<=0)return;
        FollowGate.select(this,prefs(),symbol,entry);
        new AlertDialog.Builder(this).setMessage("بدأت متابعة "+symbol+" فقط.\nسعر الدخول: "+entry).setPositiveButton("حسنًا",null).show();
    }
    private void chooseFollowToStop() {
        String raw=prefs().getString("follow_symbols",""); if(raw==null||raw.trim().isEmpty())return;
        String[] a=raw.split(","); new AlertDialog.Builder(this).setTitle("إيقاف المتابعة").setItems(a,(d,w)->stopFollow(a[w])).show();
    }
    private void stopFollow(String symbol) { FollowGate.select(this,prefs(),"",0); }

    private SharedPreferences prefs() {
        return getSharedPreferences("free_whale_radar_v3", MODE_PRIVATE);
    }

    private final android.os.Handler fastUiHandler=new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable fastUiRefresh=new Runnable(){public void run(){
        SharedPreferences p=prefs();String v=p.getString("fast_detail","");
        if(!FollowGate.active(p).isEmpty()&&!v.isEmpty()) {
            if(!p.getBoolean("radar_enabled",false)||System.currentTimeMillis()-p.getLong("fast_at",0)>60000) v="⭐ "+FollowGate.active(p)+"\n🟡 انتظار — البيانات غير محدثة";
            details.setText(styleDetails(v));
        }
        if(FollowGate.active(p).isEmpty()) {
            long expiry=p.getLong("market_buy_display_until",0);
            if(!p.getBoolean("radar_enabled",false)||expiry<=System.currentTimeMillis()) {
                String current=details.getText().toString();
                if(current.contains("🟢 شراء"))details.setText(styleDetails(current.replace("🟢 شراء","🟡 انتظار — يلزم تأكيد جديد")));
            }
        }
        fastUiHandler.postDelayed(this,2000);
    }};
    @Override protected void onResume(){super.onResume();fastUiHandler.removeCallbacks(fastUiRefresh);fastUiHandler.post(fastUiRefresh);}
    @Override protected void onPause(){fastUiHandler.removeCallbacks(fastUiRefresh);super.onPause();}
    private void restoreSavedState() {
        SharedPreferences p = prefs();
        String savedDetails = p.getString("last_details", p.getString("ui_details", ""));
        if (savedDetails != null && !savedDetails.isEmpty()) details.setText(styleDetails(savedDetails));
        refreshHealth();
    }

    private void refreshHealth() {
        SharedPreferences p = prefs();
        boolean enabled = p.getBoolean("radar_enabled", false);
        updateStartButton(enabled);
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
        boolean healthy = System.currentTimeMillis()-t <= 120000;
        health.setTextColor(healthy ? Color.rgb(118, 210, 154) : Color.rgb(245, 183, 43));
        health.setText((healthy ? "● يعمل" : "● متأخر") + " • آخر فحص " + when);
    }

    private void showNotificationHint() {
        android.app.NotificationManager nm=(android.app.NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        android.app.NotificationChannel ch=nm.getNotificationChannel(OpportunityAlerts.CHANNEL);
        if(nm.areNotificationsEnabled()&&(ch==null||ch.getImportance()!=0))return;
        new AlertDialog.Builder(this).setTitle("تفعيل إشعارات الشراء")
            .setMessage("اسمح بإشعارات التطبيق وقناة فرص الشراء المؤكدة حتى تصلك التنبيهات.")
            .setNegativeButton("لاحقًا",null)
            .setPositiveButton("الإعدادات",(d,w)->{
                Intent i=new Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS);
                i.putExtra(android.provider.Settings.EXTRA_APP_PACKAGE,getPackageName());startActivity(i);
            }).show();
    }

    private void updateStartButton(boolean running) {
        if (startButton == null) return;
        startButton.setEnabled(!running);
        startButton.setBackground(roundRect(running ? Color.rgb(65, 70, 80) : Color.rgb(35, 139, 84), 14));
        startButton.setTextColor(running ? Color.rgb(180, 185, 195) : Color.WHITE);
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
