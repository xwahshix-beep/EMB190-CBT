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
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

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
    private static final long LIVE_SIGNAL_TTL_MS = 6L * 60L * 60L * 1000L;
    private static final long INVALIDATED_VISIBLE_MS = 30L * 60L * 1000L;

    private HybridEngine() {}
    private static volatile EarlyWhaleEngine.Result earlyCache=new EarlyWhaleEngine.Result();
    private static volatile long earlyCacheAt;
    public static void refreshEarly(List<String> symbols,SharedPreferences prefs) {
        earlyCache=EarlyWhaleEngine.scan(symbols,prefs,path -> MarketHttp.read(API+path));
        earlyCacheAt=System.currentTimeMillis();
    }

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

        eligible.sort((a,b) -> {
            HourTicker ha=hour.get(a), hb=hour.get(b);
            double ra=ha==null?0:ha.quoteVolume/(day.get(a).quoteVolume/24);
            double rb=hb==null?0:hb.quoteVolume/(day.get(b).quoteVolume/24);
            return Double.compare(rb,ra);
        });
        EarlyWhaleEngine.Result early = System.currentTimeMillis()-earlyCacheAt<300000
                ? earlyCache : new EarlyWhaleEngine.Result();
        List<Candidate> candidates = new ArrayList<>();
        for (String symbol : eligible) {
            DayTicker d = day.get(symbol);
            HourTicker h = hour.get(symbol);
            if (d == null || h == null || d.last <= 0 || h.last <= 0) continue;

            double p24 = d.changePct();
            double p1 = h.changePct();
            if ((p24 < -8.0 || p24 > 10.0) && !SignalChecks.isActive(prefs,symbol)) continue;
            if ((p1 < -1.8 || p1 > 5.5) && !SignalChecks.isActive(prefs,symbol)) continue;

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

            EarlyWhaleEngine.Evidence flow = early.evidence.get(symbol);
            if (flow != null) { score += flow.score; reasons.add(flow.label()); }
            boolean hasFlow = rvol >= 1.25 || tradeAccel >= 1.25 || whale != null || catPos || flow != null;
            if (score >= 1.0 || hasFlow || SignalChecks.isActive(prefs,symbol)) {
                Candidate c = new Candidate();
                c.symbol = symbol;
                c.base = base;
                c.score = score;
                c.lastPrice = d.last;
                c.rvol = rvol;
                c.tradeAccel = tradeAccel;
                c.p1 = p1;
                c.p24 = p24;
                c.spread = spread;
                c.nearHigh = nearHigh;
                c.whale = whale;
                c.earlyWhale = flow;
                c.catalystPositive = catPos;
                c.catalystNegative = catNeg;
                c.reasons = reasons;
                candidates.add(c);
            }
        }

        candidates.sort((a, b) -> {int active=Boolean.compare(SignalChecks.isActive(prefs,b.symbol),SignalChecks.isActive(prefs,a.symbol));return active!=0?active:Double.compare(b.score,a.score);});
        // Keep four leaders and rotate four other liquid candidates; scoring thresholds remain below.
        List<Candidate> work=new ArrayList<>();
        int leaders=Math.min(4,candidates.size());
        work.addAll(candidates.subList(0,leaders));
        List<Candidate> rest=new ArrayList<>(candidates.subList(leaders,candidates.size()));
        rest.sort((x,y)->x.symbol.compareTo(y.symbol));
        if(!rest.isEmpty()) {
            int cursor=Math.floorMod(prefs.getInt("minute_cursor",0),rest.size());
            int batch=Math.min(4,rest.size());
            for(int i=0;i<batch;i++)work.add(rest.get((cursor+i)%rest.size()));
            prefs.edit().putInt("minute_cursor",(cursor+batch)%rest.size()).apply();
        }
        int confirmN=work.size();
        long deadline=System.currentTimeMillis()+30000;
        int checkedCount=0,failedCount=0;
        int buyCount = 0;
        int armedCount = 0;

        for (int i = 0; i < confirmN; i++) {
            Candidate c = work.get(i);
            if(System.currentTimeMillis()>deadline||!prefs.getBoolean("radar_enabled",false))break;
            try {
                Analysis a = analyze1m(c.symbol);
                c.analysis = a;
                if (a == null) {c.waitReason="بيانات الدقيقة غير مكتملة";failedCount++;logDecision(prefs,c.symbol,null,"DATA",c.waitReason,System.currentTimeMillis());continue;}
                checkedCount++;
                c.lastPrice=a.lastClose;
                c.checkedAt=System.currentTimeMillis();
                // Watch states precede the breakout: do not gate them on the BUY entry window.
                c.armed=a.pre.state.equals("WATCH")||a.pre.state.equals("EARLY_WATCH");
                if(c.armed)armedCount++;
                c.waitReason=a.pre.detail()+" • "+a.pre.reason;
                logDecision(prefs,c.symbol,a.pre,a.pre.state,c.waitReason,c.checkedAt);
                double total = c.score + a.score;
                boolean confirmedBuy = a.pre.state.equals("BUY") && !c.catalystNegative;
                if(confirmedBuy){
                    String guard=a.window.reason(a.lastClose,c.checkedAt);
                    if(guard!=null){c.waitReason=guard;logDecision(prefs,c.symbol,a.pre,"WAIT",guard,c.checkedAt);continue;}
                }
                if (confirmedBuy) {
                    c.buyConfirmed = true;
                    c.totalScore = total;
                    c.entryLow = a.window.entryLow();
                    c.entryHigh = a.window.entryHigh();
                    if (!BuyAlertPolicy.validRange(c.entryLow,c.entryHigh)) {logDecision(prefs,c.symbol,a.pre,"WAIT","Invalid BUY price band",c.checkedAt);continue;}
                    c.invalidation = a.invalidation;
                    c.liveSignal=true;
                    c.liveSignalAt=a.window.closedAt;
                    c.waitReason="انتظار دخول السعر في النطاق";
                    classifyLiveState(c);
                    if ("ACTIVE".equals(c.liveState)) {
                        c.tradeTriggered = true;
                        prefs.edit().putBoolean("live_triggered_" + c.symbol, true).apply();
                        buyCount++;
                        maybeBuyAlert(context, prefs, c, a, total);
                    }
                    classifyManagement(c);
                }
            } catch (Exception ex) {
                failedCount++;c.waitReason="تعذر تحديث بيانات الدقيقة";
                logDecision(prefs,c.symbol,null,"DATA",c.waitReason,System.currentTimeMillis());
            }
        }

        // Never restore a legacy 15m BUY or renew its timestamp.
        prefs.edit().remove("hybrid_live_symbols").apply();

        // Notify on active, recently confirmed signals across the scanned universe.
        // Revalidate the quote after slow optional market layers have completed.
        for (Candidate c : candidates) {
            long checked=System.currentTimeMillis();
            if(!prefs.getBoolean("radar_enabled",false))break;
            if(!c.liveSignal || c.analysis==null || c.catalystNegative
                    || !BuyAlertPolicy.validRange(c.entryLow,c.entryHigh))continue;
            if(checked-c.liveSignalAt>75000){c.liveState="WAIT";c.waitReason="بيانات الدقيقة تحتاج تحديثًا";continue;}
            // Revalidate UI state as well, even when notification cooldown is active.
            try {
                long quoteAt=System.currentTimeMillis();
                JSONObject quote=new JSONObject(readEarlyUrl(API+"/api/v3/ticker/bookTicker?symbol="+c.symbol));
                double bid=quote.getDouble("bidPrice"),ask=quote.getDouble("askPrice");
                if(!Double.isFinite(bid+ask)||bid<=0||ask<bid)throw new Exception("invalid quote");
                c.lastPrice=ask;
                String reason=c.analysis.window.reason(ask,System.currentTimeMillis());
                if(reason==null && (ask-bid)/bid*10000>15)reason="انتظار — فارق السعر مرتفع";
                if(reason!=null){c.liveState="WAIT";c.tradeTriggered=false;c.waitReason=reason;continue;}
                classifyLiveState(c);
                classifyManagement(c);
                if("ACTIVE".equals(c.liveState)) {
                    SignalChecks.Result stable=SignalChecks.confirm(prefs,c.symbol,c.analysis.window,c.lastPrice,quoteAt);
                    c.lastPrice=stable.price;c.waitReason=stable.reason;
                    if(!stable.buy){c.liveState="WAIT";c.tradeTriggered=false;logDecision(prefs,c.symbol,c.analysis.pre,"WAIT",stable.reason,checked);continue;}
                    c.displayUntil=stable.until;
                    logDecision(prefs,c.symbol,c.analysis.pre,"BUY","Verified quote and stability: "+stable.reason,checked);
                    OpportunityAlerts.send(context,prefs,c.symbol,c.lastPrice,c.entryLow,c.entryHigh,stable.quoteAt,c.liveSignalAt,c.analysis.window);
                } else c.waitReason=c.lastPrice>c.entryHigh?"فات نطاق الدخول":"انتظار دخول السعر في النطاق";
            } catch(Exception ignored) {c.liveState="WAIT";c.waitReason="تعذر التحقق من السعر الحالي";}
        }

        for(Candidate c:candidates)if(c.analysis!=null&&!"ACTIVE".equals(c.liveState)){c.waitReason=SignalChecks.reject(prefs,c.symbol,c.waitReason);SignalChecks.cancelEnded(context,prefs,c.symbol);}
        int activeCount = 0;
        int waitCount = 0;
        int invalidatedCount = 0;
        List<String> activeSymbols = new ArrayList<>();
        List<String> waitSymbols = new ArrayList<>();
        List<String> invalidatedSymbols = new ArrayList<>();
        for (Candidate c : candidates) {
            if (!c.liveSignal) continue;
            if ("ACTIVE".equals(c.liveState)) {
                activeCount++; activeSymbols.add(c.base + "/USDT");
            } else if ("WAIT".equals(c.liveState)) {
                waitCount++; waitSymbols.add(c.base + "/USDT");
            } else if ("INVALIDATED".equals(c.liveState)) {
                invalidatedCount++; invalidatedSymbols.add(c.base + "/USDT");
            }
        }
        buyCount = activeCount;
        JSONObject expiries=new JSONObject();
        for(Candidate c:candidates)if("ACTIVE".equals(c.liveState))expiries.put(c.symbol,c.displayUntil);
        prefs.edit().putString("market_buy_expiries",expiries.toString()).remove("market_buy_display_until").apply();

        // Priority: ACTIVE -> WAIT/RETEST -> INVALIDATED -> ARMED -> Early Hunt.
        candidates.sort((a, b) -> {
            int pa = livePriority(a);
            int pb = livePriority(b);
            if (pa != pb) return Integer.compare(pa, pb);
            if (a.armed != b.armed) return a.armed ? -1 : 1;
            return Double.compare(b.score, a.score);
        });

        ScanResult out = new ScanResult();
        out.candidates = candidates;
        out.armedCount = armedCount;
        out.buyCount = activeCount;
        out.waitCount = waitCount;
        out.invalidatedCount = invalidatedCount;
        out.buySymbols = activeSymbols;
        out.hot = armedCount > 0 || activeCount > 0 || waitCount > 0;
        out.status = activeCount > 0
                ? "🚨 BUY ACTIVE: " + join(activeSymbols, " • ")
                : waitCount > 0
                ? "⏳ WAIT / RETEST: " + join(waitSymbols, " • ")
                : invalidatedCount > 0
                ? "❌ INVALIDATED: " + join(invalidatedSymbols, " • ")
                : armedCount > 0
                ? "🎯 ARMED • انتظار التأكيد • " + armedCount + " مرشح"
                : candidates.isEmpty()
                ? "Hybrid V4.2.3 • مراقبة السوق"
                : "🔎 Early Hunt • " + candidates.size() + " مرشح";
        out.summary = formatSummary(candidates, armedCount, activeCount)
                + "\n\n📌 مرشحون رُصدوا سابقًا (تاريخي، ليست إشارة شراء حالية):\n" + DecisionLedger.recent(prefs.getString("v6_recent", "{}"),now,8)
                + "\nالسوق: "+hour.size()+"/"+eligible.size()+" • فحص الدقيقة: "+checkedCount+" • تعذر: "+failedCount;
        prefs.edit().putInt("market_checked",hour.size()).putInt("minute_checked",checkedCount).putInt("minute_failed",failedCount).apply();
        return out;
    }

    private static void logDecision(SharedPreferences prefs,String symbol,PreExplosionEngine.Result r,String state,String reason,long now) {
        synchronized (HybridEngine.class) {
            String events=DecisionLedger.append(prefs.getString("v6_trace","[]"),symbol,state,reason,r,now);
            String remembered=DecisionLedger.remember(prefs.getString("v6_recent","{}"),symbol,r,now);
            prefs.edit().putString("v6_trace",events).putString("v6_recent",remembered).apply();
        }
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
            JSONArray batch;
            try { batch = new JSONArray(readUrl(url)); }
            catch (Exception ex) { continue; }
            for (int i = 0; i < batch.length(); i++) {
                JSONObject j = batch.optJSONObject(i);
                if (j == null) continue;
                String symbol = j.optString("symbol", "");
                if (!symbol.isEmpty()) out.put(symbol, HourTicker.from(j));
            }
        }
        if (out.isEmpty() && !symbols.isEmpty()) throw new Exception("Rolling market data unavailable");
        return out;
    }

    private static Analysis analyze1m(String symbol) throws Exception {
        String u = API + "/api/v3/klines?symbol=" + symbol + "&interval=1m&limit=130";
        JSONArray rows = new JSONArray(readEarlyUrl(u));
        EntryWindow window=EntryWindow.parse(rows,System.currentTimeMillis());
        PreExplosionEngine.Result pre=PreExplosionJson.evaluate(rows,System.currentTimeMillis());
        window.resistance=pre.resistance;
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
        a.pre=pre;
        a.score = score;
        a.rvol = rvol;
        a.tradeRatio = tradeR;
        a.takerBuyRatio = taker;
        a.resistance = pre.resistance;
        window.resistance=a.resistance;
        a.window=window;
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

    private static boolean maybeBuyAlert(Context context, SharedPreferences prefs, Candidate c, Analysis a, double totalScore) { return false; }

    private static void persistLiveSignal(SharedPreferences prefs, Candidate c, long now) {
        Set<String> symbols = new HashSet<>(prefs.getStringSet("hybrid_live_symbols", Collections.emptySet()));
        symbols.add(c.symbol);
        prefs.edit()
                .putStringSet("hybrid_live_symbols", symbols)
                .putLong("live_at_" + c.symbol, now)
                .putString("live_entry_low_" + c.symbol, Double.toString(c.entryLow))
                .putString("live_entry_high_" + c.symbol, Double.toString(c.entryHigh))
                .putString("live_invalidation_" + c.symbol, Double.toString(c.invalidation))
                .putString("live_score_" + c.symbol, Double.toString(c.totalScore))
                .remove("live_invalidated_at_" + c.symbol)
                .apply();
        c.liveSignal = true;
        c.liveSignalAt = now;
    }

    private static void applyPersistedLiveSignals(Context context, List<Candidate> candidates,
                                                   Map<String, DayTicker> day,
                                                   SharedPreferences prefs,
                                                   long now) {
        Set<String> symbols = new HashSet<>(prefs.getStringSet("hybrid_live_symbols", Collections.emptySet()));
        if (symbols.isEmpty()) return;

        Map<String, Candidate> bySymbol = new HashMap<>();
        for (Candidate c : candidates) bySymbol.put(c.symbol, c);
        boolean changed = false;

        for (String symbol : new HashSet<>(symbols)) {
            long at = prefs.getLong("live_at_" + symbol, 0L);
            long invalidatedAt = prefs.getLong("live_invalidated_at_" + symbol, 0L);
            boolean tooOld = at <= 0L || now - at > LIVE_SIGNAL_TTL_MS;
            boolean invalidatedExpired = invalidatedAt > 0L && now - invalidatedAt > INVALIDATED_VISIBLE_MS;
            if (tooOld || invalidatedExpired) {
                symbols.remove(symbol);
                clearLiveSignalKeys(prefs, symbol);
                changed = true;
                continue;
            }

            DayTicker d = day.get(symbol);
            if (d == null || d.last <= 0) continue;
            Candidate c = bySymbol.get(symbol);
            if (c == null) {
                c = new Candidate();
                c.symbol = symbol;
                c.base = symbol.endsWith("USDT") ? symbol.substring(0, symbol.length() - 4) : symbol;
                c.score = parsePrefDouble(prefs, "live_score_" + symbol, 0.0);
                candidates.add(c);
                bySymbol.put(symbol, c);
            }
            c.liveSignal = true;
            c.liveSignalAt = at;
            c.entryLow = parsePrefDouble(prefs, "live_entry_low_" + symbol, 0.0);
            c.entryHigh = parsePrefDouble(prefs, "live_entry_high_" + symbol, 0.0);
            c.invalidation = parsePrefDouble(prefs, "live_invalidation_" + symbol, 0.0);
            c.totalScore = parsePrefDouble(prefs, "live_score_" + symbol, c.score);
            c.lastPrice = d.last;
            c.tradeTriggered = prefs.getBoolean("live_triggered_" + symbol, false);
            classifyLiveState(c);
            if ("ACTIVE".equals(c.liveState) && !c.tradeTriggered) {
                c.tradeTriggered = true;
                prefs.edit().putBoolean("live_triggered_" + symbol, true).apply();
            }
            classifyManagement(c);
            if (c.tradeTriggered && "EXIT".equals(c.managementState)) {
                maybeExitAlert(context, prefs, c);
            }

            if ("INVALIDATED".equals(c.liveState) && invalidatedAt <= 0L) {
                prefs.edit().putLong("live_invalidated_at_" + symbol, now).apply();
            }
        }
        if (changed) prefs.edit().putStringSet("hybrid_live_symbols", symbols).apply();
    }

    private static void classifyLiveState(Candidate c) {
        c.liveSignal = true;
        if (c.invalidation > 0 && c.lastPrice <= c.invalidation) {
            c.liveState = "INVALIDATED";
        } else if (c.entryLow > 0 && c.entryHigh > 0
                && c.lastPrice >= c.entryLow && c.lastPrice <= c.entryHigh) {
            c.liveState = "ACTIVE";
        } else {
            c.liveState = "WAIT";
        }
    }

    private static void classifyManagement(Candidate c) {
        if (!c.liveSignal || c.entryLow <= 0 || c.entryHigh <= 0) {
            c.managementState = "";
            return;
        }

        double entry = (c.entryLow + c.entryHigh) / 2.0;
        double risk = entry - c.invalidation;
        if (risk <= 0) risk = Math.max(entry * 0.025, entry - c.entryLow);
        c.target1 = entry + risk;
        c.target2 = entry + (2.0 * risk);
        c.managedStop = c.invalidation;

        if (!c.tradeTriggered) {
            c.managementState = "WAIT";
            return;
        }
        if (c.lastPrice <= c.invalidation) {
            c.managementState = "EXIT";
            return;
        }
        if (c.lastPrice >= c.target2) {
            c.managementState = "TP2";
            c.managedStop = c.target1;
            return;
        }
        if (c.lastPrice >= c.target1) {
            c.managementState = "TP1";
            c.managedStop = entry;
            return;
        }
        c.managementState = "HOLD";
    }

    private static int livePriority(Candidate c) {
        if (c.liveSignal && "ACTIVE".equals(c.liveState)) return 0;
        if (c.liveSignal && "WAIT".equals(c.liveState)) return 1;
        if (c.liveSignal && "INVALIDATED".equals(c.liveState)) return 2;
        if (c.armed) return 3;
        return 4;
    }

    private static void clearLiveSignalKeys(SharedPreferences prefs, String symbol) {
        prefs.edit()
                .remove("live_at_" + symbol)
                .remove("live_entry_low_" + symbol)
                .remove("live_entry_high_" + symbol)
                .remove("live_invalidation_" + symbol)
                .remove("live_score_" + symbol)
                .remove("live_invalidated_at_" + symbol)
                .remove("live_triggered_" + symbol)
                .apply();
    }

    private static double parsePrefDouble(SharedPreferences prefs, String key, double fallback) {
        try { return Double.parseDouble(prefs.getString(key, Double.toString(fallback))); }
        catch (Exception e) { return fallback; }
    }

    private static void maybeExitAlert(Context context, SharedPreferences prefs, Candidate c) { }

    private static String formatSummary(List<Candidate> candidates, int armedCount, int buyCount) {
        StringBuilder sb = new StringBuilder();
        int shown = 0;
        final int MAX_SIMPLE = 7;

        for (Candidate c : candidates) {
            if (shown >= MAX_SIMPLE) break;

            boolean buyNow = c.liveSignal && "ACTIVE".equals(c.liveState);
            boolean managed = c.liveSignal && c.tradeTriggered;
            boolean wait = !managed && ((c.liveSignal && "WAIT".equals(c.liveState)) || c.armed);
            boolean exit = managed && "EXIT".equals(c.managementState);
            boolean tp2 = managed && "TP2".equals(c.managementState);
            boolean tp1 = managed && "TP1".equals(c.managementState);
            boolean hold = managed && "HOLD".equals(c.managementState) && !buyNow;

            if(c.analysis==null && c.earlyWhale==null)continue;
            if(c.analysis!=null && c.analysis.window.reason(c.lastPrice,System.currentTimeMillis())!=null)buyNow=false;
            // Automated discovery is not an executed user trade.
            exit=false;tp2=false;tp1=false;hold=false;

            if (shown > 0) sb.append("\n────────────────\n\n");
            sb.append(c.base).append("/USDT\n");

            if (buyNow) {
                sb.append("🟢 شراء\n");
                sb.append("نطاق الشراء: ")
                        .append(fmt(c.entryLow)).append(" – ").append(fmt(c.entryHigh));
            } else if (exit) {
                sb.append("🔴 خروج\n");
                sb.append("كسر مستوى الحماية: ").append(fmt(c.invalidation));
            } else if (tp2) {
                sb.append("💰 خذ ربحًا إضافيًا\n");
                sb.append("وقف الحماية الآن: ").append(fmt(c.managedStop));
            } else if (tp1) {
                sb.append("🟣 بيع جزئي\n");
                sb.append("وقف الحماية الآن: ").append(fmt(c.managedStop));
            } else if (hold) {
                sb.append("🔵 احتفاظ\n");
                sb.append("الهدف الأول: ").append(fmt(c.target1))
                        .append(" • وقف: ").append(fmt(c.managedStop));
            } else {
                sb.append(c.armed?"🐋 مراقبة مبكرة\n":"🟡 انتظار\n");
                if (c.liveSignal && BuyAlertPolicy.validRange(c.entryLow,c.entryHigh)) {
                    sb.append("نطاق الشراء: ")
                            .append(fmt(c.entryLow)).append(" – ").append(fmt(c.entryHigh));
                } else {
                    sb.append("نطاق الشراء: بانتظار التأكيد");
                }
            }
            sb.append("\nالسعر: ").append(fmt(c.lastPrice));
            if(c.waitReason!=null)sb.append("\n").append(c.waitReason);
            if(c.analysis!=null)sb.append("\nشمعة الدقيقة: ").append(new java.text.SimpleDateFormat("HH:mm:ss",Locale.getDefault()).format(new java.util.Date(c.analysis.window.closedAt)));
            if (c.earlyWhale != null) sb.append("\n").append(c.earlyWhale.label());
            shown++;
        }

        if (shown == 0) sb.append("لا توجد فرصة جاهزة الآن");
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

    private static String readEarlyUrl(String url) throws Exception {return MarketHttp.read(url);}

    private static String readUrl(String url) throws Exception {return MarketHttp.read(url);}

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
        public int waitCount;
        public int invalidatedCount;
        public List<String> buySymbols = new ArrayList<>();
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
        boolean buyConfirmed;
        boolean liveSignal;
        boolean tradeTriggered;
        String liveState = "";
        String managementState = "";
        long liveSignalAt;
        long displayUntil;
        double lastPrice;
        double totalScore;
        double target1;
        double target2;
        double managedStop;
        double entryLow;
        double entryHigh;
        double invalidation;
        ScannerService.WhaleSignal whale;
        EarlyWhaleEngine.Evidence earlyWhale;
        Analysis analysis;
        String waitReason="بانتظار فحص الدقيقة";
        long checkedAt;
        List<String> reasons;
    }

    static final class Analysis {
        EntryWindow window;
        PreExplosionEngine.Result pre;
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
