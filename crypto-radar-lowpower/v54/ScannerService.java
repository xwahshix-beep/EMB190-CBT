package com.wahshi.cryptoexplosionradar;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Build;
import android.os.PowerManager;
import android.os.SystemClock;
import android.os.IBinder;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.HttpsURLConnection;

public class ScannerService extends Service {
    public static final String ACTION_START = "com.wahshi.cryptoexplosionradar.START";
    public static final String ACTION_STOP = "com.wahshi.cryptoexplosionradar.STOP";
    public static final String ACTION_TICK = "com.wahshi.cryptoexplosionradar.TICK";
    public static final String ACTION_UI = "com.wahshi.cryptoexplosionradar.UI";

    private static final String SERVICE_CHANNEL = "free_whale_radar_service";
    private static final String ALERT_CHANNEL = "free_whale_accumulation_alerts";
    private static final int SERVICE_NOTIFICATION_ID = 4101;

    private static final long NORMAL_INTERVAL_MS = 20 * 60_000L;
    private static final long WATCH_INTERVAL_MS = 10 * 60_000L;
    private static final long ERROR_RETRY_MS = 12 * 60_000L;
    private static final long ALERT_COOLDOWN_MS = 4 * 60 * 60_000L;

    private static final int ROTATING_BATCH = 24;
    private static final int QUIET_PRIORITY = 12;
    private static final double MIN_BINANCE_24H_QUOTE_USDT = 1_500_000.0;
    private static final double MAX_EARLY_24H_CHANGE = 8.0;
    private static final double MIN_DEX_LIQUIDITY_USD = 100_000.0;
    private static final double BASE_WHALE_USD = 25_000.0;
    private static final String BINANCE_API = "https://api.binance.com";
    private static final String DEX_API = "https://api.dexscreener.com";
    private static final String SOLANA_RPC = "https://api.mainnet.solana.com";
    private static final String TRANSFER_TOPIC = "0xddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef";

    private static final Map<String, String> EVM_RPC = new HashMap<>();
    private static final Map<String, Integer> EVM_LOOKBACK_BLOCKS = new HashMap<>();
    static {
        EVM_RPC.put("ethereum", "https://cloudflare-eth.com/v1/mainnet");
        EVM_RPC.put("base", "https://mainnet.base.org");
        EVM_RPC.put("arbitrum", "https://arb1.arbitrum.io/rpc");
        EVM_RPC.put("avalanche", "https://api.avax.network/ext/bc/C/rpc");
        EVM_RPC.put("bsc", "https://bsc-rpc.publicnode.com");

        // Roughly 30–90 minutes depending on chain cadence. The range is address-filtered.
        EVM_LOOKBACK_BLOCKS.put("ethereum", 360);
        EVM_LOOKBACK_BLOCKS.put("base", 2400);
        EVM_LOOKBACK_BLOCKS.put("arbitrum", 8000);
        EVM_LOOKBACK_BLOCKS.put("avalanche", 2400);
        EVM_LOOKBACK_BLOCKS.put("bsc", 8000);
    }

    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
    private volatile boolean running = false;
    private SharedPreferences prefs;
    private AlarmManager alarmManager;
    private PendingIntent tickIntent;
    private PowerManager.WakeLock cycleWakeLock;
    private volatile boolean cycleInFlight = false;
    private volatile long lastWhaleScanAt = 0L;
    private volatile List<WhaleSignal> cachedWhaleSignals = new ArrayList<>();
    private volatile int lastWhaleScanned = 0;
    private volatile int lastWhaleMapped = 0;
    private volatile int lastWhaleOnChain = 0;

    private java.util.concurrent.ScheduledExecutorService fastWorker;
    @Override public void onCreate() {
        super.onCreate();
        prefs = getSharedPreferences("free_whale_radar_v3", MODE_PRIVATE);
        createChannels();
        alarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);
        Intent tick = new Intent(this, ScannerService.class).setAction(ACTION_TICK);
        tickIntent = PendingIntent.getService(this, 4202, tick, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        if (pm != null) {
            cycleWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "WhaleCatalystRadar:scan");
            cycleWakeLock.setReferenceCounted(false);
        }
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();
        if (ACTION_STOP.equals(action)) {
            stopScanner();
            return START_NOT_STICKY;
        }
        if (ACTION_TICK.equals(action)) {
            if (!prefs.getBoolean("radar_enabled", false)) {
                stopSelf();
                return START_NOT_STICKY;
            }
            if (!running) startScanner();
            triggerCycle();
            return START_STICKY;
        }
        if (intent == null && !prefs.getBoolean("radar_enabled", false)) {
            stopSelf();
            return START_NOT_STICKY;
        }
        startScanner();
        return START_STICKY;
    }

    private synchronized void startScanner() {
        if (running) return;
        running = true;
        prefs.edit().putBoolean("radar_enabled", true).putBoolean("service_alive", true).apply();
        startForeground(SERVICE_NOTIFICATION_ID, buildServiceNotification("Hybrid Explosion Radar V4.2.3 يعمل • Background wake"));
        sendUi("🐋⚡ Hybrid Explosion Radar V4.2.3", "أول فحص مجاني On-chain خلال ثوانٍ…");
        if(fastWorker==null || fastWorker.isShutdown()) {
            fastWorker=java.util.concurrent.Executors.newSingleThreadScheduledExecutor();
            fastWorker.scheduleWithFixedDelay(() -> {
                if(running) { FastWatch.scan(this,prefs); }
            },0,15,java.util.concurrent.TimeUnit.SECONDS);
        }
        scheduleNext(2_000L);
    }

    private synchronized void stopScanner() {
        running = false;
        scanHandler.removeCallbacks(scheduledScan);
        if(enrichmentWorker!=null)enrichmentWorker.shutdownNow();
        if(fastWorker!=null)fastWorker.shutdownNow();
        prefs.edit().putBoolean("radar_enabled", false).putBoolean("service_alive", false).apply();
        if (alarmManager != null && tickIntent != null) alarmManager.cancel(tickIntent);
        if (cycleWakeLock != null && cycleWakeLock.isHeld()) cycleWakeLock.release();
        sendUi("الرادار متوقف", "تم إيقاف Hybrid Explosion Radar V4.2.3 بواسطة المستخدم.");
        if (Build.VERSION.SDK_INT >= 24) stopForeground(STOP_FOREGROUND_REMOVE); else stopForeground(true);
        stopSelf();
    }

    private synchronized void scheduleNext(long delayMs) {
        if (!running || alarmManager == null || tickIntent == null) return;
        long when = SystemClock.elapsedRealtime() + Math.max(1_000L, delayMs);
        alarmManager.cancel(tickIntent);
        scanHandler.removeCallbacks(scheduledScan);
        scanHandler.postDelayed(scheduledScan,Math.max(1000,delayMs));
        if (Build.VERSION.SDK_INT >= 23) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, when, tickIntent);
        } else {
            alarmManager.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, when, tickIntent);
        }
        prefs.edit().putLong("next_wake_elapsed", when).apply();
    }

    private synchronized void triggerCycle() {
        if (!running || cycleInFlight) return;
        cycleInFlight = true;
        executor.execute(() -> {
            try {
                runCycleSafe();
            } finally {
                cycleInFlight = false;
            }
        });
    }

    private final android.os.Handler scanHandler=new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable scheduledScan=() -> triggerCycle();
    private java.util.concurrent.ExecutorService enrichmentWorker;
    private java.util.concurrent.Future<?> enrichmentTask;
    private volatile long enrichmentAt;
    private volatile CatalystEngine.ScanResult cachedCatalyst;
    private void startEnrichment() {
        long enrichmentNow=System.currentTimeMillis();
        if(enrichmentNow-enrichmentAt<600000 || (enrichmentTask!=null&&!enrichmentTask.isDone()))return;
        if(enrichmentWorker==null||enrichmentWorker.isShutdown())enrichmentWorker=java.util.concurrent.Executors.newSingleThreadExecutor();
        enrichmentAt=enrichmentNow;
        enrichmentTask=enrichmentWorker.submit(() -> {
            try {
            long now = System.currentTimeMillis();
            List<BinanceTicker> all = loadBinanceSpotTickers();

            boolean existingWhaleWatch = false;
            for (WhaleSignal w : cachedWhaleSignals) {
                if (w.score >= 4.25) { existingWhaleWatch = true; break; }
            }
            long whaleCadence = existingWhaleWatch ? WATCH_INTERVAL_MS : NORMAL_INTERVAL_MS;
            boolean whaleDue = lastWhaleScanAt == 0L
                    || now - lastWhaleScanAt >= whaleCadence;

            List<WhaleSignal> signals = cachedWhaleSignals;
            if (whaleDue) {
                List<BinanceTicker> batch = selectBatch(all);
                batch=new ArrayList<>(batch.subList(0,Math.min(3,batch.size())));
                List<WhaleSignal> fresh = new ArrayList<>();
                int mapped = 0;
                int directOnChain = 0;

                for (BinanceTicker bt : batch) {
                    if (!running) break;
                    try {
                        DexToken token = resolveDexToken(bt);
                        if (token == null) continue;
                        mapped++;

                        WhaleSignal ws = null;
                        if ("solana".equals(token.chain)) {
                            directOnChain++;
                            ws = analyzeSolanaTopHolders(bt, token);
                        } else if (EVM_RPC.containsKey(token.chain)) {
                            directOnChain++;
                            ws = analyzeEvmTransfers(bt, token);
                        }
                        if (ws != null) fresh.add(ws);
                    } catch (Exception ignored) {
                        // Public on-chain providers are best-effort per token.
                    }
                }

                fresh.sort((a, b) -> Double.compare(b.score, a.score));
                cachedWhaleSignals = fresh;
                signals = fresh;
                lastWhaleScanAt = now;
                lastWhaleScanned = batch.size();
                lastWhaleMapped = mapped;
                lastWhaleOnChain = directOnChain;
            }

            boolean whaleWatch = false;
            for (WhaleSignal w : signals) {
                if (w.score >= 4.25) { whaleWatch = true; break; }
            }
            whaleCadence = whaleWatch ? WATCH_INTERVAL_MS : NORMAL_INTERVAL_MS;

            cachedCatalyst = CatalystEngine.scan(this, all, signals, prefs);
            java.util.List<String> earlySymbols=new java.util.ArrayList<>();
            for(BinanceTicker t:all)earlySymbols.add(t.symbol);
            HybridEngine.refreshEarly(earlySymbols,prefs);

            }catch(Exception ignored){/* Optional enrichment cannot block discovery. */}
        });
    }

    private void runCycleSafe() {
        if(!running)return;
        long started=System.currentTimeMillis();
        try {
            if(cycleWakeLock!=null&&!cycleWakeLock.isHeld())cycleWakeLock.acquire(90000);
            startEnrichment();
            List<WhaleSignal> whales=System.currentTimeMillis()-lastWhaleScanAt<900000?cachedWhaleSignals:Collections.emptyList();
            HybridEngine.ScanResult result=HybridEngine.scan(this,whales,cachedCatalyst,prefs);
            if(!running)return;
            long finished=System.currentTimeMillis();
            prefs.edit().putLong("last_scan_at",finished).putLong("scan_duration_ms",finished-started).apply();
            sendUi(result.status,result.summary+"\nمدة الفحص: "+((finished-started)/1000)+" ثانية");
            updateServiceNotification("رصد الدقيقة يعمل • فرص الشراء دون فتح التطبيق");
        } catch(Exception ex) {
            if(running)sendUi("تعذر تحديث السوق","انتظار — "+safe(ex.getMessage()));
        } finally {
            if(cycleWakeLock!=null&&cycleWakeLock.isHeld())cycleWakeLock.release();
            long elapsed=System.currentTimeMillis()-started;
            scheduleNext(Math.max(MarketHttp.retryDelay(),Math.max(5000,30000-elapsed)));
        }
    }

    private List<BinanceTicker> loadBinanceSpotTickers() throws Exception {
        JSONArray rows = new JSONArray(readUrl(BINANCE_API + "/api/v3/ticker/24hr"));
        List<BinanceTicker> out = new ArrayList<>();
        for (int n = 0; n < rows.length(); n++) {
            JSONObject j = rows.optJSONObject(n);
            if (j == null) continue;
            String symbol = j.optString("symbol", "");
            if (!eligibleBinanceSymbol(symbol)) continue;
            BinanceTicker t = new BinanceTicker();
            t.symbol = symbol;
            t.base = symbol.substring(0, symbol.length() - 4).toUpperCase(Locale.US);
            t.last = d(j, "lastPrice");
            t.quoteVolume = d(j, "quoteVolume");
            t.change24 = d(j, "priceChangePercent");
            if (t.last <= 0 || t.quoteVolume < MIN_BINANCE_24H_QUOTE_USDT) continue;
            if (Math.abs(t.change24) > MAX_EARLY_24H_CHANGE) continue;
            out.add(t);
        }
        return out;
    }

    private List<BinanceTicker> selectBatch(List<BinanceTicker> all) {
        if (all.isEmpty()) return all;
        LinkedHashMap<String, BinanceTicker> chosen = new LinkedHashMap<>();

        List<BinanceTicker> quiet = new ArrayList<>(all);
        quiet.sort(Comparator.comparingDouble(a -> Math.abs(a.change24)));
        for (int i = 0; i < Math.min(QUIET_PRIORITY, quiet.size()); i++) {
            chosen.put(quiet.get(i).symbol, quiet.get(i));
        }

        List<BinanceTicker> alphabetical = new ArrayList<>(all);
        alphabetical.sort(Comparator.comparing(a -> a.symbol));
        int cursor = prefs.getInt("rotation_cursor", 0);
        if (cursor >= alphabetical.size()) cursor = 0;
        int take = Math.min(ROTATING_BATCH, alphabetical.size());
        for (int i = 0; i < take; i++) {
            BinanceTicker t = alphabetical.get((cursor + i) % alphabetical.size());
            chosen.put(t.symbol, t);
        }
        prefs.edit().putInt("rotation_cursor", (cursor + take) % alphabetical.size()).apply();
        return new ArrayList<>(chosen.values());
    }

    private DexToken resolveDexToken(BinanceTicker bt) throws Exception {
        String q = URLEncoder.encode(bt.base + "/USDT", StandardCharsets.UTF_8.name());
        JSONObject root = new JSONObject(readUrl(DEX_API + "/latest/dex/search?q=" + q));
        JSONArray pairs = root.optJSONArray("pairs");
        if (pairs == null) return null;

        DexToken best = null;
        for (int i = 0; i < pairs.length(); i++) {
            JSONObject p = pairs.optJSONObject(i);
            if (p == null) continue;
            String chain = p.optString("chainId", "").toLowerCase(Locale.US);
            if (!("solana".equals(chain) || EVM_RPC.containsKey(chain))) continue;

            JSONObject base = p.optJSONObject("baseToken");
            if (base == null || !bt.base.equalsIgnoreCase(base.optString("symbol", ""))) continue;
            String address = base.optString("address", "").trim();
            if (address.isEmpty()) continue;

            double price = parseDouble(p.optString("priceUsd", "0"));
            if (price <= 0) continue;
            double diff = Math.abs(price / bt.last - 1.0) * 100.0;
            if (diff > 8.0) continue; // protects against same-symbol clones/wrong contracts.

            JSONObject liq = p.optJSONObject("liquidity");
            double liquidity = liq == null ? 0 : liq.optDouble("usd", 0);
            if (liquidity < MIN_DEX_LIQUIDITY_USD) continue;

            DexToken x = new DexToken();
            x.symbol = bt.base;
            x.chain = chain;
            x.address = address;
            x.pairAddress = p.optString("pairAddress", "");
            x.priceUsd = price;
            x.liquidityUsd = liquidity;
            x.priceChange1h = nestedNumber(p, "priceChange", "h1");
            x.volume1h = nestedNumber(p, "volume", "h1");
            JSONObject txns = p.optJSONObject("txns");
            JSONObject h1 = txns == null ? null : txns.optJSONObject("h1");
            if (h1 != null) {
                x.buys1h = h1.optInt("buys", 0);
                x.sells1h = h1.optInt("sells", 0);
            }
            if (best == null || x.liquidityUsd > best.liquidityUsd) best = x;
        }
        return best;
    }

    private WhaleSignal analyzeEvmTransfers(BinanceTicker bt, DexToken token) throws Exception {
        String rpc = EVM_RPC.get(token.chain);
        if (rpc == null) return null;
        int decimals = erc20Decimals(rpc, token.address);
        if (decimals < 0 || decimals > 36) return null;

        long latest = hexLong((String) rpcCall(rpc, "eth_blockNumber", new JSONArray()));
        int lookback = EVM_LOOKBACK_BLOCKS.get(token.chain);
        long from = Math.max(0, latest - lookback);
        JSONArray logs = getTransferLogs(rpc, token.address, from, latest, 0);
        if (logs == null || logs.length() == 0 || logs.length() > 6000) return null;

        Map<String, WalletFlow> flows = new HashMap<>();
        String pair = token.pairAddress == null ? "" : token.pairAddress.toLowerCase(Locale.US);
        String contract = token.address.toLowerCase(Locale.US);
        BigDecimal divisor = BigDecimal.TEN.pow(decimals);

        for (int i = 0; i < logs.length(); i++) {
            JSONObject log = logs.optJSONObject(i);
            if (log == null) continue;
            JSONArray topics = log.optJSONArray("topics");
            if (topics == null || topics.length() < 3) continue;
            String fromAddr = topicAddress(topics.optString(1, ""));
            String toAddr = topicAddress(topics.optString(2, ""));
            String data = log.optString("data", "0x0");
            if (fromAddr.isEmpty() || toAddr.isEmpty()) continue;
            if (isZero(fromAddr) || isZero(toAddr)) continue;

            double usd;
            try {
                BigInteger raw = hexBig(data);
                double tokens = new BigDecimal(raw).divide(divisor).doubleValue();
                usd = tokens * token.priceUsd;
            } catch (Exception e) {
                continue;
            }
            if (!Double.isFinite(usd) || usd < 250.0) continue;

            WalletFlow in = flows.computeIfAbsent(toAddr, k -> new WalletFlow());
            in.inUsd += usd;
            in.inCount++;
            in.counterparties.add(fromAddr);

            WalletFlow out = flows.computeIfAbsent(fromAddr, k -> new WalletFlow());
            out.outUsd += usd;
            out.outCount++;
            out.counterparties.add(toAddr);
        }

        double threshold = dynamicWhaleThreshold(token.liquidityUsd);
        List<Map.Entry<String, WalletFlow>> candidates = new ArrayList<>(flows.entrySet());
        candidates.sort((a, b) -> Double.compare(b.getValue().net(), a.getValue().net()));

        int whales = 0;
        double combined = 0;
        double largest = 0;
        int checked = 0;
        for (Map.Entry<String, WalletFlow> e : candidates) {
            if (checked >= 12) break;
            String addr = e.getKey().toLowerCase(Locale.US);
            WalletFlow f = e.getValue();
            if (f.net() < threshold) break;
            if (addr.equals(pair) || addr.equals(contract)) continue;
            if (f.inCount > 25 && f.counterparties.size() > 15) continue; // exchange/hub-like token flow pattern.
            checked++;
            if (!isEoa(rpc, addr)) continue; // exclude routers, bridges, multisig/contracts conservatively.
            if (f.outUsd > f.inUsd * 0.45) continue;
            whales++;
            combined += f.net();
            largest = Math.max(largest, f.net());
        }

        double required = Math.max(75_000.0, threshold * 2.4);
        if (whales < 2 || combined < required) return null;

        WhaleSignal s = new WhaleSignal();
        s.symbol = bt.base;
        s.chain = token.chain;
        s.wallets = whales;
        s.netUsd = combined;
        s.largestUsd = largest;
        s.change24 = bt.change24;
        s.source = "ERC-20 transfer netflows";
        s.score = scoreCommon(bt, token, whales, combined, threshold);
        s.strong = s.score >= 4.75 && whales >= 3;
        return s;
    }

    private WhaleSignal analyzeSolanaTopHolders(BinanceTicker bt, DexToken token) throws Exception {
        JSONObject root = solRpc("getTokenLargestAccounts", new JSONArray()
                .put(token.address)
                .put(new JSONObject().put("commitment", "confirmed")));
        JSONObject result = root.optJSONObject("result");
        if (result == null) return null;
        JSONArray values = result.optJSONArray("value");
        if (values == null || values.length() == 0) return null;

        JSONObject supplyRoot = solRpc("getTokenSupply", new JSONArray()
                .put(token.address)
                .put(new JSONObject().put("commitment", "confirmed")));
        JSONObject supplyResult = supplyRoot.optJSONObject("result");
        JSONObject supplyValue = supplyResult == null ? null : supplyResult.optJSONObject("value");
        double totalSupply = supplyValue == null ? 0 : parseDouble(supplyValue.optString("uiAmountString", "0"));
        if (totalSupply <= 0) return null;

        JSONObject current = new JSONObject();
        Map<String, Double> currentAmount = new LinkedHashMap<>();
        for (int i = 0; i < values.length(); i++) {
            JSONObject a = values.optJSONObject(i);
            if (a == null) continue;
            String account = a.optString("address", "");
            double amount = parseDouble(a.optString("uiAmountString", "0"));
            if (account.isEmpty() || amount <= 0) continue;
            current.put(account, amount);
            currentAmount.put(account, amount);
        }

        String key = "sol_snapshot_" + token.address;
        String oldText = prefs.getString(key, "");
        prefs.edit().putString(key, current.toString()).apply();
        if (oldText.isEmpty()) return null; // first observation establishes the baseline.

        JSONObject old;
        try { old = new JSONObject(oldText); } catch (Exception e) { return null; }
        double threshold = dynamicWhaleThreshold(token.liquidityUsd);
        List<String> changedAccounts = new ArrayList<>();
        Map<String, Double> deltaUsdByAccount = new HashMap<>();

        for (Map.Entry<String, Double> e : currentAmount.entrySet()) {
            String account = e.getKey();
            if (!old.has(account)) continue; // do not treat a newly-entered top-20 account as zero baseline.
            double nowAmount = e.getValue();
            double prevAmount = old.optDouble(account, nowAmount);
            double share = nowAmount / totalSupply;
            if (share > 0.12) continue; // treasury/exchange/vesting-like concentration guard.
            double deltaUsd = (nowAmount - prevAmount) * token.priceUsd;
            if (deltaUsd >= threshold) {
                changedAccounts.add(account);
                deltaUsdByAccount.put(account, deltaUsd);
            }
        }
        if (changedAccounts.size() < 2) return null;

        Map<String, Double> ownerNet = resolveSolanaOwners(changedAccounts, deltaUsdByAccount);
        List<Double> ownerDeltas = new ArrayList<>(ownerNet.values());
        ownerDeltas.sort(Collections.reverseOrder());
        int whales = 0;
        double combined = 0;
        double largest = 0;
        for (double v : ownerDeltas) {
            if (v < threshold) continue;
            whales++;
            combined += v;
            largest = Math.max(largest, v);
        }

        double required = Math.max(75_000.0, threshold * 2.4);
        if (whales < 2 || combined < required) return null;

        WhaleSignal s = new WhaleSignal();
        s.symbol = bt.base;
        s.chain = "solana";
        s.wallets = whales;
        s.netUsd = combined;
        s.largestUsd = largest;
        s.change24 = bt.change24;
        s.source = "top-holder balance increases";
        s.score = scoreCommon(bt, token, whales, combined, threshold);
        s.strong = s.score >= 4.75 && whales >= 3;
        return s;
    }

    private Map<String, Double> resolveSolanaOwners(List<String> accounts, Map<String, Double> deltaByAccount) throws Exception {
        Map<String, Double> out = new HashMap<>();
        JSONArray addresses = new JSONArray();
        for (String a : accounts) addresses.put(a);
        JSONArray params = new JSONArray().put(addresses)
                .put(new JSONObject().put("encoding", "jsonParsed").put("commitment", "confirmed"));
        JSONObject root = solRpc("getMultipleAccounts", params);
        JSONObject result = root.optJSONObject("result");
        JSONArray values = result == null ? null : result.optJSONArray("value");
        if (values == null) return out;

        for (int i = 0; i < values.length() && i < accounts.size(); i++) {
            JSONObject accountObj = values.optJSONObject(i);
            if (accountObj == null) continue;
            JSONObject data = accountObj.optJSONObject("data");
            JSONObject parsed = data == null ? null : data.optJSONObject("parsed");
            JSONObject info = parsed == null ? null : parsed.optJSONObject("info");
            String owner = info == null ? "" : info.optString("owner", "");
            if (owner.isEmpty()) continue;
            double delta = deltaByAccount.getOrDefault(accounts.get(i), 0.0);
            out.put(owner, out.getOrDefault(owner, 0.0) + delta);
        }
        return out;
    }

    private double scoreCommon(BinanceTicker bt, DexToken token, int wallets, double combined, double threshold) {
        double score = 2.0; // at least two independently observed large accumulators already required.
        if (wallets >= 3) score += 0.75;
        if (wallets >= 5) score += 0.35;
        if (combined >= Math.max(250_000.0, threshold * 4.0)) score += 0.75;
        if (Math.abs(bt.change24) <= 3.0) score += 0.50;
        if (Math.abs(token.priceChange1h) <= 2.5) score += 0.25;
        if (token.buys1h > token.sells1h * 1.10 && token.buys1h >= 8) score += 0.50;
        if (token.liquidityUsd >= 1_000_000.0) score += 0.25;
        return score;
    }

    private void maybeAlert(WhaleSignal s) {
        if (s.score < 3.5) return;
        String key = "alert_" + s.chain + "_" + s.symbol;
        long last = prefs.getLong(key, 0L);
        String lastTier = prefs.getString(key + "_tier", "");
        long now = System.currentTimeMillis();
        String tier = s.strong ? "strong" : "watch";
        boolean upgrade = s.strong && !"strong".equals(lastTier);
        if (!upgrade && now - last < ALERT_COOLDOWN_MS) return;
        prefs.edit().putLong(key, now).putString(key + "_tier", tier).apply();

        String title = s.strong
                ? "🐋🔥 تجميع حيتان محتمل قوي — " + s.symbol + "/USDT"
                : "🐋 تجميع حيتان محتمل — " + s.symbol + "/USDT";
        String body = s.wallets + " محافظ مستقلة • صافي +" + money(s.netUsd)
                + " • " + s.chain.toUpperCase(Locale.US)
                + " • 24h " + String.format(Locale.US, "%+.2f%%", s.change24);
        postWhaleNotification(s.symbol, title, body);
    }

    private String formatSignals(int scanned, int mapped, int onchain, List<WhaleSignal> signals, long next) {
        StringBuilder sb = new StringBuilder();
        sb.append("فحص هذه الدورة: ").append(scanned).append(" زوج Binance Spot")
                .append(" • تطابق عقد: ").append(mapped)
                .append(" • On-chain مباشر: ").append(onchain).append("\n");
        sb.append("الدورة التالية تقريبًا خلال ").append(next / 60_000L).append(" دقيقة.\n\n");

        if (signals.isEmpty()) {
            sb.append("لا يوجد تجميع حيتان متعدد المحافظ يستوفي الشروط الآن.\n\n")
                    .append("البرنامج لا يعتبر زيادة الحجم أو الاختراق السعري حوتًا. يجب أن يرى صافي زيادة كبيرة فعلية في عدة محافظ مستقلة.");
            return sb.toString();
        }

        int n = Math.min(6, signals.size());
        for (int i = 0; i < n; i++) {
            WhaleSignal s = signals.get(i);
            sb.append(i + 1).append(". ").append(s.symbol).append("/USDT")
                    .append(s.strong ? "  🐋🔥" : "  🐋")
                    .append("\n   Chain: ").append(s.chain)
                    .append(" • Wallets: ").append(s.wallets)
                    .append(" • Net +").append(money(s.netUsd))
                    .append(" • Largest +").append(money(s.largestUsd))
                    .append("\n   24h ").append(String.format(Locale.US, "%+.2f%%", s.change24))
                    .append(" • Score ").append(String.format(Locale.US, "%.2f", s.score))
                    .append("\n   Source: ").append(s.source).append("\n\n");
        }
        sb.append("تنبيه Whale ≠ أمر شراء؛ هو رصد سلوك on-chain فقط.");
        return sb.toString();
    }

    private int erc20Decimals(String rpc, String token) throws Exception {
        JSONObject call = new JSONObject().put("to", token).put("data", "0x313ce567");
        Object r = rpcCall(rpc, "eth_call", new JSONArray().put(call).put("latest"));
        if (!(r instanceof String)) return -1;
        String hex = (String) r;
        if (hex.length() <= 2) return -1;
        return new BigInteger(strip0x(hex), 16).intValue();
    }

    private boolean isEoa(String rpc, String address) {
        try {
            Object r = rpcCall(rpc, "eth_getCode", new JSONArray().put(address).put("latest"));
            if (!(r instanceof String)) return false;
            String code = ((String) r).toLowerCase(Locale.US);
            return "0x".equals(code) || "0x0".equals(code) || "0x00".equals(code);
        } catch (Exception e) {
            return false;
        }
    }

    private JSONArray getTransferLogs(String rpc, String token, long from, long to, int depth) throws Exception {
        JSONObject filter = new JSONObject()
                .put("address", token)
                .put("fromBlock", "0x" + Long.toHexString(from))
                .put("toBlock", "0x" + Long.toHexString(to))
                .put("topics", new JSONArray().put(TRANSFER_TOPIC));
        try {
            Object r = rpcCall(rpc, "eth_getLogs", new JSONArray().put(filter));
            return r instanceof JSONArray ? (JSONArray) r : new JSONArray();
        } catch (Exception e) {
            if (depth >= 4 || to - from < 250) throw e;
            long mid = (from + to) / 2;
            JSONArray a = getTransferLogs(rpc, token, from, mid, depth + 1);
            JSONArray b = getTransferLogs(rpc, token, mid + 1, to, depth + 1);
            JSONArray joined = new JSONArray();
            for (int i = 0; i < a.length(); i++) joined.put(a.get(i));
            for (int i = 0; i < b.length(); i++) joined.put(b.get(i));
            return joined;
        }
    }

    private Object rpcCall(String rpc, String method, JSONArray params) throws Exception {
        JSONObject body = new JSONObject().put("jsonrpc", "2.0").put("id", 1)
                .put("method", method).put("params", params);
        JSONObject root = postJson(rpc, body);
        if (root.has("error")) throw new Exception("RPC " + method + " • " + root.optJSONObject("error"));
        return root.opt("result");
    }

    private JSONObject solRpc(String method, JSONArray params) throws Exception {
        JSONObject body = new JSONObject().put("jsonrpc", "2.0").put("id", 1)
                .put("method", method).put("params", params);
        JSONObject root = postJson(SOLANA_RPC, body);
        if (root.has("error")) throw new Exception("Solana " + method + " • " + root.optJSONObject("error"));
        return root;
    }

    private JSONObject postJson(String url, JSONObject body) throws Exception {
        HttpsURLConnection c = (HttpsURLConnection) new URL(url).openConnection();
        c.setRequestMethod("POST");
        c.setConnectTimeout(10_000);
        c.setReadTimeout(18_000);
        c.setDoOutput(true);
        c.setRequestProperty("Content-Type", "application/json");
        c.setRequestProperty("Accept", "application/json");
        c.setRequestProperty("User-Agent", "FreeWhaleRadarV3/3.0");
        byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
        try (OutputStream os = c.getOutputStream()) { os.write(bytes); }
        int code = c.getResponseCode();
        InputStream stream = code >= 200 && code < 300 ? c.getInputStream() : c.getErrorStream();
        String text = readStream(stream);
        c.disconnect();
        if (code < 200 || code >= 300) throw new Exception("HTTP " + code + " • " + trimError(text));
        return new JSONObject(text);
    }

    private String readUrl(String url) throws Exception {
        HttpsURLConnection c = (HttpsURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(10_000);
        c.setReadTimeout(15_000);
        c.setRequestProperty("Accept", "application/json");
        c.setRequestProperty("User-Agent", "FreeWhaleRadarV3/3.0");
        int code = c.getResponseCode();
        InputStream stream = code >= 200 && code < 300 ? c.getInputStream() : c.getErrorStream();
        String text = readStream(stream);
        c.disconnect();
        if (code < 200 || code >= 300) throw new Exception("HTTP " + code + " • " + trimError(text));
        return text;
    }

    private String readStream(InputStream stream) throws Exception {
        if (stream == null) return "";
        BufferedReader br = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) sb.append(line);
        br.close();
        return sb.toString();
    }

    private void createChannels() {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        NotificationChannel service = new NotificationChannel(SERVICE_CHANNEL, "Free whale radar service", NotificationManager.IMPORTANCE_LOW);
        service.setSound(null, null);
        service.enableVibration(false);
        nm.createNotificationChannel(service);

        NotificationChannel alerts = new NotificationChannel(ALERT_CHANNEL, "Whale accumulation alerts", NotificationManager.IMPORTANCE_HIGH);
        alerts.enableVibration(true);
        alerts.setVibrationPattern(new long[]{0,250,150,350});
        nm.createNotificationChannel(alerts);
    }

    private Notification buildServiceNotification(String text) {
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 1, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, SERVICE_CHANNEL) : new Notification.Builder(this);
        return b.setContentTitle("Hybrid Explosion Radar V4.2.3")
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

    private void postWhaleNotification(String symbol, String title, String body) { }

    private void sendUi(String status, String details) {
        long now = System.currentTimeMillis();
        if (prefs != null) {
            prefs.edit()
                    .putString("ui_status", safe(status))
                    .putString("ui_details", safe(details))
                    .putString("last_status", safe(status))
                    .putString("last_details", safe(details))
                    .putLong("ui_updated_at", now)
                    .putLong("last_update_at", now)
                    .putBoolean("service_alive", running)
                    .apply();
        }
        Intent i = new Intent(ACTION_UI).setPackage(getPackageName());
        i.putExtra("status", status);
        i.putExtra("details", details);
        i.putExtra("updated_at", now);
        i.putExtra("last_scan_at", prefs == null ? 0L : prefs.getLong("last_scan_at", 0L));
        sendBroadcast(i);
    }

    private boolean eligibleBinanceSymbol(String s) {
        if (s == null || !s.endsWith("USDT") || s.length() <= 4) return false;
        String base = s.substring(0, s.length() - 4).toUpperCase(Locale.US);
        String[] skip = {"USDC","FDUSD","TUSD","USDP","DAI","EUR","TRY","BRL","UAH","BUSD"};
        for (String x : skip) if (base.equals(x)) return false;
        return !(base.endsWith("UP") || base.endsWith("DOWN") || base.endsWith("BULL") || base.endsWith("BEAR"));
    }

    private static double dynamicWhaleThreshold(double liquidity) {
        return Math.max(BASE_WHALE_USD, Math.min(125_000.0, liquidity * 0.0025));
    }

    private static String topicAddress(String t) {
        if (t == null) return "";
        String x = strip0x(t);
        if (x.length() < 40) return "";
        return "0x" + x.substring(x.length() - 40).toLowerCase(Locale.US);
    }

    private static boolean isZero(String a) {
        return "0x0000000000000000000000000000000000000000".equalsIgnoreCase(a);
    }

    private static BigInteger hexBig(String x) {
        String s = strip0x(x);
        return s.isEmpty() ? BigInteger.ZERO : new BigInteger(s, 16);
    }

    private static long hexLong(String x) {
        String s = strip0x(x);
        return s.isEmpty() ? 0 : new BigInteger(s, 16).longValue();
    }

    private static String strip0x(String x) {
        if (x == null) return "";
        return x.startsWith("0x") || x.startsWith("0X") ? x.substring(2) : x;
    }

    private static double nestedNumber(JSONObject o, String object, String field) {
        JSONObject x = o == null ? null : o.optJSONObject(object);
        return x == null ? 0 : x.optDouble(field, 0);
    }

    private static double d(JSONObject j, String k) {
        try { return Double.parseDouble(j.optString(k, "0")); } catch (Exception e) { return j.optDouble(k, 0); }
    }

    private static double parseDouble(String s) {
        try { return Double.parseDouble(s); } catch (Exception e) { return 0; }
    }

    private static String money(double x) {
        double a = Math.abs(x);
        if (a >= 1_000_000) return String.format(Locale.US, "$%.2fM", a / 1_000_000.0);
        if (a >= 1_000) return String.format(Locale.US, "$%.1fK", a / 1_000.0);
        return String.format(Locale.US, "$%.0f", a);
    }

    private static String trimError(String s) {
        if (s == null) return "";
        return s.length() > 180 ? s.substring(0, 180) : s;
    }

    private static String safe(String s) { return s == null ? "" : s; }

    @Override public void onDestroy() {
        if(fastWorker!=null)fastWorker.shutdownNow();
        running = false;
        scanHandler.removeCallbacks(scheduledScan);
        if(enrichmentWorker!=null)enrichmentWorker.shutdownNow();
        if(fastWorker!=null)fastWorker.shutdownNow();
        executor.shutdownNow();
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    static class BinanceTicker {
        String symbol;
        String base;
        double last;
        double quoteVolume;
        double change24;
    }

    static class DexToken {
        String symbol;
        String chain;
        String address;
        String pairAddress;
        double priceUsd;
        double liquidityUsd;
        double priceChange1h;
        double volume1h;
        int buys1h;
        int sells1h;
    }

    static class WalletFlow {
        double inUsd;
        double outUsd;
        int inCount;
        int outCount;
        Set<String> counterparties = new HashSet<>();
        double net() { return inUsd - outUsd; }
    }

    static class WhaleSignal {
        String symbol;
        String chain;
        String source;
        int wallets;
        double netUsd;
        double largestUsd;
        double change24;
        double score;
        boolean strong;
    }
}
