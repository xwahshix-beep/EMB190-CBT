from pathlib import Path
import re

base = Path('app/src/main/java/com/wahshi/cryptoexplosionradar')

# TradeManager: manual follow is the only trigger for HOLD/EXIT management.
trade = r'''package com.wahshi.cryptoexplosionradar;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.net.ssl.HttpsURLConnection;

public final class TradeManager {
    private static final String API = "https://api.binance.com";
    private static final String CHANNEL = "hybrid_buy_alerts_v42";
    private TradeManager() {}

    public static class TradeState {
        public String symbol;
        public double entry;
        public double price;
        public double stop;
        public double protect;
        public double pnlPct;
        public String state;
    }

    public static List<TradeState> scan(Context context, SharedPreferences prefs) {
        List<TradeState> out = new ArrayList<>();
        String raw = prefs.getString("follow_symbols", "");
        if (raw == null || raw.trim().isEmpty()) return out;
        for (String symbol : raw.split(",")) {
            symbol = symbol.trim();
            if (symbol.isEmpty()) continue;
            try {
                TradeState t = evaluate(symbol, prefs);
                if (t != null) {
                    out.add(t);
                    String prev = prefs.getString("follow_state_" + symbol, "");
                    if (!t.state.equals(prev)) {
                        prefs.edit().putString("follow_state_" + symbol, t.state).apply();
                        if ("EXIT".equals(t.state)) notifyExit(context, t);
                        else if ("HOLD".equals(t.state) && !prev.isEmpty() && !"HOLD".equals(prev)) notifyHold(context, t);
                    }
                }
            } catch (Exception ignored) {}
        }
        return out;
    }

    private static TradeState evaluate(String symbol, SharedPreferences prefs) throws Exception {
        double entry = Double.longBitsToDouble(prefs.getLong("follow_entry_" + symbol, Double.doubleToLongBits(0.0)));
        double initialStop = Double.longBitsToDouble(prefs.getLong("follow_stop_" + symbol, Double.doubleToLongBits(0.0)));
        if (entry <= 0) return null;
        double price = currentPrice(symbol);
        JSONArray rows = new JSONArray(readUrl(API + "/api/v3/klines?symbol=" + symbol + "&interval=15m&limit=20"));
        double recentLow = Double.MAX_VALUE;
        int from = Math.max(0, rows.length() - 5);
        for (int i = from; i < rows.length(); i++) {
            JSONArray r = rows.optJSONArray(i);
            if (r != null) recentLow = Math.min(recentLow, Double.parseDouble(r.optString(3, "0")));
        }
        if (recentLow == Double.MAX_VALUE || recentLow <= 0) recentLow = initialStop;
        double risk = entry - initialStop;
        if (risk <= 0) risk = entry * 0.025;
        double r1 = entry + risk;
        double protect = initialStop;
        if (price >= r1) protect = Math.max(entry, recentLow);
        else if (price > entry) protect = Math.max(initialStop, recentLow);

        TradeState t = new TradeState();
        t.symbol = symbol;
        t.entry = entry;
        t.price = price;
        t.stop = initialStop;
        t.protect = protect;
        t.pnlPct = (price / entry - 1.0) * 100.0;
        t.state = price <= protect ? "EXIT" : "HOLD";
        return t;
    }

    private static void notifyExit(Context c, TradeState t) {
        notify(c, 26000 + Math.abs(t.symbol.hashCode() % 2000), "🔴 بيع / خروج — " + t.symbol,
                "السعر " + fmt(t.price) + " • كسر مستوى الحماية " + fmt(t.protect), Color.rgb(255,90,90));
    }

    private static void notifyHold(Context c, TradeState t) {
        notify(c, 24000 + Math.abs(t.symbol.hashCode() % 2000), "🔵 احتفاظ — " + t.symbol,
                "الصفقة ما زالت سليمة • " + String.format(Locale.US, "%+.2f%%", t.pnlPct), Color.rgb(94,168,255));
    }

    private static void notify(Context c, int id, String title, String body, int color) {
        Intent open = new Intent(c, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(c, id, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(c, CHANNEL) : new Notification.Builder(c);
        Notification n = b.setContentTitle(title).setContentText(body).setStyle(new Notification.BigTextStyle().bigText(body))
                .setSmallIcon(R.drawable.app_icon).setContentIntent(pi).setAutoCancel(true).setColor(color).build();
        ((NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE)).notify(id, n);
    }

    public static double currentPrice(String symbol) throws Exception {
        JSONObject j = new JSONObject(readUrl(API + "/api/v3/ticker/price?symbol=" + symbol));
        return Double.parseDouble(j.optString("price", "0"));
    }

    private static String readUrl(String u) throws Exception {
        HttpsURLConnection c = (HttpsURLConnection)new URL(u).openConnection();
        c.setConnectTimeout(8000); c.setReadTimeout(12000); c.setRequestProperty("User-Agent", "ExplosionRadarV44/4.4");
        InputStream in = c.getResponseCode() >= 200 && c.getResponseCode() < 300 ? c.getInputStream() : c.getErrorStream();
        BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder(); String line; while ((line=br.readLine())!=null) sb.append(line); br.close(); c.disconnect();
        return sb.toString();
    }
    private static String fmt(double x) {
        if (x >= 1000) return String.format(Locale.US,"%.2f",x);
        if (x >= 1) return String.format(Locale.US,"%.4f",x);
        if (x >= .01) return String.format(Locale.US,"%.6f",x);
        return String.format(Locale.US,"%.8f",x);
    }
}
'''
(base/'TradeManager.java').write_text(trade, encoding='utf-8')

# ScannerService: evaluate only manually followed trades each scan.
p = base/'ScannerService.java'
s = p.read_text(encoding='utf-8')
needle = '            sendUi(hybrid.status, hybrid.summary);\n'
if needle not in s: raise SystemExit('V4.4 ScannerService anchor missing')
s = s.replace(needle, '            java.util.List<TradeManager.TradeState> followed = TradeManager.scan(this, prefs);\n            sendUi(hybrid.status, hybrid.summary);\n', 1)
p.write_text(s, encoding='utf-8')

# MainActivity: tap a BUY card/symbol to follow. Long press stops following.
p = base/'MainActivity.java'
m = p.read_text(encoding='utf-8')
m = m.replace('import android.widget.TextView;\n', 'import android.widget.TextView;\nimport android.app.AlertDialog;\nimport android.widget.EditText;\nimport android.text.InputType;\n', 1)
# Tap handler on the simple details view.
anchor = '        details.setBackground(roundRect(Color.rgb(20, 25, 34), 18));\n'
insert = anchor + '''        details.setOnClickListener(v -> chooseBuyToFollow());\n        details.setOnLongClickListener(v -> { chooseFollowToStop(); return true; });\n'''
if anchor not in m: raise SystemExit('V4.4 MainActivity details anchor missing')
m = m.replace(anchor, insert, 1)
# Methods before prefs().
anchor2 = '    private SharedPreferences prefs() {\n'
methods = r'''    private void chooseBuyToFollow() {
        String text = details.getText().toString();
        java.util.ArrayList<String> buys = new java.util.ArrayList<>();
        String[] lines = text.split("\\n");
        for (int i=0;i<lines.length;i++) {
            String line=lines[i].trim();
            if (line.endsWith("/USDT") && i+1<lines.length && lines[i+1].contains("شراء")) buys.add(line);
        }
        if (buys.isEmpty()) { new AlertDialog.Builder(this).setMessage("اضغط متابعة فقط عندما توجد فرصة 🟢 شراء").setPositiveButton("حسنًا",null).show(); return; }
        String[] a=buys.toArray(new String[0]);
        new AlertDialog.Builder(this).setTitle("⭐ متابعة صفقة").setItems(a,(d,w)->askEntry(a[w])).show();
    }

    private void askEntry(String symbol) {
        EditText e=new EditText(this); e.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL); e.setHint("سعر شرائك الفعلي");
        new AlertDialog.Builder(this).setTitle("⭐ "+symbol).setMessage("أدخل سعر الشراء. سيبدأ التطبيق بإرسال احتفاظ أو خروج لهذه العملة فقط.")
                .setView(e).setNegativeButton("إلغاء",null).setNeutralButton("السعر الحالي",(d,w)->followCurrent(symbol))
                .setPositiveButton("متابعة",(d,w)->{ try { double x=Double.parseDouble(e.getText().toString()); startFollow(symbol,x); } catch(Exception ex){} }).show();
    }

    private void followCurrent(String symbol) { new Thread(() -> { try { double x=TradeManager.currentPrice(symbol); runOnUiThread(()->startFollow(symbol,x)); } catch(Exception ignored){} }).start(); }
    private void startFollow(String symbol,double entry) {
        if(entry<=0)return; SharedPreferences p=prefs(); String raw=p.getString("follow_symbols","");
        if(!(","+raw+",").contains(","+symbol+",")) raw=raw.isEmpty()?symbol:raw+","+symbol;
        double stop=entry*0.97; p.edit().putString("follow_symbols",raw).putLong("follow_entry_"+symbol,Double.doubleToLongBits(entry))
                .putLong("follow_stop_"+symbol,Double.doubleToLongBits(stop)).remove("follow_state_"+symbol).apply();
        new AlertDialog.Builder(this).setMessage("⭐ بدأت متابعة "+symbol+"\nالدخول: "+entry+"\nستصلك إشعارات احتفاظ أو خروج عند تغير الحالة.").setPositiveButton("حسنًا",null).show();
    }
    private void chooseFollowToStop() {
        String raw=prefs().getString("follow_symbols",""); if(raw==null||raw.trim().isEmpty())return;
        String[] a=raw.split(","); new AlertDialog.Builder(this).setTitle("إيقاف المتابعة").setItems(a,(d,w)->stopFollow(a[w])).show();
    }
    private void stopFollow(String symbol) {
        SharedPreferences p=prefs(); String raw=p.getString("follow_symbols",""); java.util.ArrayList<String> keep=new java.util.ArrayList<>();
        for(String s:raw.split(","))if(!s.trim().equals(symbol))keep.add(s.trim());
        p.edit().putString("follow_symbols",android.text.TextUtils.join(",",keep)).remove("follow_entry_"+symbol).remove("follow_stop_"+symbol).remove("follow_state_"+symbol).apply();
    }

'''
if anchor2 not in m: raise SystemExit('V4.4 prefs anchor missing')
m=m.replace(anchor2,methods+anchor2,1)
p.write_text(m,encoding='utf-8')

# BUY quality guard: keep early discovery sensitive, but block BUY against a clearly weak 15m structure.
# This changes only WATCH -> BUY confirmation; it does not remove candidates from Early Hunt.
hp = base/'HybridEngine.java'
hs = hp.read_text(encoding='utf-8')
old_buy = '''                boolean confirmedBuy = (retestBuy || breakoutBuy)
                        && c.p24 <= 10.0
                        && !c.catalystNegative;
'''
new_buy = '''                // V4.4.1: BUY needs local trigger plus 15m trend quality.
                // Early Hunt / ARMED remain untouched, so detection stays early.
                boolean trend15Healthy = a.lastClose >= a.ema20
                        && a.ema20 >= a.ema50
                        && a.macdHist >= 0.0;
                boolean trend15Recovering = a.lastClose >= a.ema20
                        && a.macdHist > a.prevMacdHist
                        && a.rsi >= 48.0;
                boolean buyTrendGate = trend15Healthy || trend15Recovering;
                boolean confirmedBuy = (retestBuy || breakoutBuy)
                        && buyTrendGate
                        && c.p24 <= 10.0
                        && !c.catalystNegative;
'''
if old_buy not in hs:
    raise SystemExit('V4.4.1 BUY gate anchor missing')
hs = hs.replace(old_buy, new_buy, 1)
hp.write_text(hs, encoding='utf-8')

# Version.
g=Path('app/build.gradle'); x=g.read_text(encoding='utf-8'); x=re.sub(r'versionCode\s+\d+','versionCode 11',x,count=1); x=re.sub(r'versionName\s+"[^"]+"','versionName "4.4-trade-manager"',x,count=1); g.write_text(x,encoding='utf-8')
print('V4.4 applied: manual Follow Trade -> HOLD/EXIT notifications only for selected trades')
