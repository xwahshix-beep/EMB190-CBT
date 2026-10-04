from pathlib import Path

base = Path('app/src/main/java/com/wahshi/cryptoexplosionradar')
p = base / 'HybridEngine.java'
s = p.read_text(encoding='utf-8')

# Imports for persisted live signals.
if 'import java.util.HashSet;\n' not in s:
    s = s.replace('import java.util.HashMap;\n', 'import java.util.HashMap;\nimport java.util.HashSet;\n', 1)
if 'import java.util.Set;\n' not in s:
    s = s.replace('import java.util.Map;\n', 'import java.util.Map;\nimport java.util.Set;\n', 1)

# Live signal lifetime: short enough for 15m breakout/retest logic.
s = s.replace(
    '    private static final long CATALYST_MEMORY_MS = 6L * 60L * 60L * 1000L;\n',
    '    private static final long CATALYST_MEMORY_MS = 6L * 60L * 60L * 1000L;\n'
    '    private static final long LIVE_SIGNAL_TTL_MS = 2L * 60L * 60L * 1000L;\n'
    '    private static final long INVALIDATED_VISIBLE_MS = 30L * 60L * 1000L;\n',
    1
)

# Record the live Binance price for each candidate.
old = '''                c.score = score;\n                c.rvol = rvol;\n'''
new = '''                c.score = score;\n                c.lastPrice = d.last;\n                c.rvol = rvol;\n'''
if old not in s:
    raise SystemExit('candidate last price anchor not found')
s = s.replace(old, new, 1)

# On technical confirmation, persist the signal and classify it against the current Binance price.
old = '''                if (confirmedBuy) {\n                    c.buyConfirmed = true;\n                    c.totalScore = total;\n                    c.entryLow = Math.max(a.resistance, a.lastClose * 0.997);\n                    c.entryHigh = a.lastClose * 1.003;\n                    c.invalidation = a.invalidation;\n                    buyCount++;\n                    maybeBuyAlert(context, prefs, c, a, total);\n                }\n'''
new = '''                if (confirmedBuy) {\n                    c.buyConfirmed = true;\n                    c.totalScore = total;\n                    c.entryLow = Math.max(a.resistance, a.lastClose * 0.997);\n                    c.entryHigh = a.lastClose * 1.003;\n                    c.invalidation = a.invalidation;\n                    persistLiveSignal(prefs, c, now);\n                    classifyLiveState(c);\n                    if ("ACTIVE".equals(c.liveState)) {\n                        buyCount++;\n                        maybeBuyAlert(context, prefs, c, a, total);\n                    }\n                }\n'''
if old not in s:
    raise SystemExit('confirmed buy block not found')
s = s.replace(old, new, 1)

# Replace V4.2.2 result/sorting block with persisted live-state handling.
old = '''        // Make the actionable state impossible to miss: BUY first, then ARMED, then Early Hunt.\n        candidates.sort((a, b) -> {\n            if (a.buyConfirmed != b.buyConfirmed) return a.buyConfirmed ? -1 : 1;\n            if (a.armed != b.armed) return a.armed ? -1 : 1;\n            return Double.compare(b.score, a.score);\n        });\n\n        List<String> buySymbols = new ArrayList<>();\n        for (Candidate c : candidates) {\n            if (c.buyConfirmed) buySymbols.add(c.base + "/USDT");\n        }\n\n        ScanResult out = new ScanResult();\n        out.candidates = candidates;\n        out.armedCount = armedCount;\n        out.buyCount = buyCount;\n        out.buySymbols = buySymbols;\n        out.hot = armedCount > 0 || buyCount > 0;\n        out.status = buyCount > 0\n                ? "🚨 BUY CONFIRMED: " + join(buySymbols, " • ")\n                : armedCount > 0\n                ? "🎯 ARMED • انتظار التأكيد • " + armedCount + " مرشح"\n                : candidates.isEmpty()\n                ? "Hybrid V4.2.2 • مراقبة السوق"\n                : "🔎 Early Hunt • " + candidates.size() + " مرشح";\n        out.summary = formatSummary(candidates, armedCount, buyCount);\n        return out;\n'''
new = '''        // Restore previously confirmed signals even when they drop out of the current Top candidates.\n        applyPersistedLiveSignals(candidates, day, prefs, now);\n\n        int activeCount = 0;\n        int waitCount = 0;\n        int invalidatedCount = 0;\n        List<String> activeSymbols = new ArrayList<>();\n        List<String> waitSymbols = new ArrayList<>();\n        List<String> invalidatedSymbols = new ArrayList<>();\n        for (Candidate c : candidates) {\n            if (!c.liveSignal) continue;\n            if ("ACTIVE".equals(c.liveState)) {\n                activeCount++; activeSymbols.add(c.base + "/USDT");\n            } else if ("WAIT".equals(c.liveState)) {\n                waitCount++; waitSymbols.add(c.base + "/USDT");\n            } else if ("INVALIDATED".equals(c.liveState)) {\n                invalidatedCount++; invalidatedSymbols.add(c.base + "/USDT");\n            }\n        }\n        buyCount = activeCount;\n\n        // Priority: ACTIVE -> WAIT/RETEST -> INVALIDATED -> ARMED -> Early Hunt.\n        candidates.sort((a, b) -> {\n            int pa = livePriority(a);\n            int pb = livePriority(b);\n            if (pa != pb) return Integer.compare(pa, pb);\n            if (a.armed != b.armed) return a.armed ? -1 : 1;\n            return Double.compare(b.score, a.score);\n        });\n\n        ScanResult out = new ScanResult();\n        out.candidates = candidates;\n        out.armedCount = armedCount;\n        out.buyCount = activeCount;\n        out.waitCount = waitCount;\n        out.invalidatedCount = invalidatedCount;\n        out.buySymbols = activeSymbols;\n        out.hot = armedCount > 0 || activeCount > 0 || waitCount > 0;\n        out.status = activeCount > 0\n                ? "🚨 BUY ACTIVE: " + join(activeSymbols, " • ")\n                : waitCount > 0\n                ? "⏳ WAIT / RETEST: " + join(waitSymbols, " • ")\n                : invalidatedCount > 0\n                ? "❌ INVALIDATED: " + join(invalidatedSymbols, " • ")\n                : armedCount > 0\n                ? "🎯 ARMED • انتظار التأكيد • " + armedCount + " مرشح"\n                : candidates.isEmpty()\n                ? "Hybrid V4.2.3 • مراقبة السوق"\n                : "🔎 Early Hunt • " + candidates.size() + " مرشح";\n        out.summary = formatSummary(candidates, armedCount, activeCount);\n        return out;\n'''
if old not in s:
    raise SystemExit('V4.2.2 result block not found')
s = s.replace(old, new, 1)

# Replace summary headline and BUY section to reflect live states.
s = s.replace('🎯 Hybrid Explosion Radar V4.2.2\\n', '🎯 Hybrid Explosion Radar V4.2.3 • LIVE STATE\\n', 1)
s = s.replace(' • BUY الآن: ").append(buyCount).append("\\n");', ' • BUY ACTIVE: ").append(buyCount).append("\\n");', 1)
s = s.replace('⚠️ ARMED = انتظار التأكيد، وليس شراء.\\n', '⚠️ ARMED = انتظار التأكيد، وليس شراء. BUY ACTIVE فقط عندما السعر داخل نطاق الدخول الحالي.\\n', 1)
s = s.replace('🚨 BUY CONFIRMED NOW\\n', '🚨 BUY ACTIVE NOW\\n', 1)

old = '''            if (c.buyConfirmed) sb.append("  🚨 BUY CONFIRMED");\n            else if (c.armed) sb.append("  🎯 ARMED — انتظار");\n            else sb.append("  🔎");\n'''
new = '''            if (c.liveSignal && "ACTIVE".equals(c.liveState)) sb.append("  🚨 BUY ACTIVE");\n            else if (c.liveSignal && "WAIT".equals(c.liveState)) sb.append("  ⏳ WAIT / RETEST");\n            else if (c.liveSignal && "INVALIDATED".equals(c.liveState)) sb.append("  ❌ INVALIDATED");\n            else if (c.armed) sb.append("  🎯 ARMED — انتظار");\n            else sb.append("  🔎");\n'''
if old not in s:
    raise SystemExit('candidate state label anchor not found')
s = s.replace(old, new, 1)

# Add live price/range detail under any persisted signal.
old = '''            if (c.analysis != null) {\n'''
new = '''            if (c.liveSignal) {\n                sb.append("\\n   Live ").append(fmt(c.lastPrice))\n                        .append(" • Entry ").append(fmt(c.entryLow)).append("–").append(fmt(c.entryHigh))\n                        .append(" • Invalidation ").append(fmt(c.invalidation));\n            }\n            if (c.analysis != null) {\n'''
if old not in s:
    raise SystemExit('analysis detail anchor not found')
s = s.replace(old, new, 1)

# Expand ScanResult and Candidate fields.
old = '''        public int buyCount;\n        public List<String> buySymbols = new ArrayList<>();\n'''
new = '''        public int buyCount;\n        public int waitCount;\n        public int invalidatedCount;\n        public List<String> buySymbols = new ArrayList<>();\n'''
if old not in s:
    raise SystemExit('ScanResult live count anchor not found')
s = s.replace(old, new, 1)

old = '''        boolean buyConfirmed;\n        double totalScore;\n'''
new = '''        boolean buyConfirmed;\n        boolean liveSignal;\n        String liveState = "";\n        long liveSignalAt;\n        double lastPrice;\n        double totalScore;\n'''
if old not in s:
    raise SystemExit('Candidate live fields anchor not found')
s = s.replace(old, new, 1)

# Insert helper methods before formatSummary.
anchor = '    private static String formatSummary(List<Candidate> candidates, int armedCount, int buyCount) {\n'
helpers = r'''    private static void persistLiveSignal(SharedPreferences prefs, Candidate c, long now) {
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

    private static void applyPersistedLiveSignals(List<Candidate> candidates,
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
            classifyLiveState(c);

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
                .apply();
    }

    private static double parsePrefDouble(SharedPreferences prefs, String key, double fallback) {
        try { return Double.parseDouble(prefs.getString(key, Double.toString(fallback))); }
        catch (Exception e) { return fallback; }
    }

'''
if anchor not in s:
    raise SystemExit('formatSummary anchor not found')
s = s.replace(anchor, helpers + anchor, 1)

# Remove duplicate live-detail BUY section's assumption that buyConfirmed means active.
s = s.replace('                if (!c.buyConfirmed) continue;\n', '                if (!(c.liveSignal && "ACTIVE".equals(c.liveState))) continue;\n', 1)

# Version user agent string.
s = s.replace('HybridExplosionRadarV42/4.2', 'HybridExplosionRadarV423/4.2.3')

p.write_text(s, encoding='utf-8')

# ScannerService: keep 2-minute cadence while a live BUY/WAIT signal exists.
p = base / 'ScannerService.java'
ss = p.read_text(encoding='utf-8')
ss = ss.replace(
    '            next = hybrid.armedCount > 0 ? HYBRID_ARMED_MS : HYBRID_NORMAL_MS;\n',
    '            next = hybrid.hot ? HYBRID_ARMED_MS : HYBRID_NORMAL_MS;\n',
    1
)
ss = ss.replace('Hybrid Explosion Radar V4.2', 'Hybrid Explosion Radar V4.2.3')
p.write_text(ss, encoding='utf-8')

# MainActivity: explicit live-state description and version.
p = base / 'MainActivity.java'
m = p.read_text(encoding='utf-8')
m = m.replace('Hybrid Explosion Radar V4.2.2', 'Hybrid Explosion Radar V4.2.3')
m = m.replace('📡 المرشحون الآن • BUY يظهر أولاً', '📡 المرشحون الآن • LIVE SIGNAL أولاً')
m = m.replace(
    '📲 Push فقط عند BUY مؤكد.',
    '📲 LIVE STATE: 🚨 BUY ACTIVE داخل نطاق الدخول • ⏳ WAIT/RETEST خارج النطاق مع بقاء الإبطال سليمًا • ❌ INVALIDATED عند كسر الإبطال.'
)
p.write_text(m, encoding='utf-8')

print('V4.2.3 patch applied: persistent live BUY state + current Binance price revalidation')
