package com.wahshi.cryptoexplosionradar;

import android.app.Notification;
import android.app.NotificationChannel;
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
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.net.ssl.HttpsURLConnection;

/** V4.2 market-wide Early Hunt engine. */
public final class HybridEngine {
    private static final String API = "https://api.binance.com";
    private static final String CHANNEL = "hybrid_buy_alerts_v42";
    private static final double MIN_QUOTE_24H = 1_500_000.0;
    private static final int ROLLING_BATCH = 100;
    private static final int MAX_DISPLAY = 10;
    private static final int MAX_CONFIRM = 8;
    private static final long BUY_COOLDOWN_MS = 6L * 60L * 60L * 1000L;
    private static final long CATALYST_MEMORY_MS = 6L * 60L * 60L * 1000L;

    private HybridEngine() {}

    public static ScanResult scan(Context context,
                                  List<ScannerService.WhaleSignal> whales,
                                  CatalystEngine.ScanResult catalyst,
                                  SharedPreferences prefs) throws Exception {
        ensureChannel(context);
        long now = System.currentTimeMillis();

        Map<String, ScannerService.WhaleSignal> whaleBySymbol = new HashMap<>();
        if (whales != null) {
            for (ScannerService.WhaleSignal w : whales) {
                if (w != null && w.symbol != null) whaleBySymbol.put(w.symbol, w);
            }
        }

        if (catalyst != null) {
            SharedPreferences.Editor e = prefs.edit();
            for (String s : catalyst.freshPositiveSymbols) {
                e.putLong("hybrid_cat_pos_" + s, now + CATALYST_MEMORY_MS);
            }
            for (String s : catalyst.freshNegativeSymbols) {
                e.putLong("hybrid_cat_neg_" + s, now + CATALYST_MEMORY_MS);
            }
            e.apply();
        }

        Map<String, DayTicker> day = load24h();
        List<String> eligible = new ArrayList<>(day.keySet());
        Map<String, HourTicker> hour = loadRolling1h(eligible);

        List<Candidate> candidates = new ArrayList<>();
        for (String symbol : eligible) {
            DayTicker d = day.get(symbol);
            HourTicker h = hour.get(symbol);
            if (d == null || h == null || d.last <= 0 || h.last <= 0) continue;

            double p24 = d.changePct();
            double p1 = h.changePct();
            if (p24 < -8.0 || p24 > 10.0) continue;
            if (p1 < -1.8 || p1 > 5.5) continue;

            double avgHourlyQ = d.quoteVolume / 24.0;
            double avgHourlyN = d.trades / 24.0;
            double rvol = avgHourlyQ > 0 ? h.quoteVolume / avgHourlyQ : 0;
            double tradeAccel = avgHourlyN > 0 ? h.trades / avgHourlyN : 0;
            double spread = d.spreadBps();
            if (spread > 45.0) continue;

            double nearHigh = d.high > 0 ? d.last / d.high : 0;
            double score = 0.0;
            List<String> reasons = new ArrayList<>();

            if (rvol >= 1.4) {
                score += Math.min(1.8, 0.55 + log2(Math.max(1.0, rvol)) * 0.55);
                reasons.add(String.format(Locale.US, "RVOL %.1fx", rvol));
            }
            if (tradeAccel >= 1.35) {
                score += Math.min(1.25, 0.45 + log2(Math.max(1.0, tradeAccel)) * 0.42);
                reasons.add(String.format(Locale.US, "Trades %.1fx", tradeAccel));
            }
            if (p1 >= 0.15 && p1 <= 4.5) {
                score += 0.75;
                reasons.add(String.format(Locale.US, "1h %+.2f%%", p1));
            } else if (p1 > -0.20 && rvol >= 1.8) {
                score += 0.30;
                reasons.add("Quiet volume");
            }
            if (p24 >= -3.0 && p24 <= 6.0) {
                score += 0.60;
                reasons.add(String.format(Locale.US, "24h %+.2f%%", p24));
            }
            if (nearHigh >= 0.94 && nearHigh <= 1.003) {
                score += 0.45;
                reasons.add("Near 24h high");
            }
            if (spread <= 15.0) score += 0.25;

            String base = symbol.substring(0, symbol.length() - 4);
            ScannerService.WhaleSignal whale = whaleBySymbol.get(base);
            if (whale != null) {
                score += whale.strong ? 1.25 : 0.80;
                reasons.add(whale.strong ? "Whale strong" : "Whale");
            }

            boolean catPos = prefs.getLong("hybrid_cat_pos_" + base, 0L) > now;
            boolean catNeg = prefs.getLong("hybrid_cat_neg_" + base, 0L) > now;
            if (catPos) {
                score += 1.20;
                reasons.add("Catalyst +");
            }
            if (catNeg) {
                score -= 2.00;
                reasons.add("Catalyst -");
            }

            boolean hasFlow = rvol >= 1.25 || tradeAccel >= 1.25 || whale != null || catPos;
            if (score >= 2.35 && hasFlow) {
                Candidate c = new Candidate();
                c.symbol = symbol;
                c.base = base;
                c.score = score;
                c.rvol = rvol;
                c.tradeAccel = tradeAccel;
                c.p1 = p1;
                c.p24 = p24;
                c.spread = spread;
                c.nearHigh = nearHigh;
                c.whale = whale;
                c.catalystPositive = catPos;
                c.catalystNegative = catNeg;
                c.reasons = reasons;
                candidates.add(c);
            }
        }

        candidates.sort((a, b) -> Double.compare(b.score, a.score));
        int confirmN = Math.min(MAX_CONFIRM, candidates.size());
        int buyCount = 0;
        int armedCount = 0;

        for (int i = 0; i < confirmN; i++) {
            Candidate c = candidates.get(i);
            try {
                Analysis a = analyze15m(c.symbol);
                c.analysis = a;
                if (a == null) continue;
                c.armed = isArmed(c, a);
                if (c.armed) armedCount++;

                double total = c.score + a.score;
                boolean retestBuy = a.retest
                        && total >= 6.3
                        && a.rvol >= 1.30
                        && a.takerBuyRatio >= 0.50;
                boolean breakoutBuy = a.breakout
                        && total >= 7.2
                        && a.rvol >= 1.70
                        && a.takerBuyRatio >= 0.55
                        && a.tests >= 2;

                if ((retestBuy || breakoutBuy) && c.p24 <= 10.0 && !c.catalystNegative) {
                    if (maybeBuyAlert(context, prefs, c, a, total)) buyCount++;
                }
            } catch (Exception ignored) {
                // A single symbol must never abort the market-wide scan.
            }
        }

        ScanResult out = new ScanResult();
        out.candidates = candidates;
        out.armedCount = armedCount;
        out.buyCount = buyCount;
        out.hot = armedCount > 0;
        out.status = buyCount > 0
                ? "🚨 BUY confirmed • Hybrid V4.2"
                : armedCount > 0
                ? "🎯 ARMED • " + armedCount + " مرشح"
                : candidates.isEmpty()
                ? "Hybrid V4.2 • مراقبة السوق"
                : "🔎 Early Hunt • " + candidates.size() + " مرشح";
        out.summary = formatSummary(candidates, armedCount, buyCount);
        return out;
    }

    private static Map<String, DayTicker> load24h() throws Exception {
        JSONArray rows = new JSONArray(readUrl(API + "/api/v3/ticker/24hr"));
        Map<String, DayTicker> out = new HashMap<>();
        for (int i = 0; i < rows.length(); i++) {
            JSONObject j = rows.optJSONObject(i);
            if (j == null) continue;
            String symbol = j.optString("symbol", "");
            if (!eligibleSymbol(symbol)) continue;
            DayTicker t = DayTicker.from(j);
            if (t.last <= 0 || t.quoteVolume < MIN_QUOTE_24H) continue;
            out.put(symbol, t);
        }
        return out;
    }

    private static Map<String, HourTicker> loadRolling1h(List<String> symbols) throws Exception {
        Map<String, HourTicker> out = new HashMap<>();
        for (int start = 0; start < symbols.size(); start += ROLLING_BATCH) {
            int end = Math.min(start + ROLLING_BATCH, symbols.size());
            JSONArray names = new JSONArray();
            for (int i = start; i < end; i++) names.put(symbols.get(i));
            String encoded = URLEncoder.encode(names.toString(), StandardCharsets.UTF_8.name());
            String url = API + "/api/v3/ticker?symbols=" + encoded
                    + "&windowSize=1h&type=FULL&symbolStatus=TRADING";
            JSONArray batch = new JSONArray(readUrl(url));
            for (int i = 0; i < batch.length(); i++) {
                JSONObject j = batch.optJSONObject(i);
                if (j == null) continue;
                String symbol = j.optString("symbol", "");
                if (!symbol.isEmpty()) out.put(symbol, HourTicker.from(j));
            }
        }
        return out;
    }

    private static Analysis analyze15m(String symbol) throws Exception {
        String u = API + "/api/v3/klines?symbol=" + symbol + "&interval=15m&limit=50";
        JSONArray rows = new JSONArray(readUrl(u));
        List<Bar> closed = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (int i = 0; i < rows.length(); i++) {
            JSONArray r = rows.optJSONArray(i);
            if (r == null) continue;
            long closeTime = r.optLong(6, Long.MAX_VALUE);
            if (closeTime < now) closed.add(Bar.from(r));
        }
        if (closed.size() < 24) return null;

        Bar last = closed.get(closed.size() - 1);
        Bar prev = closed.get(closed.size() - 2);

        List<Double> qv = new ArrayList<>();
        List<Double> nt = new ArrayList<>();
        for (int i = closed.size() - 21; i < closed.size() - 1; i++) {
            Bar b = closed.get(i);
            qv.add(b.quoteVolume);
            nt.add((double) b.trades);
        }
        double medQ = median(qv);
        double medN = median(nt);
        double rvol = medQ > 0 ? last.quoteVolume / medQ : 0;
        double tradeR = medN > 0 ? last.trades / medN : 0;
        double taker = last.quoteVolume > 0 ? last.takerBuyQuote / last.quoteVolume : 0;

        int from = Math.max(0, closed.size() - 13);
        int to = closed.size() - 1;
        double resistance = 0;
        for (int i = from; i < to; i++) resistance = Math.max(resistance, closed.get(i).high);
        int tests = 0;
        for (int i = from; i < to; i++) {
            if (resistance > 0 && closed.get(i).high >= resistance * 0.995) tests++;
        }

        boolean higherLows = false;
        if (closed.size() >= 5) {
            double l1 = closed.get(closed.size() - 5).low;
            double l2 = closed.get(closed.size() - 4).low;
            double l3 = closed.get(closed.size() - 3).low;
            double l4 = closed.get(closed.size() - 2).low;
            higherLows = l1 <= l2 && l2 <= l3 && l3 <= l4;
        }

        double recentRange = 0;
        int recentN = 0;
        for (int i = closed.size() - 7; i < closed.size() - 1; i++) {
            Bar b = closed.get(i);
            if (b.close > 0) {
                recentRange += (b.high - b.low) / b.close;
                recentN++;
            }
        }
        double priorRange = 0;
        int priorN = 0;
        for (int i = closed.size() - 19; i < closed.size() - 7; i++) {
            Bar b = closed.get(i);
            if (b.close > 0) {
                priorRange += (b.high - b.low) / b.close;
                priorN++;
            }
        }
        boolean compression = recentN > 0 && priorN > 0
                && (recentRange / recentN) <= (priorRange / priorN) * 0.88;

        boolean nearBreakout = resistance > 0
                && last.close >= resistance * 0.990
                && last.close <= resistance * 1.006;

        boolean breakout = resistance > 0
                && last.close > resistance * 1.001
                && rvol >= 1.50
                && last.close >= last.high * 0.993;

        double prevRes = 0;
        int pfrom = Math.max(0, closed.size() - 14);
        int pto = closed.size() - 2;
        for (int i = pfrom; i < pto; i++) prevRes = Math.max(prevRes, closed.get(i).high);
        boolean prevBreak = prevRes > 0 && prev.close > prevRes * 1.001;
        boolean retest = prevBreak
                && last.low <= prevRes * 1.005
                && last.close > prevRes
                && taker >= 0.50;

        double score = 0;
        if (rvol >= 1.30) score += 1.00;
        if (rvol >= 2.00) score += 0.35;
        if (tradeR >= 1.40) score += 0.65;
        if (taker >= 0.52) score += 0.70;
        if (taker >= 0.58) score += 0.30;
        if (higherLows) score += 0.60;
        if (tests >= 2) score += 0.50;
        if (compression) score += 0.40;
        if (nearBreakout) score += 0.40;
        if (breakout) score += 1.50;
        if (retest) score += 2.00;

        double invalidation = last.low;
        for (int i = Math.max(0, closed.size() - 6); i < closed.size() - 1; i++) {
            invalidation = Math.min(invalidation, closed.get(i).low);
        }

        Analysis a = new Analysis();
        a.score = score;
        a.rvol = rvol;
        a.tradeRatio = tradeR;
        a.takerBuyRatio = taker;
        a.resistance = resistance;
        a.breakout = breakout;
        a.retest = retest;
        a.nearBreakout = nearBreakout;
        a.higherLows = higherLows;
        a.compression = compression;
        a.tests = tests;
        a.lastClose = last.close;
        a.invalidation = invalidation;
        return a;
    }

    private static boolean isArmed(Candidate c, Analysis a) {
        if (c.catalystNegative) return false;
        boolean pressure = a.rvol >= 1.20 || a.tradeRatio >= 1.30 || a.takerBuyRatio >= 0.53;
        boolean structure = a.nearBreakout || a.higherLows || a.compression || a.tests >= 2;
        return c.score >= 3.0 && pressure && structure;
    }

    private static boolean maybeBuyAlert(Context context,
                                         SharedPreferences prefs,
                                         Candidate c,
                                         Analysis a,
                                         double totalScore) {
        long now = System.currentTimeMillis();
        String key = "hybrid_buy_" + c.symbol;
        long last = prefs.getLong(key, 0L);
        if (now - last < BUY_COOLDOWN_MS) return false;
        prefs.edit().putLong(key, now).apply();

        double entryLow = Math.max(a.resistance, a.lastClose * 0.997);
        double entryHigh = a.lastClose * 1.003;

        List<String> badges = new ArrayList<>();
        badges.add("Early Hunt");
        if (c.whale != null) badges.add(c.whale.strong ? "Whale strong" : "Whale");
        if (c.catalystPositive) badges.add("Catalyst");
        badges.add(a.retest ? "Retest" : "Breakout");

        String title = "🚨 BUY — " + c.base + "/USDT";
        String body = "تأكيد: " + fmt(entryLow) + "–" + fmt(entryHigh)
                + " • إبطال: " + fmt(a.invalidation)
                + "\nRVOL15m " + String.format(Locale.US, "%.1fx", a.rvol)
                + " • Taker Buy " + String.format(Locale.US, "%.0f%%", a.takerBuyRatio * 100.0)
                + " • Score " + String.format(Locale.US, "%.1f", totalScore)
                + "\n" + join(badges, " + ");

        Intent open = new Intent(context, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(context, c.symbol.hashCode(), open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(context, CHANNEL)
                : new Notification.Builder(context);
        Notification n = b.setContentTitle(title)
                .setContentText(body)
                .setStyle(new Notification.BigTextStyle().bigText(body))
                .setSmallIcon(R.drawable.app_icon)
                .setContentIntent(pi)
                .setAutoCancel(true)
                .setColor(Color.rgb(245, 183, 43))
                .build();
        ((NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE))
                .notify(9000 + Math.abs(c.symbol.hashCode() % 800), n);
        return true;
    }

    private static String formatSummary(List<Candidate> candidates, int armedCount, int buyCount) {
        StringBuilder sb = new StringBuilder();
        sb.append("🎯 Hybrid Explosion Radar V4.2\n");
        sb.append("Early Hunt يفحص جميع أزواج Binance Spot/USDT تقريبًا • تأكيد 15m لأفضل المرشحين.\n");
        sb.append("Early Hunt: ").append(candidates.size())
                .append(" • ARMED: ").append(armedCount)
                .append(" • BUY الآن: ").append(buyCount).append("\n\n");

        if (candidates.isEmpty()) {
            sb.append("لا توجد حركة مبكرة قوية الآن. Whale/Catalyst يبقيان طبقات تقوية وليسا شرطًا.");
            return sb.toString();
        }

        int shown = Math.min(MAX_DISPLAY, candidates.size());
        for (int i = 0; i < shown; i++) {
            Candidate c = candidates.get(i);
            sb.append(i + 1).append(". ").append(c.base).append("/USDT");
            if (c.armed) sb.append("  🎯 ARMED"); else sb.append("  🔎");
            if (c.whale != null) sb.append(c.whale.strong ? " 🐋🔥" : " 🐋");
            if (c.catalystPositive) sb.append(" ⚡");
            if (c.catalystNegative) sb.append(" ⚠️");
            sb.append("\n   Score ").append(String.format(Locale.US, "%.1f", c.score))
                    .append(" • RVOL1h ").append(String.format(Locale.US, "%.1fx", c.rvol))
                    .append(" • Trades ").append(String.format(Locale.US, "%.1fx", c.tradeAccel))
                    .append(" • 1h ").append(String.format(Locale.US, "%+.2f%%", c.p1))
                    .append(" • 24h ").append(String.format(Locale.US, "%+.2f%%", c.p24));
            if (c.analysis != null) {
                sb.append("\n   15m: RVOL ").append(String.format(Locale.US, "%.1fx", c.analysis.rvol))
                        .append(" • Taker ").append(String.format(Locale.US, "%.0f%%", c.analysis.takerBuyRatio * 100.0));
                if (c.analysis.retest) sb.append(" • Retest ✅");
                else if (c.analysis.breakout) sb.append(" • Breakout ✅");
                else if (c.analysis.nearBreakout) sb.append(" • Near resistance");
                if (c.analysis.higherLows) sb.append(" • Higher lows");
                if (c.analysis.compression) sb.append(" • Compression");
            }
            sb.append("\n");
        }
        sb.append("\n📲 Push notifications: BUY فقط. Early Hunt وARMED يظهران داخل التطبيق.");
        return sb.toString();
    }

    private static void ensureChannel(Context context) {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        NotificationChannel c = new NotificationChannel(
                CHANNEL, "Hybrid confirmed BUY alerts", NotificationManager.IMPORTANCE_HIGH);
        c.enableVibration(true);
        c.setVibrationPattern(new long[]{0, 220, 100, 320});
        nm.createNotificationChannel(c);
    }

    private static String readUrl(String url) throws Exception {
        HttpsURLConnection c = (HttpsURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(10_000);
        c.setReadTimeout(18_000);
        c.setRequestProperty("Accept", "application/json");
        c.setRequestProperty("User-Agent", "HybridExplosionRadarV42/4.2");
        int code = c.getResponseCode();
        InputStream stream = code >= 200 && code < 300 ? c.getInputStream() : c.getErrorStream();
        String text = readStream(stream);
        c.disconnect();
        if (code < 200 || code >= 300) throw new Exception("Binance HTTP " + code + " • " + trim(text));
        return text;
    }

    private static String readStream(InputStream stream) throws Exception {
        if (stream == null) return "";
        BufferedReader br = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) sb.append(line);
        br.close();
        return sb.toString();
    }

    private static boolean eligibleSymbol(String s) {
        if (s == null || !s.endsWith("USDT") || s.length() <= 4) return false;
        String base = s.substring(0, s.length() - 4).toUpperCase(Locale.US);
        String[] skip = {"USDC","FDUSD","TUSD","USDP","DAI","EUR","TRY","BRL","UAH","BUSD"};
        for (String x : skip) if (base.equals(x)) return false;
        return !(base.endsWith("UP") || base.endsWith("DOWN")
                || base.endsWith("BULL") || base.endsWith("BEAR"));
    }

    private static double median(List<Double> xs) {
        if (xs == null || xs.isEmpty()) return 0;
        List<Double> a = new ArrayList<>(xs);
        Collections.sort(a);
        int n = a.size();
        return n % 2 == 1 ? a.get(n / 2) : (a.get(n / 2 - 1) + a.get(n / 2)) / 2.0;
    }

    private static double log2(double x) { return Math.log(x) / Math.log(2.0); }
    private static double d(JSONObject j, String key) {
        try { return Double.parseDouble(j.optString(key, "0")); }
        catch (Exception e) { return j.optDouble(key, 0); }
    }
    private static long l(JSONObject j, String key) {
        try { return Long.parseLong(j.optString(key, "0")); }
        catch (Exception e) { return j.optLong(key, 0); }
    }
    private static String fmt(double x) {
        if (x >= 1000) return String.format(Locale.US, "%.2f", x);
        if (x >= 1) return String.format(Locale.US, "%.4f", x);
        if (x >= 0.01) return String.format(Locale.US, "%.6f", x);
        return String.format(Locale.US, "%.8f", x);
    }
    private static String join(List<String> xs, String sep) {
        StringBuilder sb = new StringBuilder();
        for (String x : xs) {
            if (sb.length() > 0) sb.append(sep);
            sb.append(x);
        }
        return sb.toString();
    }
    private static String trim(String x) {
        if (x == null) return "";
        return x.length() > 160 ? x.substring(0, 160) : x;
    }

    public static final class ScanResult {
        public String status = "";
        public String summary = "";
        public boolean hot;
        public int armedCount;
        public int buyCount;
        public List<Candidate> candidates = new ArrayList<>();
    }

    static final class Candidate {
        String symbol;
        String base;
        double score;
        double rvol;
        double tradeAccel;
        double p1;
        double p24;
        double spread;
        double nearHigh;
        boolean catalystPositive;
        boolean catalystNegative;
        boolean armed;
        ScannerService.WhaleSignal whale;
        Analysis analysis;
        List<String> reasons;
    }

    static final class Analysis {
        double score;
        double rvol;
        double tradeRatio;
        double takerBuyRatio;
        double resistance;
        double lastClose;
        double invalidation;
        int tests;
        boolean breakout;
        boolean retest;
        boolean nearBreakout;
        boolean higherLows;
        boolean compression;
    }

    static final class DayTicker {
        double last;
        double open;
        double high;
        double bid;
        double ask;
        double quoteVolume;
        long trades;

        static DayTicker from(JSONObject j) {
            DayTicker t = new DayTicker();
            t.last = d(j, "lastPrice");
            t.open = d(j, "openPrice");
            t.high = d(j, "highPrice");
            t.bid = d(j, "bidPrice");
            t.ask = d(j, "askPrice");
            t.quoteVolume = d(j, "quoteVolume");
            t.trades = l(j, "count");
            return t;
        }

        double changePct() { return open > 0 ? (last / open - 1.0) * 100.0 : 0; }
        double spreadBps() {
            double mid = (bid + ask) / 2.0;
            return mid > 0 ? (ask - bid) / mid * 10_000.0 : 9999.0;
        }
    }

    static final class HourTicker {
        double last;
        double open;
        double quoteVolume;
        long trades;

        static HourTicker from(JSONObject j) {
            HourTicker t = new HourTicker();
            t.last = d(j, "lastPrice");
            t.open = d(j, "openPrice");
            t.quoteVolume = d(j, "quoteVolume");
            t.trades = l(j, "count");
            return t;
        }

        double changePct() { return open > 0 ? (last / open - 1.0) * 100.0 : 0; }
    }

    static final class Bar {
        double high;
        double low;
        double close;
        double quoteVolume;
        long trades;
        double takerBuyQuote;

        static Bar from(JSONArray r) {
            Bar b = new Bar();
            b.high = parse(r.optString(2, "0"));
            b.low = parse(r.optString(3, "0"));
            b.close = parse(r.optString(4, "0"));
            b.quoteVolume = parse(r.optString(7, "0"));
            b.trades = r.optLong(8, 0);
            b.takerBuyQuote = parse(r.optString(10, "0"));
            return b;
        }

        static double parse(String s) {
            try { return Double.parseDouble(s); } catch (Exception e) { return 0; }
        }
    }
}
