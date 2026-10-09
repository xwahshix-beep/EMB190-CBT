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

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.net.ssl.HttpsURLConnection;

public final class CatalystEngine {
    private static final String CHANNEL = "catalyst_radar_alerts";
    private static final String BINANCE_ANNOUNCEMENTS = "https://www.binance.com/en/support/announcement/";
    private static final String BINANCE_LISTINGS = "https://www.binance.com/en/support/announcement/list/000";
    private static final String BINANCE_TELEGRAM = "https://t.me/s/binance_announcements";
    private static final long SEEN_RETENTION_MS = 7L * 24L * 60L * 60L * 1000L;

    private CatalystEngine() {}

    public static ScanResult scan(Context context,
                                  List<ScannerService.BinanceTicker> tickers,
                                  List<ScannerService.WhaleSignal> whales,
                                  SharedPreferences prefs) {
        ScanResult out = new ScanResult();
        try {
            ensureChannel(context);
            List<Announcement> announcements = fetchAnnouncements();
            out.sourceOk = !announcements.isEmpty();

            Map<String, ScannerService.BinanceTicker> bySymbol = new HashMap<>();
            for (ScannerService.BinanceTicker t : tickers) bySymbol.put(t.base, t);
            Map<String, ScannerService.WhaleSignal> whaleBySymbol = new HashMap<>();
            for (ScannerService.WhaleSignal w : whales) whaleBySymbol.put(w.symbol, w);

            boolean initialized = prefs.getBoolean("catalyst_initialized_v4", false);
            List<CatalystSignal> relevant = new ArrayList<>();

            for (Announcement a : announcements) {
                Set<String> symbols = extractSymbols(a.title, bySymbol);
                if (symbols.isEmpty()) continue;
                Classification c = classify(a.title);
                if (c.impact <= 0) continue;

                for (String symbol : symbols) {
                    ScannerService.BinanceTicker bt = bySymbol.get(symbol);
                    if (bt == null) continue;
                    CatalystSignal s = new CatalystSignal();
                    s.symbol = symbol;
                    s.title = a.title;
                    s.id = a.id;
                    s.label = c.label;
                    s.impact = c.impact;
                    s.positive = c.positive;
                    s.negative = c.negative;
                    s.change24 = bt.change24;
                    s.early = Math.abs(bt.change24) <= 8.0;
                    s.whale = whaleBySymbol.get(symbol);
                    relevant.add(s);

                    String seenKey = seenKey(a.id, symbol);
                    boolean seen = prefs.contains(seenKey);
                    if (!initialized || seen) continue;
                    if (s.positive) out.freshPositiveSymbols.add(symbol);
                    if (s.negative) out.freshNegativeSymbols.add(symbol);
                    prefs.edit().putLong(seenKey, System.currentTimeMillis()).apply();

                    if (s.positive && s.early && s.whale != null && s.impact >= 1.5) {
                        out.combinedSymbols.add(symbol);
                        out.hot = true;
                        if (prefs.getBoolean("standalone_signal_notifications", false)) post(context, 7200 + Math.abs((symbol + a.id).hashCode() % 1500),
                                "🐋⚡ حيتان + محفز — " + symbol + "/USDT",
                                "Whale net +" + money(s.whale.netUsd) + " • " + s.whale.wallets + " محافظ"
                                        + "\n⚡ " + s.label + " • 24h " + pct(bt.change24)
                                        + "\n" + shortTitle(a.title));
                    } else if (s.positive && s.early && s.impact >= 3.0) {
                        out.hot = true;
                        if (prefs.getBoolean("standalone_signal_notifications", false)) post(context, 7600 + Math.abs((symbol + a.id).hashCode() % 1500),
                                "⚡ محفز مبكر — " + symbol + "/USDT",
                                s.label + " • 24h " + pct(bt.change24) + "\n" + shortTitle(a.title));
                    } else if (s.negative && s.impact >= 3.0) {
                        out.hot = true;
                        if (prefs.getBoolean("standalone_signal_notifications", false)) post(context, 8000 + Math.abs((symbol + a.id).hashCode() % 1500),
                                "⚠️ محفز سلبي — " + symbol + "/USDT",
                                s.label + " • 24h " + pct(bt.change24) + "\n" + shortTitle(a.title));
                    }
                }
            }

            if (!initialized) {
                SharedPreferences.Editor e = prefs.edit().putBoolean("catalyst_initialized_v4", true);
                for (Announcement a : announcements) {
                    for (String symbol : extractSymbols(a.title, bySymbol)) {
                        e.putLong(seenKey(a.id, symbol), System.currentTimeMillis());
                    }
                }
                e.apply();
                out.baselineBuilt = true;
            }

            cleanupOldSeen(prefs);
            out.relevant = relevant;
            out.summary = formatSummary(out, relevant);
        } catch (Exception e) {
            out.summary = "⚡ Catalyst Radar: تعذر قراءة الإعلانات الرسمية مؤقتًا.\n" + safe(e.getMessage());
        }
        return out;
    }

    private static List<Announcement> fetchAnnouncements() throws Exception {
        LinkedHashMap<String, Announcement> out = new LinkedHashMap<>();
        try { parseBinanceHtml(readText(BINANCE_ANNOUNCEMENTS), out); } catch (Exception ignored) {}
        try { parseBinanceHtml(readText(BINANCE_LISTINGS), out); } catch (Exception ignored) {}
        if (out.size() < 5) {
            try { parseTelegramHtml(readText(BINANCE_TELEGRAM), out); } catch (Exception ignored) {}
        }
        return new ArrayList<>(out.values());
    }

    private static void parseBinanceHtml(String html, Map<String, Announcement> out) {
        if (html == null || html.isEmpty()) return;
        Pattern link = Pattern.compile("href=[\\\"']([^\\\"']*/support/announcement/detail/([A-Za-z0-9]+)[^\\\"']*)[\\\"'][^>]*>(.*?)</a>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
        Matcher m = link.matcher(html);
        while (m.find()) {
            String title = cleanHtml(m.group(3));
            if (validTitle(title)) add(out, m.group(2), title);
        }
        if (out.size() < 3) {
            Pattern jsonTitle = Pattern.compile("\\\"title\\\"\\s*:\\s*\\\"([^\\\"]{8,260})\\\"", Pattern.CASE_INSENSITIVE);
            Matcher j = jsonTitle.matcher(html);
            int n = 0;
            while (j.find() && n < 40) {
                String title = decodeJson(cleanHtml(j.group(1)));
                if (validTitle(title)) { add(out, "html_" + Integer.toHexString(title.hashCode()), title); n++; }
            }
        }
    }

    private static void parseTelegramHtml(String html, Map<String, Announcement> out) {
        if (html == null || html.isEmpty()) return;
        Pattern block = Pattern.compile("<div class=\\\"tgme_widget_message_text[^\\\"]*\\\"[^>]*>(.*?)</div>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
        Matcher m = block.matcher(html);
        int n = 0;
        while (m.find() && n < 40) {
            String raw = m.group(1);
            String title = cleanHtml(raw);
            if (!validTitle(title)) continue;
            Matcher idm = Pattern.compile("support/announcement/detail/([A-Za-z0-9]+)", Pattern.CASE_INSENSITIVE).matcher(raw);
            String id = idm.find() ? idm.group(1) : "tg_" + Integer.toHexString(title.hashCode());
            add(out, id, title);
            n++;
        }
    }

    private static void add(Map<String, Announcement> out, String id, String title) {
        String key = id == null || id.isEmpty() ? Integer.toHexString(title.hashCode()) : id;
        if (!out.containsKey(key)) out.put(key, new Announcement(key, title));
    }

    private static Set<String> extractSymbols(String title, Map<String, ScannerService.BinanceTicker> bySymbol) {
        Set<String> out = new HashSet<>();
        String upper = title.toUpperCase(Locale.US);
        Matcher p = Pattern.compile("\\(([A-Z0-9]{2,15})\\)").matcher(upper);
        while (p.find()) if (bySymbol.containsKey(p.group(1))) out.add(p.group(1));
        Matcher pair = Pattern.compile("\\b([A-Z0-9]{2,15})USDT\\b").matcher(upper);
        while (pair.find()) if (bySymbol.containsKey(pair.group(1))) out.add(pair.group(1));
        for (String symbol : bySymbol.keySet()) {
            if (symbol.length() < 4 || out.contains(symbol)) continue;
            if (Pattern.compile("(?<![A-Z0-9])" + Pattern.quote(symbol) + "(?![A-Z0-9])").matcher(upper).find()) out.add(symbol);
        }
        return out;
    }

    private static Classification classify(String title) {
        String x = title.toLowerCase(Locale.US);
        if (has(x, "will delist", "delisting", "delist ")) return new Classification("Delisting / إزالة إدراج", 5.0, false, true);
        if (has(x, "removal of spot trading pairs", "remove spot trading pairs")) return new Classification("إزالة أزواج Spot", 4.0, false, true);
        if (has(x, "monitoring tag applied", "apply monitoring tag")) return new Classification("Monitoring Tag", 3.5, false, true);
        if (x.contains("suspend") && (x.contains("deposit") || x.contains("withdraw"))) return new Classification("تعليق إيداع/سحب", 2.5, false, true);

        if (x.contains("will list") && !x.contains("futures")) return new Classification("Spot Listing", 5.0, true, false);
        if (x.contains("new cryptocurrency listing")) return new Classification("New Listing", 5.0, true, false);
        if (has(x, "monitoring tag removed", "remove monitoring tag", "seed tag removed", "remove seed tag")) return new Classification("إزالة Risk Tag", 4.5, true, false);
        if (has(x, "launchpool", "megadrop", "hodler airdrop")) return new Classification("Launch/Airdrop Event", 4.1, true, false);
        if (has(x, "token swap", "token migration", "token merge", "contract swap")) return new Classification("Token Migration/Swap", 3.8, true, false);
        if (has(x, "mainnet", "network upgrade", "hard fork", "network fork")) return new Classification("Mainnet / Network Upgrade", 3.3, true, false);
        if (x.contains("airdrop")) return new Classification("Airdrop", 3.1, true, false);
        if (x.contains("burn")) return new Classification("Token Burn", 3.0, true, false);
        if (x.contains("trading competition")) return new Classification("Trading Campaign", 1.5, true, false);
        if (x.contains("futures will launch")) return new Classification("Futures Launch", 1.3, true, false);
        return new Classification("", 0, false, false);
    }

    private static boolean has(String x, String... needles) {
        for (String n : needles) if (x.contains(n)) return true;
        return false;
    }

    private static String formatSummary(ScanResult r, List<CatalystSignal> signals) {
        StringBuilder sb = new StringBuilder();
        sb.append("⚡ Catalyst Radar • Binance official public announcements\n");
        if (!r.sourceOk) return sb.append("المصدر الرسمي غير متاح مؤقتًا.").toString();
        if (r.baselineBuilt) sb.append("تم بناء baseline للإعلانات الحالية؛ التنبيهات تبدأ من الإعلانات الجديدة التالية.\n");
        if (signals.isEmpty()) return sb.append("لا يوجد محفز مرتبط بأزواج Spot الحالية ضمن آخر الإعلانات المفحوصة.").toString();
        int shown = 0;
        for (CatalystSignal s : signals) {
            if (s.impact < 1.5 || shown >= 5) continue;
            sb.append(shown + 1).append(". ").append(s.symbol).append("/USDT • ")
                    .append(s.negative ? "⚠️ " : "⚡ ").append(s.label)
                    .append(" • 24h ").append(pct(s.change24));
            if (s.whale != null) sb.append(" • 🐋 whale overlap");
            sb.append("\n   ").append(shortTitle(s.title)).append("\n");
            shown++;
        }
        if (shown == 0) sb.append("لا يوجد محفز عالي الصلة الآن.");
        return sb.toString();
    }

    private static void ensureChannel(Context context) {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        NotificationChannel c = new NotificationChannel(CHANNEL, "Catalyst & combined alerts", NotificationManager.IMPORTANCE_HIGH);
        c.enableVibration(true);
        c.setVibrationPattern(new long[]{0, 180, 120, 280});
        nm.createNotificationChannel(c);
    }

    private static void post(Context context, int id, String title, String body) { }

    private static String readText(String url) throws Exception {
        HttpsURLConnection c = (HttpsURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(10_000);
        c.setReadTimeout(15_000);
        c.setInstanceFollowRedirects(true);
        c.setRequestProperty("Accept", "text/html,application/xhtml+xml");
        c.setRequestProperty("Accept-Language", "en-US,en;q=0.9");
        c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 Chrome/140 Mobile Safari/537.36");
        int code = c.getResponseCode();
        InputStream s = code >= 200 && code < 400 ? c.getInputStream() : c.getErrorStream();
        String text = readStream(s);
        c.disconnect();
        if (code < 200 || code >= 400) throw new Exception("HTTP " + code);
        return text;
    }

    private static String readStream(InputStream s) throws Exception {
        if (s == null) return "";
        BufferedReader br = new BufferedReader(new InputStreamReader(s, StandardCharsets.UTF_8));
        StringBuilder out = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) out.append(line).append('\n');
        br.close();
        return out.toString();
    }

    private static String cleanHtml(String x) {
        if (x == null) return "";
        return x.replaceAll("(?is)<script.*?</script>", " ")
                .replaceAll("(?is)<style.*?</style>", " ")
                .replaceAll("(?is)<br\\s*/?>", " ")
                .replaceAll("(?is)<[^>]+>", " ")
                .replace("&amp;", "&").replace("&quot;", "\"")
                .replace("&#39;", "'").replace("&nbsp;", " ")
                .replace("&lt;", "<").replace("&gt;", ">")
                .replaceAll("\\s+", " ").trim();
    }

    private static String decodeJson(String x) {
        return x.replace("\\u0026", "&").replace("\\/", "/").replace("\\\"", "\"");
    }

    private static boolean validTitle(String x) {
        return x != null && x.length() >= 8 && x.length() <= 320 && !x.toLowerCase(Locale.US).contains("cookie");
    }

    private static String seenKey(String id, String symbol) {
        return "cat_seen_" + Integer.toHexString((id + "|" + symbol).hashCode());
    }

    private static void cleanupOldSeen(SharedPreferences prefs) {
        long now = System.currentTimeMillis();
        SharedPreferences.Editor e = null;
        for (Map.Entry<String, ?> x : prefs.getAll().entrySet()) {
            if (!x.getKey().startsWith("cat_seen_")) continue;
            if (!(x.getValue() instanceof Long)) continue;
            if (now - (Long) x.getValue() > SEEN_RETENTION_MS) {
                if (e == null) e = prefs.edit();
                e.remove(x.getKey());
            }
        }
        if (e != null) e.apply();
    }

    private static String shortTitle(String x) { return x.length() > 170 ? x.substring(0, 170) + "…" : x; }
    private static String pct(double x) { return String.format(Locale.US, "%+.2f%%", x); }
    private static String safe(String x) { return x == null ? "" : x; }
    private static String money(double x) {
        double a = Math.abs(x);
        if (a >= 1_000_000) return String.format(Locale.US, "$%.2fM", a / 1_000_000.0);
        if (a >= 1_000) return String.format(Locale.US, "$%.1fK", a / 1_000.0);
        return String.format(Locale.US, "$%.0f", a);
    }

    static class Announcement {
        final String id;
        final String title;
        Announcement(String id, String title) { this.id = id; this.title = title; }
    }

    static class Classification {
        final String label;
        final double impact;
        final boolean positive;
        final boolean negative;
        Classification(String label, double impact, boolean positive, boolean negative) {
            this.label = label; this.impact = impact; this.positive = positive; this.negative = negative;
        }
    }

    static class CatalystSignal {
        String symbol;
        String title;
        String id;
        String label;
        double impact;
        double change24;
        boolean positive;
        boolean negative;
        boolean early;
        ScannerService.WhaleSignal whale;
    }

    public static class ScanResult {
        public final Set<String> combinedSymbols = new HashSet<>();
        public final Set<String> freshPositiveSymbols = new HashSet<>();
        public final Set<String> freshNegativeSymbols = new HashSet<>();
        public List<CatalystSignal> relevant = new ArrayList<>();
        public boolean hot;
        public boolean sourceOk;
        public boolean baselineBuilt;
        public String summary = "";
    }
}
