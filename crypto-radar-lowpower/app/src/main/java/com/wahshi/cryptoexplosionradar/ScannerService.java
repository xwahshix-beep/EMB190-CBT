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

    private static final int ONCHAIN_BATCH = 3;
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

    public static final String ACTION_REFRESH_FOLLOW="com.wahshi.cryptoexplosionradar.REFRESH_FOLLOW";
    private java.util.concurrent.ScheduledExecutorService fastWorker;
    private java.util.concurrent.ScheduledFuture<?> fastTask;
    private long lastFollowRequest;
    private synchronized void scheduleFollow(long delay){
        if(fastWorker==null||fastWorker.isShutdown())fastWorker=java.util.concurrent.Executors.newSingleThreadScheduledExecutor();
        if(fastTask!=null)fastTask.cancel(false);
        fastTask=fastWorker.scheduleWithFixedDelay(() -> {
            if(!running)return;
            try{FastWatch.scan(this,prefs);}
            catch(Exception failure){
                // Keep the periodic worker alive; a later attempt may recover.
                android.util.Log.e("FollowWatch","Follow scan failed",failure);
            }
        },delay,15,java.util.concurrent.TimeUnit.SECONDS);
    }

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
        if(ACTION_REFRESH_FOLLOW.equals(action)){
            boolean wasRunning=running;
            if(!running)startScanner();
            long now=android.os.SystemClock.elapsedRealtime();
            if(wasRunning&&now-lastFollowRequest>=2000)scheduleFollow(0);
            lastFollowRequest=now;
            return START_STICKY;
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
        MinuteStreams.start();
        prefs.edit().putBoolean("radar_enabled", true).putBoolean("service_alive", true).apply();
        startForeground(SERVICE_NOTIFICATION_ID, buildServiceNotification("Explosion Radar " + BuildConfig.VERSION_NAME + " يعمل"));
        sendUi("Explosion Radar " + BuildConfig.VERSION_NAME, "أول فحص مجاني On-chain خلال ثوانٍ…");
        scheduleFollow(0);
        scheduleNext(2_000L);
    }

    private synchronized void stopScanner() {
        running = false;
        MinuteStreams.stop();
        scanHandler.removeCallbacks(scheduledScan);
        if(enrichmentWorker!=null)enrichmentWorker.shutdownNow();
        if(fastWorker!=null)fastWorker.shutdownNow();
        prefs.edit().putBoolean("radar_enabled", false).putBoolean("service_alive", false).apply();
        if (alarmManager != null && tickIntent != null) alarmManager.cancel(tickIntent);
        if (cycleWakeLock != null && cycleWakeLock.isHeld()) cycleWakeLock.release();
        sendUi("الرادار متوقف", "تم إيقاف Explosion Radar بواسطة المستخدم.");
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

                List<WhaleSignal> fresh = new ArrayList<>();
                int attempted = 0, failures = 0;
                int mapped = 0;
                int directOnChain = 0;

                for (BinanceTicker bt : batch) {
                    if (!running) break;
                    attempted++;
                    // Persist only actual attempts; a failed provider must not starve the next symbol.
                    prefs.edit().putString("onchain_after_symbol",bt.symbol).apply();
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
                    } catch (Exception failure) {
                        failures++;
                        recordEnrichmentError("onchain", failure);
                    }
                }

                fresh.sort((a, b) -> Double.compare(b.score, a.score));
                cachedWhaleSignals = fresh;
                signals = fresh;
                lastWhaleScanAt = now;
                lastWhaleScanned = attempted;
                prefs.edit().putString("onchain_health", "فُحص " + attempted + " • أخطاء " + failures
                    + " • نتائج بحثية فقط: هوية العقد غير موثقة").apply();
                lastWhaleMapped = mapped;
                lastWhaleOnChain = directOnChain;
            }

            boolean whaleWatch = false;
            for (WhaleSignal w : signals) {
                if (w.score >= 4.25) { whaleWatch = true; break; }
            }
            whaleCadence = whaleWatch ? WATCH_INTERVAL_MS : NORMAL_INTERVAL_MS;

            cachedCatalyst = CatalystEngine.scan(this, all, Collections.emptyList(), prefs);
            java.util.List<String> earlySymbols=new java.util.ArrayList<>();
            for(BinanceTicker t:all)earlySymbols.add(t.symbol);
            HybridEngine.refreshEarly(earlySymbols,prefs);

            }catch(Exception failure){
                cachedWhaleSignals = new ArrayList<>();
                recordEnrichmentError("enrichment", failure);
                prefs.edit().putString("onchain_health", "تعذر تحديث بيانات السلسلة — غير متاحة للإشارات").apply();
            }
        });
    }

    private void runCycleSafe() {
        if(!running)return;
        long started=System.currentTimeMillis();
        try {
            if(cycleWakeLock!=null&&!cycleWakeLock.isHeld())cycleWakeLock.acquire(90000);
            startEnrichment();
            // Symbol/price matching is not verified contract identity. Research results cannot boost BUY.
            List<WhaleSignal> whales=Collections.emptyList();
            HybridEngine.ScanResult result=HybridEngine.scan(this,whales,cachedCatalyst,prefs);
            if(!running)return;
            long finished=System.currentTimeMillis();
            prefs.edit().putLong("last_scan_at",finished).putLong("scan_duration_ms",finished-started).apply();
            sendUi(result.status,result.summary+"\n"+prefs.getString("onchain_health","بيانات السلسلة: انتظار الفحص")+"\nمدة الفحص: "+((finished-started)/1000)+" ثانية");
            updateServiceNotification("رصد الدقيقة يعمل • فرص الشراء دون فتح التطبيق");
        } catch(Exception ex) {
            if(running)sendUi("تعذر تحديث السوق","انتظار — "+safe(ex.getMessage()));
        } finally {
            if(cycleWakeLock!=null&&cycleWakeLock.isHeld())cycleWakeLock.release();
            long elapsed=System.currentTimeMillis()-started;
            scheduleNext(Math.max(MarketHttp.retryDelay(),Math.max(5000,15000-elapsed)));
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

    private void recordEnrichmentError(String stage, Exception failure) {
        // Do not log credentials, URLs, provider bodies or wallet addresses.
        String kind=failure.getClass().getSimpleName();
        android.util.Log.w("RadarData",stage+": "+kind);
        prefs.edit().putString("enrichment_error",stage+": "+kind)
            .putLong("enrichment_error_at",System.currentTimeMillis()).apply();
    }

    private List<BinanceTicker> selectBatch(List<BinanceTicker> all) {
        Map<String,BinanceTicker> bySymbol=new HashMap<>();
        for(BinanceTicker t:all) bySymbol.put(t.symbol,t);
        List<BinanceTicker> batch=new ArrayList<>();
        for(String symbol:FairRotation.next(bySymbol.keySet(),prefs.getString("onchain_after_symbol",""),ONCHAIN_BATCH))
            batch.add(bySymbol.get(symbol));
        return batch;
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
            if (diff > 8.0) continue; // Heuristic only; does NOT verify contract identity or safety.

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
        long now=System.currentTimeMillis();
        String key="sol_cohort_v2_"+token.address;
        JSONObject old=new JSONObject(prefs.getString(key,"{}"));
        JSONObject largest=solRpc("getTokenLargestAccounts",new JSONArray().put(token.address)
            .put(new JSONObject().put("commitment","finalized"))).getJSONObject("result");
        long minimumSlot=Math.max(old.optLong("slot",0),largest.getJSONObject("context").getLong("slot"));
        JSONObject supply=solRpc("getTokenSupply",new JSONArray().put(token.address)
            .put(new JSONObject().put("commitment","finalized"))).getJSONObject("result");
        minimumSlot=Math.max(minimumSlot,supply.getJSONObject("context").getLong("slot"));
        JSONObject supplyValue=supply.getJSONObject("value");
        BigDecimal totalSupply=new BigDecimal(supplyValue.getString("amount")).movePointLeft(supplyValue.getInt("decimals"));
        if(totalSupply.signum()<=0) throw new IllegalStateException("Invalid supply");
        JSONArray top=largest.getJSONArray("value");
        Set<String> topAddresses=new java.util.LinkedHashSet<>();
        for(int i=0;i<top.length();i++) topAddresses.add(top.getJSONObject(i).getString("address"));
        Set<String> union=new java.util.LinkedHashSet<>(topAddresses);
        JSONObject previous=old.optJSONObject("accounts");
        if(previous!=null) for(java.util.Iterator<String> keys=previous.keys();keys.hasNext();) union.add(keys.next());
        if(union.isEmpty() || union.size()>40) { prefs.edit().remove(key).apply(); return null; }
        JSONObject result=solRpc("getMultipleAccounts",new JSONArray().put(new JSONArray(union))
            .put(new JSONObject().put("encoding","jsonParsed").put("commitment","finalized")
                .put("minContextSlot",minimumSlot))).getJSONObject("result");
        long slot=result.getJSONObject("context").getLong("slot");
        JSONArray values=result.getJSONArray("value");
        if(slot<minimumSlot || values.length()!=union.size()) throw new IllegalStateException("Incomplete cohort");
        JSONObject current=new JSONObject(), next=new JSONObject();
        int i=0;
        for(String address:union) {
            JSONObject account=values.optJSONObject(i++);
            if(account==null) {
                // A missing/closed account is unknown, not an assumed zero balance.
                prefs.edit().remove(key).apply();
                throw new IllegalStateException("Missing cohort account");
            }
            JSONObject info=account.getJSONObject("data").getJSONObject("parsed").getJSONObject("info");
            if(!token.address.equals(info.getString("mint"))) throw new IllegalStateException("Mint mismatch");
            JSONObject amount=info.getJSONObject("tokenAmount");
            BigDecimal balance=new BigDecimal(amount.getString("amount")).movePointLeft(amount.getInt("decimals"));
            if(balance.signum()<0) throw new IllegalStateException("Invalid balance");
            JSONObject record=new JSONObject().put("owner",info.getString("owner")).put("amount",balance.toPlainString());
            current.put(address,record);
            if(topAddresses.contains(address)) next.put(address,record);
        }
        Map<String,BigDecimal> net=HolderNet.calculate(old,current,now,slot);
        // Baseline contains current top 20; next observation also queries those leaving the top 20.
        prefs.edit().putString(key,new JSONObject().put("at",now).put("slot",slot).put("accounts",next).toString()).apply();
        double threshold=dynamicWhaleThreshold(token.liquidityUsd);
        int whales=0; double combined=0, largestUsd=0;
        Map<String,BigDecimal> ownerBalances=new HashMap<>();
        for(java.util.Iterator<String> keys=current.keys();keys.hasNext();) {
            String address=keys.next();
            JSONObject record=current.getJSONObject(address);
            String owner=record.getString("owner");
            ownerBalances.put(owner,ownerBalances.getOrDefault(owner,BigDecimal.ZERO).add(new BigDecimal(record.getString("amount"))));
        }
        for(Map.Entry<String,BigDecimal> change:net.entrySet()) {
            // Concentration guard applies AFTER signed owner aggregation, never to individual legs.
            if(ownerBalances.get(change.getKey()).compareTo(totalSupply.multiply(new BigDecimal("0.12")))>0) continue;
            double usd=change.getValue().multiply(BigDecimal.valueOf(token.priceUsd)).doubleValue();
            if(!Double.isFinite(usd) || usd<threshold) continue;
            whales++; combined+=usd; largestUsd=Math.max(largestUsd,usd);
        }
        if(whales<2 || combined<Math.max(75000,threshold*2.4)) return null;
        WhaleSignal signal=new WhaleSignal();
        signal.symbol=bt.base; signal.chain="solana"; signal.wallets=whales;
        signal.netUsd=combined; signal.largestUsd=largestUsd; signal.change24=bt.change24;
        signal.source="زيادة صافية في حسابات مرصودة فقط؛ تغطية جزئية؛ ليست إثبات شراء أو هوية عقد";
        signal.score=scoreCommon(bt,token,whales,combined,threshold);
        signal.strong=false; // unverified identity and partial coverage: research only
        return signal;
    }

    private double scoreCommon(BinanceTicker bt, DexToken token, int wallets, double combined, double threshold) {
        double score = 2.0; // Distinct owner addresses are not proof of economically independent buyers.
        if (wallets >= 3) score += 0.75;
        if (wallets >= 5) score += 0.35;
        if (combined >= Math.max(250_000.0, threshold * 4.0)) score += 0.75;
        if (Math.abs(bt.change24) <= 3.0) score += 0.50;
        if (Math.abs(token.priceChange1h) <= 2.5) score += 0.25;
        if (token.buys1h > token.sells1h * 1.10 && token.buys1h >= 8) score += 0.50;
        if (token.liquidityUsd >= 1_000_000.0) score += 0.25;
        return score;
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
        c.setRequestProperty("User-Agent", "ExplosionRadar/"+BuildConfig.VERSION_NAME);
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
        c.setRequestProperty("User-Agent", "ExplosionRadar/"+BuildConfig.VERSION_NAME);
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
        return b.setContentTitle("Explosion Radar " + BuildConfig.VERSION_NAME)
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
        MinuteStreams.stop();
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
