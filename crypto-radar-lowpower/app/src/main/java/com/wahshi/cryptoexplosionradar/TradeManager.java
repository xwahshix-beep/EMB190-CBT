package com.wahshi.cryptoexplosionradar;

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
