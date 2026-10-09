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
    private TextView savedSignals;
    private TextView followStatus;
    private Button retryFollow;
    private long quoteRequest;

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (!ScannerService.ACTION_UI.equals(intent.getAction())) return;
            renderDetails();
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
        title.setText("🎯 Explosion Radar V6");
        title.setTextColor(Color.WHITE);
        title.setTextSize(27);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        root.addView(title);

        TextView sub = new TextView(this);
        sub.setText("EXPERIMENTAL • 1m RADAR • BINANCE SPOT");
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
        Button replay=new Button(this);replay.setText("Historical Replay • الاختبار التاريخي");
        replay.setOnClickListener(v -> startActivity(new Intent(this,ReplayActivity.class)));
        root.addView(replay);

        TextView listTitle = new TextView(this);
        listTitle.setText("الرادار والإشارات");
        listTitle.setTextColor(Color.WHITE);
        listTitle.setTextSize(21);
        listTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        listTitle.setPadding(dp(4), dp(18), 0, dp(8));
        root.addView(listTitle);

        followStatus = new TextView(this);
        followStatus.setTextColor(Color.rgb(245,183,43));
        followStatus.setTextSize(14);
        followStatus.setPadding(dp(4),dp(6),dp(4),dp(10));
        followStatus.setOnClickListener(v -> chooseFollowToStop());
        root.addView(followStatus);
        retryFollow=new Button(this);retryFollow.setText("↻ تحديث المتابعة");
        retryFollow.setTextColor(Color.WHITE);retryFollow.setBackground(roundRect(Color.rgb(65,70,80),12));
        retryFollow.setOnClickListener(v -> {ensureRunning();feedback("تم طلب تحديث المتابعة");});
        root.addView(retryFollow);

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
        LinearLayout content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);
        TextView savedTitle=new TextView(this);savedTitle.setText("📌 إشارات الشراء المحفوظة");
        savedTitle.setTextColor(Color.rgb(245,183,43));savedTitle.setTypeface(Typeface.DEFAULT,Typeface.BOLD);savedTitle.setTextSize(20);
        savedTitle.setPadding(dp(4),dp(10),dp(4),dp(8));content.addView(savedTitle);
        TextView hint=new TextView(this);hint.setText("أحدث إشارة لكل عملة • تبقى بعد تغيّر قائمة الفرص\nاضغط على السجل لاختيار عملة ومتابعتها");
        hint.setTextColor(Color.rgb(160,172,190));hint.setTextSize(13);hint.setPadding(dp(4),0,dp(4),dp(10));content.addView(hint);
        savedSignals=new TextView(this);savedSignals.setTextColor(Color.rgb(235,239,245));savedSignals.setTextSize(17);
        savedSignals.setLineSpacing(dp(4),1.05f);savedSignals.setPadding(dp(16),dp(16),dp(16),dp(16));
        savedSignals.setBackground(roundRect(Color.rgb(20,25,34),18));
        savedSignals.setOnClickListener(v -> chooseSavedSignal());content.addView(savedSignals);
        TextView currentTitle=new TextView(this);currentTitle.setText("الفرص الآن / العملة المتابعة");
        currentTitle.setTextColor(Color.WHITE);currentTitle.setTextSize(20);currentTitle.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        currentTitle.setPadding(dp(4),dp(22),dp(4),dp(8));content.addView(currentTitle);
        content.addView(details);scroll.addView(content);
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

    private void feedback(String message) {
        android.widget.Toast.makeText(this,message,android.widget.Toast.LENGTH_LONG).show();
    }
    private String symbolOf(EditText field) {
        String symbol=field.getText().toString().trim().toUpperCase(Locale.US).replace("/", "");
        if(!symbol.endsWith("USDT"))symbol+="USDT";
        if(!symbol.matches("[A-Z0-9]{2,20}USDT")) {field.setError("أدخل رمز العملة مثل BTC أو BTCUSDT");return null;}
        return symbol;
    }
    private void chooseSavedSignal(){
        String[] symbols=SignalChecks.historySymbols(prefs());
        if(symbols.length==0){feedback("ستُحفظ أول إشارة شراء مؤكدة هنا تلقائيًا");return;}
        new AlertDialog.Builder(this).setTitle("اختر إشارة محفوظة").setItems(symbols,(d,w)->chooseBuyToFollow(symbols[w])).setNegativeButton("إلغاء",null).show();
    }
    private void chooseBuyToFollow() {chooseBuyToFollow(FollowGate.active(prefs()));}
    private void chooseBuyToFollow(String selectedSymbol) {
        EditText coin=new EditText(this);coin.setSingleLine(true);coin.setHint("BTCUSDT");
        coin.setText(selectedSymbol);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("⭐ العملة المختارة")
            .setMessage("مراقبة: متابعة الإشارات. الشراء الآن: جلب سعر العرض من Binance وتسجيله لمتابعة الصفقة داخل الرادار فقط؛ لا ينفذ أمر شراء في حسابك.")
            .setView(coin).setNegativeButton("إلغاء",(d,w)->feedback("تم إلغاء العملية"))
            .setNeutralButton("الشراء الآن",null).setPositiveButton("مراقبة",null).create();
        dialog.setOnCancelListener(d -> feedback("تم إلغاء العملية"));
        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String sym=symbolOf(coin);if(sym==null)return;
                quoteRequest++;FollowGate.select(this,prefs(),sym,0);ensureRunning();renderDetails();
                dialog.dismiss();feedback("تمت إضافة "+sym+" للمراقبة");
            });
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
                String sym=symbolOf(coin);if(sym==null)return;dialog.dismiss();followCurrent(sym);
            });
        });
        dialog.show();
    }
    private void ensureRunning() {
        Intent i=new Intent(this,ScannerService.class).setAction(ScannerService.ACTION_REFRESH_FOLLOW);
        if(Build.VERSION.SDK_INT>=26)startForegroundService(i);else startService(i);
        updateStartButton(true);
    }
    private void followCurrent(String symbol) {
        final long request=++quoteRequest;
        final long generation=prefs().getLong("follow_generation",0);
        AlertDialog loading=new AlertDialog.Builder(this).setTitle("الشراء الآن — "+symbol)
            .setMessage("جارٍ جلب سعر العرض مباشرة من Binance…\nسيُسجّل للمتابعة فقط، وقد يختلف عن سعر تنفيذك الفعلي.")
            .setNegativeButton("إلغاء",(d,w)->{quoteRequest++;feedback("تم إلغاء جلب السعر؛ لم تُسجّل صفقة");}).create();
        loading.setOnCancelListener(d -> {quoteRequest++;feedback("تم إلغاء جلب السعر؛ لم تُسجّل صفقة");});
        loading.show();
        new Thread(() -> {
            try {
                long started=android.os.SystemClock.elapsedRealtime();
                org.json.JSONObject q=new org.json.JSONObject(MarketHttp.read("https://api.binance.com/api/v3/ticker/bookTicker?symbol="+symbol));
                double price=q.getDouble("askPrice"),bid=q.getDouble("bidPrice");
                if(!symbol.equals(q.getString("symbol"))||!Double.isFinite(price+bid)||bid<=0||price<bid)
                    throw new java.io.IOException("سعر غير صالح");
                runOnUiThread(() -> {
                    if(isFinishing()||isDestroyed()||request!=quoteRequest)return;
                    loading.dismiss();
                    if(generation!=prefs().getLong("follow_generation",0)) {feedback("تغيرت المتابعة؛ لم يتم تسجيل السعر");return;}
                    if(android.os.SystemClock.elapsedRealtime()-started>10000) {feedback("تأخر وصول السعر؛ أعد المحاولة");return;}
                    FollowGate.select(this,prefs(),symbol,price);ensureRunning();renderDetails();
                    new AlertDialog.Builder(this).setTitle("تم تسجيل سعر المتابعة")
                        .setMessage(symbol+"\nسعر العرض: "+price+" USDT\nوقت الجلب: "+new SimpleDateFormat("HH:mm:ss",Locale.getDefault()).format(new Date())+"\nبدأت متابعة الصفقة وتنبيهات الخروج. لم يُنفّذ شراء في Binance.")
                        .setPositiveButton("حسنًا",null).show();
                });
            } catch(Exception error) {
                runOnUiThread(() -> {
                    if(isFinishing()||isDestroyed()||request!=quoteRequest)return;
                    loading.dismiss();
                    new AlertDialog.Builder(this).setTitle("تعذر جلب السعر")
                        .setMessage("لم تُسجّل صفقة. تحقق من اتصالك ورمز العملة وتوفر الزوج على Binance ثم أعد المحاولة.")
                        .setNegativeButton("إلغاء",(d,w)->feedback("تم إلغاء العملية"))
                        .setPositiveButton("إعادة المحاولة",(d,w)->followCurrent(symbol)).show();
                });
            }
        },"entry-quote").start();
    }
    private void chooseFollowToStop() {
        String symbol=FollowGate.active(prefs());
        if(symbol.isEmpty()){feedback("لا توجد عملة قيد المتابعة");return;}
        new AlertDialog.Builder(this).setTitle("إلغاء متابعة "+symbol)
            .setMessage("سيتم إيقاف مراقبة هذه العملة ومتابعة صفقتها داخل الرادار. تنبيهات فرص السوق تبقى مفعّلة.")
            .setNegativeButton("رجوع",null).setPositiveButton("إلغاء المتابعة",(d,w)->stopFollow(symbol)).show();
    }
    private void stopFollow(String symbol) {
        quoteRequest++;FollowGate.select(this,prefs(),"",0);renderDetails();feedback("تم إلغاء متابعة "+symbol);
    }

    private SharedPreferences prefs() {
        return getSharedPreferences("free_whale_radar_v3", MODE_PRIVATE);
    }

    private final android.os.Handler fastUiHandler=new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable fastUiRefresh=new Runnable(){public void run(){
        renderDetails();refreshHealth();fastUiHandler.postDelayed(this,2000);
    }};
    private void renderDetails() {
        SharedPreferences p=prefs();String symbol=FollowGate.active(p);
        long now=System.currentTimeMillis();boolean enabled=p.getBoolean("radar_enabled",false);
        String text;
        retryFollow.setVisibility(symbol.isEmpty()?android.view.View.GONE:android.view.View.VISIBLE);
        if(!symbol.isEmpty()) {
            double entry=Double.longBitsToDouble(p.getLong("follow_entry_"+symbol,0));
            followStatus.setText("⭐ "+symbol+(entry>0?" • صفقة مسجلة بسعر "+entry:" • مراقبة")+"\nاضغط هنا لإلغاء المتابعة");
            text=p.getString("fast_detail","");
            text=FollowView.render(symbol,text,p.getLong("fast_attempt_at",0),now,enabled);
            text=SignalChecks.decorate(p,text);
            text=SignalDisplay.one(text,p.getLong("fast_buy_until",0),now,enabled);
            text=SignalChecks.followDisplay(p,symbol,text);
        } else {
            followStatus.setText("اضغط على قائمة الفرص لاختيار عملة ومتابعتها");
            text=p.getString("last_details",p.getString("ui_details","لا توجد بيانات بعد"));
            java.util.Map<String,Long> expiries=new java.util.HashMap<>();
            try {
                org.json.JSONObject o=new org.json.JSONObject(p.getString("market_buy_expiries","{}"));
                java.util.Iterator<String> keys=o.keys();while(keys.hasNext()){String key=keys.next();expiries.put(key,o.getLong(key));}
            } catch(Exception ignored){}
            text=SignalChecks.decorate(p,text);
            text=SignalDisplay.market(text,expiries,now,enabled);
        }
        details.setText(styleDetails(text));
        savedSignals.setText(styleDetails(SignalChecks.history(p)));
    }
    @Override protected void onResume(){super.onResume();fastUiHandler.removeCallbacks(fastUiRefresh);fastUiHandler.post(fastUiRefresh);}
    @Override protected void onPause(){fastUiHandler.removeCallbacks(fastUiRefresh);super.onPause();}
    private void restoreSavedState() {
        SharedPreferences p = prefs();
        renderDetails();
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

    @Override protected void onDestroy() { quoteRequest++;super.onDestroy(); }

    @Override protected void onStop() {
        try { unregisterReceiver(receiver); } catch (Exception ignored) {}
        super.onStop();
    }
}
