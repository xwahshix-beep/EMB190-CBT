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
    private TextView scanCoverage;
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

    private static final int EXPORT_PHASE=660,EXPORT_AUDIT=670;
    private void exportAudit(){
        new AlertDialog.Builder(this).setTitle("تصدير سجل CSV")
            .setMessage("يشمل آخر 300 قرار خلال 24 ساعة، ورصد الأسعار حتى 1000 سجل / 7 أيام، وتسليم الإشعارات حتى 500 سجل / 7 أيام. قد يُحذف الأقدم عند بلوغ الحد؛ هذا ليس أرشيفًا كاملًا. النتائج قبل التكاليف وليست صفقات. الأوقات بالمللي ثانية UTC. القيم القديمة غير المسجلة تبقى فارغة.")
            .setPositiveButton("حفظ CSV",(d,w)->{
                Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("text/csv");
                i.putExtra(Intent.EXTRA_TITLE,"Radar-audit-"+System.currentTimeMillis()+".csv");startActivityForResult(i,EXPORT_AUDIT);
            }).setNegativeButton("إلغاء",null).show();
    }
    private void showPhaseShadow(){
        new AlertDialog.Builder(this).setTitle("اختبار ربع الساعة")
            .setMessage(PhaseShadow.report(prefs().getAll(),System.currentTimeMillis())+"\n"+prefs().getString("v66_phase_error",""))
            .setPositiveButton("إغلاق",null).setNeutralButton("تصدير سجل البحث",(d,w)->{
                Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("application/json");i.putExtra(Intent.EXTRA_TITLE,"Radar-phase-shadow-"+System.currentTimeMillis()+".json");
                startActivityForResult(i,EXPORT_PHASE);
            }).show();
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if((request!=EXPORT_PHASE&&request!=EXPORT_AUDIT)||result!=RESULT_OK||data==null||data.getData()==null)return;
        final android.net.Uri uri=data.getData();
        final java.util.Map<String,?> snapshot=prefs().getAll();
        new Thread(()->{
            try{
                if(request==EXPORT_AUDIT){
                    String csv=ResearchExport.csv(String.valueOf((snapshot.containsKey("v6_trace")?snapshot.get("v6_trace"):"[]")),String.valueOf((snapshot.containsKey("v65_outcomes")?snapshot.get("v65_outcomes"):"[]")),String.valueOf((snapshot.containsKey("v67_notifications")?snapshot.get("v67_notifications"):"[]")));
                    try(java.io.OutputStream stream=getContentResolver().openOutputStream(uri,"wt")){
                        if(stream==null)throw new java.io.IOException("No output stream");stream.write(csv.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    }
                    runOnUiThread(()->feedback("تم حفظ CSV"));return;
                }
                org.json.JSONObject out=new org.json.JSONObject().put("schema","phase-shadow-v1").put("app","6.8-evidence").put("exportedAt",System.currentTimeMillis())
                    .put("description","Frozen per-symbol calibration; activity only, not BUY. Hourly samples capped at 72 per symbol. Gross closed-minute price returns, no fills or costs.");
                org.json.JSONObject symbols=new org.json.JSONObject();
                for(java.util.Map.Entry<String,?> e:snapshot.entrySet())if(e.getKey().startsWith("v66_phase_")){
                    if(e.getKey().equals("v66_phase_error"))continue;
                    symbols.put(e.getKey().substring(10),new org.json.JSONObject(String.valueOf(e.getValue())));
                }
                out.put("symbols",symbols);
                try(java.io.OutputStream stream=getContentResolver().openOutputStream(uri,"wt")){
                    if(stream==null)throw new java.io.IOException("No output stream");
                    stream.write(out.toString(2).getBytes(java.nio.charset.StandardCharsets.UTF_8));
                }
                runOnUiThread(()->feedback("تم تصدير سجل البحث"));
            }catch(Exception ex){runOnUiThread(()->feedback("تعذر حفظ سجل البحث؛ حاول اختيار موقع آخر"));}
        },"phase-export").start();
    }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        ProductionGate.clearBlocked(this);
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 7);
        }

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(18), dp(18), dp(16));
        root.setBackgroundColor(Color.rgb(9, 12, 17));

        TextView title = new TextView(this);
        title.setText("🎯 Explosion Radar "+BuildConfig.VERSION_NAME);
        title.setTextColor(Color.WHITE);
        title.setTextSize(22);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        root.addView(title);

        TextView sub = new TextView(this);
        sub.setText("تجريبي • الربحية غير مثبتة • BINANCE SPOT");
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

        Button research=new Button(this);research.setText("مختبر الزخم اليومي · نتائج وفحص ورقي");
        research.setOnClickListener(v -> startActivity(new Intent(this,ResearchLabActivity.class)));

        Button log=new Button(this);
        log.setText("📋 سجل قبول ورفض الإشارات");
        log.setOnClickListener(v -> new AlertDialog.Builder(this)
            .setTitle("سجل قرارات Scanner • آخر 80 حدثًا")
            .setMessage(DecisionLedger.history(prefs().getString("v6_trace","[]"),80))
            .setPositiveButton("إغلاق",null).show());

        Button audit=new Button(this);audit.setText("🔎 لماذا لم تظهر العملة؟");
        audit.setOnClickListener(v->{
            android.widget.EditText input=new android.widget.EditText(this);input.setHint("ARK أو ARKUSDT");
            new AlertDialog.Builder(this).setTitle("فحص آخر قرار مسجل").setView(input).setPositiveButton("فحص",(d,w)->{
                String symbol=input.getText().toString().trim().toUpperCase(java.util.Locale.US).replace("/","");
                if(!symbol.endsWith("USDT"))symbol+="USDT";
                long at=prefs().getLong("v65_last_at_"+symbol,0),scopeAt=prefs().getLong("v65_universe_at",0);
                boolean inside=false;try{inside=new org.json.JSONObject(prefs().getString("v65_universe","{}")).has(symbol);}catch(Exception ignored){}
                String message="آخر تحديث لنطاق الفحص: "+(scopeAt==0?"غير متاح":new java.util.Date(scopeAt).toString())+
                    "\n"+(inside?"داخل نطاق آخر فحص":"غير موجود في نطاق آخر فحص؛ قد يكون غير مدعوم أو دون حد السيولة، ولا يكفي ذلك لتحديد السبب التاريخي")+
                    "\n\n"+(at==0?"لا يوجد قرار محفوظ لهذه العملة منذ تثبيت هذا التحديث":new java.util.Date(at)+"\n"+prefs().getString("v65_last_"+symbol,""))+
                    "\n\nهذا آخر قرار فقط، وليس إعادة بناء لما حدث قبل التحديث. ارتفاع 24 ساعة لا يثبت وجود شرط شراء أثناء تشغيل الرادار.";
                new AlertDialog.Builder(this).setTitle(symbol).setMessage(message).setPositiveButton("إغلاق",null).show();
            }).setNegativeButton("إلغاء",null).show();
        });
        Button outcomes=new Button(this);outcomes.setText("📊 نتائج الرصد بعد 5 / 15 / 60 دقيقة");
        outcomes.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("بطاقة الأداء • "+BuildConfig.VERSION_NAME)
            .setMessage(PerformanceCard.report(prefs().getString("v65_outcomes","[]"),System.currentTimeMillis()))
            .setPositiveButton("إغلاق",null).show());
        Button alerts=new Button(this);alerts.setText("🔔 إعدادات الإشعارات / اختبار");
        alerts.setOnClickListener(v -> new AlertDialog.Builder(this).setTitle("نطاق إشعارات الشراء")
            .setItems(new String[]{"كل إشارات الشراء المؤكدة","العملة المتابعة فقط","إرسال إشعار تجريبي","إعدادات إشعارات Android"},(d,which)->{
                if(which<2){prefs().edit().putBoolean("notify_all_buys",which==0).apply();feedback(which==0?"تم حفظ النطاق؛ يبقى قفل الإنتاج مطبقًا":"تنبيهات العملة المتابعة فقط");renderDetails();}
                else if(which==2)feedback(OpportunityAlerts.test(this));
                else{Intent i=new Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS);i.putExtra(android.provider.Settings.EXTRA_APP_PACKAGE,getPackageName());startActivity(i);}
            }).setNegativeButton("إغلاق",null).show());

        Button toolsMenu=new Button(this);toolsMenu.setText("☰ الأدوات والتقارير والإشعارات");
        toolsMenu.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("الأدوات")
            .setItems(new String[]{"الاختبار التاريخي","مختبر الزخم اليومي","سجل قبول ورفض الإشارات","لماذا لم تظهر العملة؟","بطاقة أداء المحرك","إعدادات الإشعارات / اختبار","حالة الاتصال وتغطية الفحص","اختبار ربع الساعة (تجريبي)","تصدير سجل الإشارات CSV"},(d,w)->{
                Button[] actions={replay,research,log,audit,outcomes,alerts};
                if(w<actions.length)actions[w].performClick();
                else if(w==7)showPhaseShadow();
                else if(w==8)exportAudit();
                else new AlertDialog.Builder(this).setTitle("حالة الفحص والتغطية").setMessage(scanCoverage.getText()).setPositiveButton("إغلاق",null).show();
            }).setNegativeButton("إغلاق",null).show());root.addView(toolsMenu);
        TextView listTitle = new TextView(this);
        listTitle.setText("الرادار والإشارات");
        listTitle.setTextColor(Color.WHITE);
        listTitle.setTextSize(21);
        listTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        listTitle.setPadding(dp(4), dp(6), 0, dp(4));
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
        scanCoverage=new TextView(this);scanCoverage.setTextColor(Color.rgb(135,210,180));
        scanCoverage.setTextSize(14);scanCoverage.setPadding(dp(12),dp(12),dp(12),dp(12));
        // Coverage is available from Tools; it no longer pushes coins below the fold.
        TextView currentTitle=new TextView(this);currentTitle.setText("نتائج آخر دفعة / العملة المتابعة");
        currentTitle.setTextColor(Color.WHITE);currentTitle.setTextSize(20);currentTitle.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        currentTitle.setPadding(dp(4),dp(12),dp(4),dp(8));content.addView(currentTitle);
        content.addView(details);
        TextView savedTitle=new TextView(this);savedTitle.setText("📌 إشارات الشراء المحفوظة");
        savedTitle.setTextColor(Color.rgb(245,183,43));savedTitle.setTypeface(Typeface.DEFAULT,Typeface.BOLD);savedTitle.setTextSize(20);
        savedTitle.setPadding(dp(4),dp(10),dp(4),dp(8));content.addView(savedTitle);
        TextView hint=new TextView(this);hint.setText("أحدث إشارة لكل عملة • تبقى بعد تغيّر قائمة الفرص\nاضغط على السجل لاختيار عملة ومتابعتها");
        hint.setTextColor(Color.rgb(160,172,190));hint.setTextSize(13);hint.setPadding(dp(4),0,dp(4),dp(10));content.addView(hint);
        savedSignals=new TextView(this);savedSignals.setTextColor(Color.rgb(235,239,245));savedSignals.setTextSize(17);
        savedSignals.setLineSpacing(dp(4),1.05f);savedSignals.setPadding(dp(16),dp(16),dp(16),dp(16));
        savedSignals.setBackground(roundRect(Color.rgb(20,25,34),18));
        savedSignals.setOnClickListener(v -> chooseSavedSignal());content.addView(savedSignals);
        root.addView(content);
        // One page scroll: large fonts and short screens cannot collapse the coin viewport.
        scroll.addView(root);
        setContentView(scroll);
        inspectAlert(getIntent());
    }

    private SpannableString styleDetails(String text) {
        SpannableString sp = new SpannableString("بحث غير مثبت — ليس توصية تداول\n"+text);
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
            .setMessage("مراقبة: متابعة الإشارات. تسجيل دخول ورقي: جلب سعر العرض من Binance وتسجيله لمتابعة الصفقة داخل الرادار فقط؛ لا ينفذ أمر شراء في حسابك.")
            .setView(coin).setNegativeButton("إلغاء",(d,w)->feedback("تم إلغاء العملية"))
            .setNeutralButton("تسجيل دخول ورقي",null).setPositiveButton("مراقبة",null).create();
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
        AlertDialog loading=new AlertDialog.Builder(this).setTitle("تسجيل دخول ورقي — "+symbol)
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
        long coverageAt=p.getLong("scan_coverage_at",0);
        String coverage=p.getString("scan_coverage","لم تكتمل دورة فحص بعد");
        if(coverageAt>0)coverage="عمر تقرير التغطية: "+Math.max(0,(now-coverageAt)/1000)+" ثانية\n"+coverage;
        if(!enabled)coverage="الرادار متوقف — التقرير السابق فقط\n"+coverage;
        else if(coverageAt>0&&now-coverageAt>120000)coverage="⚠ تقرير قديم؛ قد تكون دورة الفحص متأخرة أو البيانات متعذرة\n"+coverage;
        coverage=(ProductionGate.enabled(this)?"بحث غير مثبت — ليس توصية":ProductionGate.MESSAGE)+"\n"+coverage;
        coverage="نطاق التنبيهات المطلوب (لا يتجاوز قفل الإنتاج): "+(p.getBoolean("notify_all_buys",true)?"كل الإشارات المؤكدة":"العملة المتابعة فقط")+"\n"+p.getString("push_status","لم يُسجّل إرسال إشعار بعد")+"\n"+coverage;
        scanCoverage.setText(coverage);
        details.setText(styleDetails(text));
        savedSignals.setText(styleDetails(SignalChecks.history(p)));
    }
    private void inspectAlert(Intent intent){
        if(intent==null||!intent.hasExtra("alertExpires"))return;
        long expiry=intent.getLongExtra("alertExpires",0);String symbol=intent.getStringExtra("alertSymbol");
        intent.removeExtra("alertExpires");
        boolean expired=System.currentTimeMillis()>=expiry;
        new AlertDialog.Builder(this).setTitle(expired?"انتهت صلاحية التنبيه":"لقطة إشعار سابقة")
            .setMessage(symbol+"\n"+(expired?"انتهت الصلاحية؛ يلزم تحليل جديد.":"بقاء التنبيه ضمن مدته لا يثبت استمرار شروط الدخول؛ راجع آخر حالة وسعر في الرادار.")+"\nهذا ليس أمر تداول.").setPositiveButton("فهمت",null).show();
    }
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);inspectAlert(intent);}
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
            .setMessage("اسمح بإشعارات التطبيق وقناة الإشارات البحثية حتى تصلك التنبيهات.")
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
