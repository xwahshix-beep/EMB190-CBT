package com.wahshi.cryptoexplosionradar;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.HttpsURLConnection;

public class ScannerService extends Service {
    public static final String ACTION_START = "com.wahshi.cryptoexplosionradar.START";
    public static final String ACTION_STOP = "com.wahshi.cryptoexplosionradar.STOP";
    public static final String ACTION_UI = "com.wahshi.cryptoexplosionradar.UI";

    private static final String SERVICE_CHANNEL = "radar_service";
    private static final String ALERT_CHANNEL = "radar_buy_alerts";
    private static final int SERVICE_NOTIFICATION_ID = 1001;

    private static final long QUIET_SCREEN_ON_MS = 60_000L;
    private static final long QUIET_SCREEN_OFF_MS = 90_000L;
    private static final long HOT_INTERVAL_MS = 15_000L;
    private static final long HOT_MARKET_REFRESH_MS = 30_000L;
    private static final long HOT_DURATION_MS = 6 * 60_000L;
    private static final double MIN_24H_QUOTE_USDT = 1_500_000.0;
    private static final int ROLLING_BATCH_SIZE = 100;
    private static final String API_BASE = "https://api.binance.com";

    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
    private volatile boolean running = false;
    private volatile long hotUntil = 0L;
    private volatile long lastMarketScan = 0L;
    private volatile List<Candidate> cachedCandidates = new ArrayList<>();
    private SharedPreferences prefs;

    @Override public void onCreate() {
        super.onCreate();
        prefs = getSharedPreferences("radar", MODE_PRIVATE);
        createChannels();
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();
        if (ACTION_STOP.equals(action)) {
            stopScanner();
            return START_NOT_STICKY;
        }
        startScanner();
        return START_STICKY;
    }

    private synchronized void startScanner() {
        if (running) return;
        running = true;
        startForeground(SERVICE_NOTIFICATION_ID, buildServiceNotification("Smart Eco يعمل • وضع هادئ"));
        sendUi("Smart Eco يعمل", "أول فحص خلال ثوانٍ…");
        scheduleNext(2_000L);
    }

    private synchronized void stopScanner() {
        running = false;
        cachedCandidates = new ArrayList<>();
        hotUntil = 0L;
        sendUi("الرادار متوقف", "لن تتم مراقبة السوق حتى تشغيله من جديد.");
        if (Build.VERSION.SDK_INT >= 24) stopForeground(STOP_FOREGROUND_REMOVE); else stopForeground(true);
        stopSelf();
    }

    private void scheduleNext(long delayMs) {
        if (!running) return;
        executor.schedule(this::runCycleSafe, delayMs, TimeUnit.MILLISECONDS);
    }

    private void runCycleSafe() {
        if (!running) return;
        long next = QUIET_SCREEN_OFF_MS;
        try {
            long now = System.currentTimeMillis();
            boolean reuseHot = now < hotUntil && !cachedCandidates.isEmpty() && now - lastMarketScan < HOT_MARKET_REFRESH_MS;
            List<Candidate> candidates;

            if (reuseHot) {
                candidates = cachedCandidates;
            } else {
                candidates = scanMarket();
                cachedCandidates = candidates;
                lastMarketScan = now;
                if (!candidates.isEmpty()) hotUntil = now + HOT_DURATION_MS;
            }

            if (!candidates.isEmpty()) {
                confirmTopCandidates(candidates, 3);
            }

            boolean hot = !candidates.isEmpty() || System.currentTimeMillis() < hotUntil;
            boolean interactive = isScreenInteractive();
            next = hot ? HOT_INTERVAL_MS : (interactive ? QUIET_SCREEN_ON_MS : QUIET_SCREEN_OFF_MS);

            String status = hot ? "Early Hunt • فحص سريع مؤقت" : "Smart Eco • مراقبة هادئة";
            String details = formatCandidates(candidates, next);
            sendUi(status, details);
            updateServiceNotification(status + " • التالي ~" + Math.max(1, next / 1000) + "s");
        } catch (Exception e) {
            next = 120_000L;
            sendUi("Smart Eco • خطأ اتصال مؤقت", "سيعيد المحاولة تلقائيًا. " + safe(e.getMessage()));
            updateServiceNotification("Smart Eco • إعادة محاولة لاحقًا");
        } finally {
            scheduleNext(next);
        }
    }

    private List<Candidate> scanMarket() throws Exception {
        JSONArray a24 = new JSONArray(readUrl(API_BASE + "/api/v3/ticker/24hr"));

        Map<String, Ticker> m24 = new HashMap<>();
        List<String> eligible = new ArrayList<>();
        for (int i = 0; i < a24.length(); i++) {
            JSONObject j = a24.getJSONObject(i);
            String s = j.optString("symbol", "");
            if (!eligibleSymbol(s)) continue;
            Ticker t = Ticker.from24(j);
            if (t.quoteVolume >= MIN_24H_QUOTE_USDT) {
                m24.put(s, t);
                eligible.add(s);
            }
        }

        Map<String, Ticker> m1 = loadRolling1h(eligible, m24);

        List<Candidate> out = new ArrayList<>();
        for (Map.Entry<String, Ticker> e : m24.entrySet()) {
            String symbol = e.getKey();
            Ticker d24 = e.getValue();
            Ticker d1 = m1.get(symbol);
            if (d1 == null || d24.last <= 0 || d1.last <= 0) continue;

            double p24 = d24.changePct();
            double p1 = d1.changePct();
            if (p24 > 15.0 || p1 > 6.5) continue;

            double avgHourlyQ = d24.quoteVolume / 24.0;
            double avgHourlyN = d24.trades / 24.0;
            double rvol = avgHourlyQ > 0 ? d1.quoteVolume / avgHourlyQ : 0;
            double tradeAccel = avgHourlyN > 0 ? d1.trades / avgHourlyN : 0;
            double spread = d24.spreadBps();
            if (spread > 35.0) continue;

            double score = 0.0;
            List<String> reasons = new ArrayList<>();
            if (rvol >= 1.8) {
                score += Math.min(2.0, 0.8 + log2(Math.max(1.0, rvol)) * 0.55);
                reasons.add(String.format(Locale.US, "RVOL %.1fx", rvol));
            }
            if (tradeAccel >= 1.6) {
                score += Math.min(1.5, 0.65 + log2(Math.max(1.0, tradeAccel)) * 0.40);
                reasons.add(String.format(Locale.US, "Trades %.1fx", tradeAccel));
            }
            if (p1 >= 0.35 && p1 <= 6.5) {
                score += 0.8;
                reasons.add(String.format(Locale.US, "1h %+,.2f%%", p1));
            }
            if (p24 >= -3.0 && p24 <= 8.0) {
                score += 0.7;
                reasons.add(String.format(Locale.US, "24h %+,.2f%%", p24));
            } else if (p24 > 8.0) {
                score -= 0.6;
            }
            double nearHigh = d24.high > 0 ? d24.last / d24.high : 0;
            if (nearHigh >= 0.965 && nearHigh <= 1.002) {
                score += 0.6;
                reasons.add("Near high");
            }
            if (spread <= 12.0) score += 0.25;

            if (score >= 3.2) {
                out.add(new Candidate(symbol, score, rvol, tradeAccel, p1, p24, spread, reasons));
            }
        }

        out.sort((a,b) -> Double.compare(b.score, a.score));
        if (out.size() > 12) return new ArrayList<>(out.subList(0, 12));
        return out;
    }

    private Map<String, Ticker> loadRolling1h(List<String> symbols, Map<String, Ticker> m24) throws Exception {
        Map<String, Ticker> out = new HashMap<>();
        for (int start = 0; start < symbols.size(); start += ROLLING_BATCH_SIZE) {
            int end = Math.min(start + ROLLING_BATCH_SIZE, symbols.size());
            JSONArray names = new JSONArray();
            for (int i = start; i < end; i++) names.put(symbols.get(i));

            String encoded = URLEncoder.encode(names.toString(), StandardCharsets.UTF_8.name());
            String url = API_BASE + "/api/v3/ticker?symbols=" + encoded + "&windowSize=1h&type=FULL&symbolStatus=TRADING";
            JSONArray batch = new JSONArray(readUrl(url));

            for (int i = 0; i < batch.length(); i++) {
                JSONObject j = batch.getJSONObject(i);
                String s = j.optString("symbol", "");
                if (!m24.containsKey(s)) continue;
                out.put(s, Ticker.fromRolling(j));
            }
        }
        return out;
    }

    private void confirmTopCandidates(List<Candidate> candidates, int max) {
        int count = Math.min(max, candidates.size());
        for (int i = 0; i < count; i++) {
            Candidate c = candidates.get(i);
            try {
                Analysis a = analyze15m(c.symbol);
                if (a == null) continue;
                double buyScore = c.score + a.score;
                boolean trigger = a.breakout || a.retest;
                if (!trigger || buyScore < 7.0 || a.rvol < 1.8) continue;
                if (a.takerBuyRatio < 0.54 && !a.retest) continue;
                if (c.p24 > 15.0) continue;
                maybeAlert(c, a, buyScore);
            } catch (Exception ignored) {}
        }
    }

    private Analysis analyze15m(String symbol) throws Exception {
        String u = API_BASE + "/api/v3/klines?symbol=" + symbol + "&interval=15m&limit=50";
        JSONArray rows = new JSONArray(readUrl(u));
        List<Bar> closed = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (int i = 0; i < rows.length(); i++) {
            JSONArray r = rows.getJSONArray(i);
            long closeTime = r.optLong(6, Long.MAX_VALUE);
            if (closeTime < now) closed.add(Bar.from(r));
        }
        if (closed.size() < 22) return null;

        Bar last = closed.get(closed.size()-1);
        Bar prev = closed.get(closed.size()-2);
        List<Bar> hist = closed.subList(closed.size()-21, closed.size()-1);
        List<Double> qv = new ArrayList<>();
        List<Double> nt = new ArrayList<>();
        for (Bar b : hist) { qv.add(b.quoteVolume); nt.add((double)b.trades); }
        double medQ = median(qv);
        double medN = median(nt);
        double rvol = medQ > 0 ? last.quoteVolume / medQ : 0;
        double tradeR = medN > 0 ? last.trades / medN : 0;
        double taker = last.quoteVolume > 0 ? last.takerBuyQuote / last.quoteVolume : 0;

        int from = Math.max(0, closed.size()-13);
        int to = closed.size()-1;
        double resistance = 0;
        int tests = 0;
        for (int i = from; i < to; i++) resistance = Math.max(resistance, closed.get(i).high);
        for (int i = from; i < to; i++) if (closed.get(i).high >= resistance * 0.995) tests++;

        boolean breakout = last.close > resistance * 1.001 && rvol >= 1.8 && last.close >= last.high * 0.994;

        double prevRes = 0;
        int pfrom = Math.max(0, closed.size()-14);
        int pto = closed.size()-2;
        for (int i = pfrom; i < pto; i++) prevRes = Math.max(prevRes, closed.get(i).high);
        boolean prevBreak = prev.close > prevRes * 1.001;
        boolean retest = prevBreak && last.low <= prevRes * 1.004 && last.close > prevRes && taker >= 0.50;

        boolean higherLows = false;
        if (closed.size() >= 4) {
            double l1 = closed.get(closed.size()-4).low;
            double l2 = closed.get(closed.size()-3).low;
            double l3 = closed.get(closed.size()-2).low;
            higherLows = l1 < l2 && l2 < l3;
        }

        double score = 0;
        if (rvol >= 1.8) score += 1.5;
        if (tradeR >= 1.6) score += 0.8;
        if (taker >= 0.54) score += 0.9;
        if (higherLows) score += 0.6;
        if (tests >= 2) score += 0.4;
        if (breakout) score += 2.0;
        if (retest) score += 2.2;

        double invalidation = last.low;
        for (int i = Math.max(0, closed.size()-6); i < closed.size()-1; i++) {
            invalidation = Math.min(invalidation, closed.get(i).low);
        }
        return new Analysis(score, rvol, tradeR, taker, resistance, breakout, retest, last.close, invalidation);
    }

    private void maybeAlert(Candidate c, Analysis a, double buyScore) {
        long now = System.currentTimeMillis();
        String key = "alert_" + c.symbol;
        long last = prefs.getLong(key, 0L);
        if (now - last < 6 * 60 * 60_000L) return;
        prefs.edit().putLong(key, now).apply();

        double entryLow = Math.max(a.resistance, a.lastClose * 0.997);
        double entryHigh = a.lastClose * 1.003;
        String pair = c.symbol.substring(0, c.symbol.length()-4) + "/USDT";
        String title = "🚨 شراء — " + pair;
        String body = "الدخول/التأكيد: " + fmt(entryLow) + "–" + fmt(entryHigh) + " | إبطال الفكرة: " + fmt(a.invalidation);
        postBuyNotification(c.symbol, title, body);
        sendUi(title, body + "\nScore " + String.format(Locale.US, "%.1f", buyScore));
    }

    private String formatCandidates(List<Candidate> cands, long nextMs) {
        if (cands == null || cands.isEmpty()) {
            return "لا يوجد Early Hunt قوي الآن.\nالفحص التالي تقريبًا خلال " + Math.max(1, nextMs/1000) + " ثانية.\n\nالوضع الاقتصادي لا يستخدم WebSocket دائم ولا WakeLock.";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Early Hunt: ").append(cands.size()).append(" مرشح\n");
        int n = Math.min(6, cands.size());
        for (int i=0;i<n;i++) {
            Candidate c = cands.get(i);
            sb.append(i+1).append(". ").append(c.symbol.substring(0,c.symbol.length()-4)).append("/USDT")
              .append("  • Score ").append(String.format(Locale.US,"%.1f",c.score))
              .append(" • RVOL ").append(String.format(Locale.US,"%.1fx",c.rvol))
              .append(" • 1h ").append(String.format(Locale.US,"%+.2f%%",c.p1))
              .append(" • 24h ").append(String.format(Locale.US,"%+.2f%%",c.p24)).append("\n");
        }
        sb.append("\nيتم الآن فحص التأكيد بوتيرة أسرع مؤقتًا.");
        return sb.toString();
    }

    private boolean eligibleSymbol(String s) {
        if (s == null || !s.endsWith("USDT") || s.length() <= 4) return false;
        String base = s.substring(0, s.length()-4);
        String[] skip = {"USDC","FDUSD","TUSD","USDP","DAI","EUR","TRY","BRL","UAH","BUSD"};
        for (String x : skip) if (base.equals(x)) return false;
        return !(base.endsWith("UP") || base.endsWith("DOWN") || base.endsWith("BULL") || base.endsWith("BEAR"));
    }

    private String readUrl(String url) throws Exception {
        HttpsURLConnection c = (HttpsURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(8_000);
        c.setReadTimeout(12_000);
        c.setRequestProperty("User-Agent", "ExplosionRadarEco/1.2.1");
        c.setRequestProperty("Accept", "application/json");
        int code = c.getResponseCode();
        InputStream stream = code >= 200 && code < 300 ? c.getInputStream() : c.getErrorStream();
        StringBuilder sb = new StringBuilder();
        if (stream != null) {
            BufferedReader br = new BufferedReader(new InputStreamReader(stream));
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            br.close();
        }
        c.disconnect();
        String body = sb.toString();
        if (code != 200) {
            String detail = body.length() > 180 ? body.substring(0, 180) : body;
            throw new Exception("Binance HTTP " + code + (detail.isEmpty() ? "" : " • " + detail));
        }
        return body;
    }

    private void createChannels() {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        NotificationChannel service = new NotificationChannel(SERVICE_CHANNEL, "Radar background service", NotificationManager.IMPORTANCE_LOW);
        service.setDescription("Low-power market monitoring status");
        service.setSound(null, null);
        service.enableVibration(false);
        nm.createNotificationChannel(service);

        NotificationChannel alerts = new NotificationChannel(ALERT_CHANNEL, "Buy signals", NotificationManager.IMPORTANCE_HIGH);
        alerts.setDescription("Confirmed Explosion Radar buy triggers");
        alerts.enableVibration(true);
        alerts.setVibrationPattern(new long[]{0,250,150,350});
        nm.createNotificationChannel(alerts);
    }

    private Notification buildServiceNotification(String text) {
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 1, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, SERVICE_CHANNEL) : new Notification.Builder(this);
        return b.setContentTitle("Explosion Radar • Smart Eco")
                .setContentText(text)
                .setSmallIcon(R.drawable.app_icon)
                .setContentIntent(pi)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .build();
    }

    private void updateServiceNotification(String text) {
        NotificationManager nm = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        nm.notify(SERVICE_NOTIFICATION_ID, buildServiceNotification(text));
    }

    private void postBuyNotification(String symbol, String title, String body) {
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, symbol.hashCode(), open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, ALERT_CHANNEL) : new Notification.Builder(this);
        Notification n = b.setContentTitle(title)
                .setContentText(body)
                .setStyle(new Notification.BigTextStyle().bigText(body))
                .setSmallIcon(R.drawable.app_icon)
                .setContentIntent(pi)
                .setAutoCancel(true)
                .setColor(Color.rgb(245,183,43))
                .build();
        ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).notify(2000 + Math.abs(symbol.hashCode()%5000), n);
    }

    private void sendUi(String status, String details) {
        Intent i = new Intent(ACTION_UI).setPackage(getPackageName());
        i.putExtra("status", status);
        i.putExtra("details", details);
        sendBroadcast(i);
    }

    private boolean isScreenInteractive() {
        PowerManager pm = (PowerManager)getSystemService(POWER_SERVICE);
        return pm == null || pm.isInteractive();
    }

    private static double log2(double x) { return Math.log(x) / Math.log(2.0); }
    private static String safe(String x) { return x == null ? "" : x; }
    private static double d(JSONObject j, String k) { try { return Double.parseDouble(j.optString(k,"0")); } catch (Exception e) { return 0; } }
    private static long l(JSONObject j, String k) { try { return Long.parseLong(j.optString(k,"0")); } catch (Exception e) { return j.optLong(k,0); } }
    private static double jd(JSONArray a, int i) { try { return Double.parseDouble(a.optString(i,"0")); } catch (Exception e) { return 0; } }

    private static double median(List<Double> x) {
        if (x.isEmpty()) return 0;
        List<Double> a = new ArrayList<>(x);
        Collections.sort(a);
        int n = a.size();
        return n%2==1 ? a.get(n/2) : (a.get(n/2-1)+a.get(n/2))/2.0;
    }

    private static String fmt(double x) {
        if (x >= 1000) return String.format(Locale.US,"%,.2f",x);
        if (x >= 1) return trim(String.format(Locale.US,"%.4f",x));
        if (x >= 0.01) return trim(String.format(Locale.US,"%.6f",x));
        return trim(String.format(Locale.US,"%.8f",x));
    }
    private static String trim(String s) {
        if (!s.contains(".")) return s;
        while (s.endsWith("0")) s=s.substring(0,s.length()-1);
        if (s.endsWith(".")) s=s.substring(0,s.length()-1);
        return s;
    }

    @Override public void onDestroy() {
        running = false;
        executor.shutdownNow();
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    static class Ticker {
        String symbol; double last, open, high, quoteVolume, bid, ask; long trades;
        static Ticker from24(JSONObject j) {
            Ticker t = new Ticker();
            t.symbol=j.optString("symbol",""); t.last=d(j,"lastPrice"); t.open=d(j,"openPrice"); t.high=d(j,"highPrice");
            t.quoteVolume=d(j,"quoteVolume"); t.bid=d(j,"bidPrice"); t.ask=d(j,"askPrice"); t.trades=l(j,"count"); return t;
        }
        static Ticker fromRolling(JSONObject j) {
            Ticker t = new Ticker();
            t.symbol=j.optString("symbol",""); t.last=d(j,"lastPrice"); t.open=d(j,"openPrice"); t.high=d(j,"highPrice");
            t.quoteVolume=d(j,"quoteVolume"); t.trades=l(j,"count"); return t;
        }
        double changePct(){ return open>0 ? (last/open-1.0)*100.0 : 0; }
        double spreadBps(){ if (bid<=0||ask<=0) return 9999; double mid=(bid+ask)/2.0; return mid>0?(ask-bid)/mid*10000.0:9999; }
    }

    static class Candidate {
        String symbol; double score,rvol,tradeAccel,p1,p24,spread; List<String> reasons;
        Candidate(String s,double sc,double rv,double ta,double h,double d,double sp,List<String> r){symbol=s;score=sc;rvol=rv;tradeAccel=ta;p1=h;p24=d;spread=sp;reasons=r;}
    }

    static class Bar {
        double open,high,low,close,quoteVolume,takerBuyQuote; long trades;
        static Bar from(JSONArray r){ Bar b=new Bar(); b.open=jd(r,1); b.high=jd(r,2); b.low=jd(r,3); b.close=jd(r,4); b.quoteVolume=jd(r,7); b.trades=r.optLong(8,0); b.takerBuyQuote=jd(r,10); return b; }
    }

    static class Analysis {
        double score,rvol,tradeRatio,takerBuyRatio,resistance,lastClose,invalidation; boolean breakout,retest;
        Analysis(double sc,double rv,double tr,double tb,double res,boolean bo,boolean re,double lc,double inv){score=sc;rvol=rv;tradeRatio=tr;takerBuyRatio=tb;resistance=res;breakout=bo;retest=re;lastClose=lc;invalidation=inv;}
    }
}