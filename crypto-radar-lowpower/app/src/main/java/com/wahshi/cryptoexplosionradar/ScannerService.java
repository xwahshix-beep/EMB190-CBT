package com.wahshi.cryptoexplosionradar;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Build;
import android.os.IBinder;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
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

    private static final String SERVICE_CHANNEL = "whale_radar_service";
    private static final String ALERT_CHANNEL = "whale_accumulation_alerts";
    private static final int SERVICE_NOTIFICATION_ID = 3101;

    private static final long QUIET_INTERVAL_MS = 20 * 60_000L;
    private static final long WATCH_INTERVAL_MS = 10 * 60_000L;
    private static final long ERROR_RETRY_MS = 10 * 60_000L;
    private static final long ALERT_COOLDOWN_MS = 6 * 60 * 60_000L;

    private static final double MIN_BINANCE_24H_QUOTE_USDT = 1_500_000.0;
    private static final double MAX_EARLY_24H_CHANGE = 8.0;
    private static final double MAX_SCREENER_1H_CHANGE = 6.0;

    private static final String BINANCE_API = "https://api.binance.com";
    private static final String NANSEN_API = "https://api.nansen.ai";

    // Restrict discovery to chains also supported by Flow Intelligence so a token can
    // always be verified at the whale/cohort layer after Token Screener discovery.
    private static final String[][] CHAIN_GROUPS = new String[][]{
            {"ethereum", "solana", "base", "bnb", "arbitrum"},
            {"polygon", "optimism", "avalanche", "linea", "scroll"},
            {"sui", "ton", "tron", "sei", "sonic"},
            {"mantle", "monad", "plasma", "near", "starknet"}
    };

    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
    private volatile boolean running = false;
    private SharedPreferences prefs;

    @Override public void onCreate() {
        super.onCreate();
        prefs = getSharedPreferences("whale_radar", MODE_PRIVATE);
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
        if (!SecretStore.hasNansenKey(this)) {
            sendUi("Nansen API key مطلوب", "أدخل المفتاح داخل التطبيق ثم أعد تشغيل الرادار.");
            stopSelf();
            return;
        }
        running = true;
        startForeground(SERVICE_NOTIFICATION_ID, buildServiceNotification("Whale Radar يعمل • فحص On-chain هادئ"));
        sendUi("🐋 Whale Radar يعمل", "أول فحص On-chain خلال ثوانٍ…");
        scheduleNext(2_000L);
    }

    private synchronized void stopScanner() {
        running = false;
        sendUi("الرادار متوقف", "لن تتم مراقبة حركة الحيتان حتى تشغيله من جديد.");
        if (Build.VERSION.SDK_INT >= 24) stopForeground(STOP_FOREGROUND_REMOVE); else stopForeground(true);
        stopSelf();
    }

    private void scheduleNext(long delayMs) {
        if (!running) return;
        executor.schedule(this::runCycleSafe, delayMs, TimeUnit.MILLISECONDS);
    }

    private void runCycleSafe() {
        if (!running) return;
        long next = QUIET_INTERVAL_MS;
        try {
            String key = SecretStore.getNansenKey(this);
            if (key.isEmpty()) throw new Exception("Nansen API key غير موجود");

            Map<String, BinanceTicker> binance = loadBinanceSpotTickers();
            List<Seed> seeds = discoverSmartMoneySeeds(key, binance);
            List<WhaleSignal> signals = analyzeTopSeeds(key, seeds, binance, 6);

            boolean activeWatch = false;
            for (WhaleSignal s : signals) {
                if (s.score >= 4.0) {
                    activeWatch = true;
                    maybeAlert(s);
                }
            }
            next = activeWatch ? WATCH_INTERVAL_MS : QUIET_INTERVAL_MS;

            String status = activeWatch ? "🐋 Whale Watch • تجميع قيد المتابعة" : "Whale Radar • مراقبة هادئة";
            String details = formatSignals(seeds, signals, next);
            sendUi(status, details);
            updateServiceNotification(status + " • التالي ~" + (next / 60_000L) + "m");
        } catch (Exception e) {
            next = ERROR_RETRY_MS;
            String msg = safe(e.getMessage());
            sendUi("Whale Radar • خطأ مؤقت", "سيعيد المحاولة تلقائيًا.\n" + msg);
            updateServiceNotification("Whale Radar • إعادة محاولة لاحقًا");
        } finally {
            scheduleNext(next);
        }
    }

    private Map<String, BinanceTicker> loadBinanceSpotTickers() throws Exception {
        JSONArray rows = new JSONArray(readUrl(BINANCE_API + "/api/v3/ticker/24hr"));
        Map<String, BinanceTicker> out = new HashMap<>();
        for (int n = 0; n < rows.length(); n++) {
            JSONObject j = rows.getJSONObject(n);
            String symbol = j.optString("symbol", "");
            if (!eligibleBinanceSymbol(symbol)) continue;
            BinanceTicker t = new BinanceTicker();
            t.symbol = symbol;
            t.base = symbol.substring(0, symbol.length() - 4).toUpperCase(Locale.US);
            t.last = d(j, "lastPrice");
            t.quoteVolume = d(j, "quoteVolume");
            t.change24 = d(j, "priceChangePercent");
            if (t.quoteVolume < MIN_BINANCE_24H_QUOTE_USDT) continue;
            out.put(t.base, t);
        }
        return out;
    }

    private List<Seed> discoverSmartMoneySeeds(String apiKey, Map<String, BinanceTicker> binance) throws Exception {
        Map<String, Seed> bestBySymbol = new HashMap<>();
        for (String[] group : CHAIN_GROUPS) {
            JSONObject body = new JSONObject();
            JSONArray chains = new JSONArray();
            for (String c : group) chains.put(c);
            body.put("chains", chains);
            body.put("timeframe", "1h");
            body.put("pagination", new JSONObject().put("page", 1).put("per_page", 100));
            body.put("filters", new JSONObject().put("only_smart_money", true).put("hide_spam_tokens", true));
            // buy_volume is a documented sortable field; netflow is still used by our own gate below.
            body.put("order_by", new JSONArray().put(new JSONObject().put("field", "buy_volume").put("direction", "DESC")));

            JSONObject response = postNansen(apiKey, "/api/v1/token-screener", body);
            JSONArray data = response.optJSONArray("data");
            if (data == null) continue;

            for (int n = 0; n < data.length(); n++) {
                JSONObject j = data.optJSONObject(n);
                if (j == null) continue;
                String symbol = j.optString("token_symbol", "").toUpperCase(Locale.US).trim();
                String chain = j.optString("chain", "").trim();
                String address = j.optString("token_address", "").trim();
                if (symbol.isEmpty() || chain.isEmpty() || address.isEmpty()) continue;

                BinanceTicker bt = binance.get(symbol);
                if (bt == null || bt.change24 > MAX_EARLY_24H_CHANGE) continue;

                Seed s = new Seed();
                s.symbol = symbol;
                s.chain = chain;
                s.tokenAddress = address;
                s.netflow = d(j, "netflow");
                s.buyVolume = d(j, "buy_volume");
                s.sellVolume = d(j, "sell_volume");
                s.priceChange1h = d(j, "price_change");
                s.liquidity = d(j, "liquidity");
                s.marketCap = d(j, "market_cap_usd");
                s.volume = d(j, "volume");
                if (s.priceChange1h > MAX_SCREENER_1H_CHANGE) continue;

                boolean netPositive = s.netflow > 0;
                boolean buyDominant = s.buyVolume > 0 && s.buyVolume > s.sellVolume * 1.05;
                if (!netPositive && !buyDominant) continue;

                s.discoveryQuality = Math.max(0, s.netflow)
                        + Math.max(0, s.buyVolume - s.sellVolume)
                        + Math.min(250_000.0, s.liquidity * 0.02);
                Seed old = bestBySymbol.get(symbol);
                if (old == null || s.discoveryQuality > old.discoveryQuality) bestBySymbol.put(symbol, s);
            }
        }

        List<Seed> out = new ArrayList<>(bestBySymbol.values());
        out.sort((a, b) -> Double.compare(b.discoveryQuality, a.discoveryQuality));
        return out;
    }

    private List<WhaleSignal> analyzeTopSeeds(String apiKey, List<Seed> seeds,
                                               Map<String, BinanceTicker> binance, int max) {
        List<WhaleSignal> out = new ArrayList<>();
        int n = Math.min(max, seeds.size());
        for (int x = 0; x < n; x++) {
            Seed seed = seeds.get(x);
            BinanceTicker bt = binance.get(seed.symbol);
            if (bt == null) continue;
            try {
                FlowData h1 = loadFlowIntelligence(apiKey, seed, "1h");
                if (h1 == null) continue;
                if (h1.whaleNet <= 0 || h1.whaleCount < 2) continue;

                WhaleSignal s = scoreSignal(seed, bt, h1);
                if (s.score >= 4.0) {
                    FlowData d1 = null;
                    try { d1 = loadFlowIntelligence(apiKey, seed, "1d"); } catch (Exception ignored) {}
                    s.day = d1;
                    if (d1 != null) {
                        if (d1.whaleNet > 0 && d1.whaleCount >= 2) s.score += 0.75;
                        if (d1.smartNet > 0 && d1.smartCount >= 2) s.score += 0.50;
                        if (d1.exchangeNet < 0) s.score += 0.50;
                    }
                    s.strong = s.score >= 5.5 && d1 != null && d1.whaleNet > 0 && h1.exchangeNet <= 0;
                    out.add(s);
                }
            } catch (Exception ignored) {
                // A single unsupported/malformed token must not abort the whole scan cycle.
            }
        }
        out.sort((a, b) -> Double.compare(b.score, a.score));
        return out;
    }

    private FlowData loadFlowIntelligence(String apiKey, Seed seed, String timeframe) throws Exception {
        JSONObject body = new JSONObject()
                .put("chain", seed.chain)
                .put("token_address", seed.tokenAddress)
                .put("timeframe", timeframe);
        JSONObject response = postNansen(apiKey, "/api/v1/tgm/flow-intelligence", body);
        JSONArray data = response.optJSONArray("data");
        if (data == null || data.length() == 0) return null;
        JSONObject j = data.optJSONObject(0);
        if (j == null) return null;

        FlowData f = new FlowData();
        f.whaleNet = d(j, "whale_net_flow_usd");
        f.whaleCount = i(j, "whale_wallet_count");
        f.smartNet = d(j, "smart_trader_net_flow_usd");
        f.smartCount = i(j, "smart_trader_wallet_count");
        f.topPnlNet = d(j, "top_pnl_net_flow_usd");
        f.topPnlCount = i(j, "top_pnl_wallet_count");
        f.exchangeNet = d(j, "exchange_net_flow_usd");
        f.exchangeCount = i(j, "exchange_wallet_count");
        f.freshNet = d(j, "fresh_wallets_net_flow_usd");
        f.freshCount = i(j, "fresh_wallets_wallet_count");
        return f;
    }

    private WhaleSignal scoreSignal(Seed seed, BinanceTicker bt, FlowData f) {
        WhaleSignal s = new WhaleSignal();
        s.seed = seed;
        s.binance = bt;
        s.hour = f;
        double score = 0;

        if (f.whaleNet > 0 && f.whaleCount >= 2) score += 2.0;
        if (f.whaleCount >= 4) score += 0.5;
        if (f.smartNet > 0 && f.smartCount >= 2) score += 1.0;
        if (f.topPnlNet > 0 && f.topPnlCount >= 1) score += 0.75;
        if (f.exchangeNet < 0) score += 1.25;
        if (f.freshNet > 0 && f.freshCount >= 5) score += 0.50;
        if (seed.netflow > 0 && seed.buyVolume > seed.sellVolume) score += 0.50;
        if (bt.change24 <= 5.0 && seed.priceChange1h <= 3.0) score += 0.50;

        double accumulation = Math.max(0, f.whaleNet) + Math.max(0, f.smartNet) + Math.max(0, f.topPnlNet);
        if (f.exchangeNet > Math.max(50_000.0, accumulation)) score -= 2.0;
        if (bt.change24 > MAX_EARLY_24H_CHANGE) score -= 3.0;

        s.score = score;
        return s;
    }

    private void maybeAlert(WhaleSignal s) {
        String tier = s.strong ? "strong" : "watch";
        String key = "last_" + s.seed.symbol;
        long last = prefs.getLong(key, 0L);
        String lastTier = prefs.getString(key + "_tier", "");
        long now = System.currentTimeMillis();

        boolean upgrade = s.strong && !"strong".equals(lastTier);
        if (!upgrade && now - last < ALERT_COOLDOWN_MS) return;
        prefs.edit().putLong(key, now).putString(key + "_tier", tier).apply();

        String pair = s.seed.symbol + "/USDT";
        String title = s.strong
                ? "🐋🔥 تجميع حيتان قوي — " + pair
                : "🐋 تجميع حيتان مبكر — " + pair;
        String body = "Whales " + money(s.hour.whaleNet) + " (" + s.hour.whaleCount + ")"
                + " • Smart " + money(s.hour.smartNet)
                + " • Exchange " + money(s.hour.exchangeNet)
                + " • 24h " + String.format(Locale.US, "%+.2f%%", s.binance.change24);
        postWhaleNotification(s.seed.symbol, title, body);
    }

    private String formatSignals(List<Seed> seeds, List<WhaleSignal> signals, long nextMs) {
        StringBuilder sb = new StringBuilder();
        sb.append("Binance Spot + On-chain Whale Intelligence\n");
        sb.append("تم اكتشاف ").append(seeds.size()).append(" مرشح Smart-Money أولي.\n\n");

        if (signals.isEmpty()) {
            sb.append("لا يوجد تجميع حيتان مؤكد الآن.\n");
            sb.append("لن يصدر التطبيق تنبيهًا لمجرد ارتفاع الحجم أو السعر.\n");
        } else {
            int n = Math.min(5, signals.size());
            for (int x = 0; x < n; x++) {
                WhaleSignal s = signals.get(x);
                sb.append(s.strong ? "🐋🔥 " : "🐋 ")
                        .append(s.seed.symbol).append("/USDT")
                        .append(" • ").append(s.seed.chain).append("\n")
                        .append("Whales 1h: ").append(money(s.hour.whaleNet))
                        .append(" • wallets ").append(s.hour.whaleCount).append("\n")
                        .append("Smart Traders: ").append(money(s.hour.smartNet))
                        .append(" • Top-PnL: ").append(money(s.hour.topPnlNet)).append("\n")
                        .append("Exchange flow: ").append(money(s.hour.exchangeNet))
                        .append(" • Fresh: ").append(money(s.hour.freshNet)).append("\n")
                        .append("Binance 24h: ").append(String.format(Locale.US, "%+.2f%%", s.binance.change24))
                        .append(" • Score ").append(String.format(Locale.US, "%.2f", s.score)).append("\n\n");
            }
        }
        sb.append("الفحص التالي تقريبًا خلال ").append(Math.max(1, nextMs / 60_000L)).append(" دقيقة.\n")
                .append("Powered by Nansen API • لا توجد أوامر تداول.");
        return sb.toString();
    }

    private JSONObject postNansen(String apiKey, String path, JSONObject body) throws Exception {
        HttpsURLConnection c = (HttpsURLConnection) new URL(NANSEN_API + path).openConnection();
        c.setConnectTimeout(10_000);
        c.setReadTimeout(20_000);
        c.setRequestMethod("POST");
        c.setDoOutput(true);
        c.setRequestProperty("Content-Type", "application/json");
        c.setRequestProperty("Accept", "application/json");
        c.setRequestProperty("apiKey", apiKey);
        c.setRequestProperty("User-Agent", "WhaleAccumulationRadar/2.0");
        byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
        try (OutputStream os = c.getOutputStream()) { os.write(bytes); }
        int code = c.getResponseCode();
        String text = readConnection(c, code);
        c.disconnect();
        if (code < 200 || code >= 300) {
            String detail = text.length() > 220 ? text.substring(0, 220) : text;
            throw new Exception("Nansen HTTP " + code + (detail.isEmpty() ? "" : " • " + detail));
        }
        return new JSONObject(text);
    }

    private String readUrl(String url) throws Exception {
        HttpsURLConnection c = (HttpsURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(8_000);
        c.setReadTimeout(12_000);
        c.setRequestProperty("User-Agent", "WhaleAccumulationRadar/2.0");
        c.setRequestProperty("Accept", "application/json");
        int code = c.getResponseCode();
        String text = readConnection(c, code);
        c.disconnect();
        if (code < 200 || code >= 300) throw new Exception("Binance HTTP " + code);
        return text;
    }

    private String readConnection(HttpsURLConnection c, int code) throws Exception {
        InputStream stream = code >= 200 && code < 300 ? c.getInputStream() : c.getErrorStream();
        if (stream == null) return "";
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
        }
        return sb.toString();
    }

    private boolean eligibleBinanceSymbol(String s) {
        if (s == null || !s.endsWith("USDT") || s.length() <= 4) return false;
        String base = s.substring(0, s.length() - 4);
        String[] skip = {"USDC", "FDUSD", "TUSD", "USDP", "DAI", "BUSD"};
        for (String x : skip) if (base.equals(x)) return false;
        return !(base.endsWith("UP") || base.endsWith("DOWN") || base.endsWith("BULL") || base.endsWith("BEAR"));
    }

    private void createChannels() {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        NotificationChannel service = new NotificationChannel(SERVICE_CHANNEL, "Whale Radar background", NotificationManager.IMPORTANCE_LOW);
        service.setDescription("Low-power on-chain whale monitoring");
        service.setSound(null, null);
        service.enableVibration(false);
        nm.createNotificationChannel(service);

        NotificationChannel alerts = new NotificationChannel(ALERT_CHANNEL, "Whale accumulation alerts", NotificationManager.IMPORTANCE_HIGH);
        alerts.setDescription("Early whale and smart-money accumulation signals");
        alerts.enableVibration(true);
        alerts.setVibrationPattern(new long[]{0, 250, 150, 350});
        nm.createNotificationChannel(alerts);
    }

    private Notification buildServiceNotification(String text) {
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 1, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, SERVICE_CHANNEL) : new Notification.Builder(this);
        return b.setContentTitle("Whale Accumulation Radar")
                .setContentText(text)
                .setSmallIcon(R.drawable.app_icon)
                .setContentIntent(pi)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .build();
    }

    private void updateServiceNotification(String text) {
        ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).notify(SERVICE_NOTIFICATION_ID, buildServiceNotification(text));
    }

    private void postWhaleNotification(String symbol, String title, String body) {
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, symbol.hashCode(), open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, ALERT_CHANNEL) : new Notification.Builder(this);
        Notification n = b.setContentTitle(title)
                .setContentText(body)
                .setStyle(new Notification.BigTextStyle().bigText(body))
                .setSmallIcon(R.drawable.app_icon)
                .setContentIntent(pi)
                .setAutoCancel(true)
                .setColor(Color.rgb(245, 183, 43))
                .build();
        ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).notify(5000 + Math.abs(symbol.hashCode() % 4000), n);
    }

    private void sendUi(String status, String details) {
        Intent i = new Intent(ACTION_UI).setPackage(getPackageName());
        i.putExtra("status", status);
        i.putExtra("details", details);
        sendBroadcast(i);
    }

    private static double d(JSONObject j, String k) {
        try {
            Object v = j.opt(k);
            if (v instanceof Number) return ((Number) v).doubleValue();
            return Double.parseDouble(j.optString(k, "0"));
        } catch (Exception e) { return 0; }
    }

    private static int i(JSONObject j, String k) {
        try {
            Object v = j.opt(k);
            if (v instanceof Number) return ((Number) v).intValue();
            return Integer.parseInt(j.optString(k, "0"));
        } catch (Exception e) { return 0; }
    }

    private static String money(double x) {
        String sign = x > 0 ? "+" : "";
        double a = Math.abs(x);
        if (a >= 1_000_000) return sign + String.format(Locale.US, "%.2fM$", x / 1_000_000.0);
        if (a >= 1_000) return sign + String.format(Locale.US, "%.1fK$", x / 1_000.0);
        return sign + String.format(Locale.US, "%.0f$", x);
    }

    private static String safe(String x) { return x == null ? "" : x; }

    @Override public void onDestroy() {
        running = false;
        executor.shutdownNow();
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    static class BinanceTicker {
        String symbol, base;
        double last, quoteVolume, change24;
    }

    static class Seed {
        String symbol, chain, tokenAddress;
        double netflow, buyVolume, sellVolume, priceChange1h, liquidity, marketCap, volume, discoveryQuality;
    }

    static class FlowData {
        double whaleNet, smartNet, topPnlNet, exchangeNet, freshNet;
        int whaleCount, smartCount, topPnlCount, exchangeCount, freshCount;
    }

    static class WhaleSignal {
        Seed seed;
        BinanceTicker binance;
        FlowData hour, day;
        double score;
        boolean strong;
    }
}
