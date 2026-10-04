from pathlib import Path
import re

base = Path('app/src/main/java/com/wahshi/cryptoexplosionradar')

# ScannerService: Hybrid scheduler + BSC whale coverage.
p = base / 'ScannerService.java'
s = p.read_text(encoding='utf-8')

if 'EVM_RPC.put("bsc"' not in s:
    s = s.replace(
        '        EVM_RPC.put("avalanche", "https://api.avax.network/ext/bc/C/rpc");\n',
        '        EVM_RPC.put("avalanche", "https://api.avax.network/ext/bc/C/rpc");\n'
        '        EVM_RPC.put("bsc", "https://bsc-rpc.publicnode.com");\n',
        1
    )
if 'EVM_LOOKBACK_BLOCKS.put("bsc"' not in s:
    s = s.replace(
        '        EVM_LOOKBACK_BLOCKS.put("avalanche", 2400);\n',
        '        EVM_LOOKBACK_BLOCKS.put("avalanche", 2400);\n'
        '        EVM_LOOKBACK_BLOCKS.put("bsc", 8000);\n',
        1
    )

start = s.index('    private void runCycleSafe() {')
end = s.index('    private List<BinanceTicker> loadBinanceSpotTickers()', start)
method = r'''    private void runCycleSafe() {
        if (!running) return;
        final long HYBRID_NORMAL_MS = 5 * 60_000L;
        final long HYBRID_ARMED_MS = 2 * 60_000L;
        long next = HYBRID_NORMAL_MS;
        boolean wakeHeld = false;
        try {
            if (cycleWakeLock != null && !cycleWakeLock.isHeld()) {
                cycleWakeLock.acquire(3 * 60_000L);
                wakeHeld = true;
            }

            long now = System.currentTimeMillis();
            List<BinanceTicker> all = loadBinanceSpotTickers();

            boolean existingWhaleWatch = false;
            for (WhaleSignal w : cachedWhaleSignals) {
                if (w.score >= 4.25) { existingWhaleWatch = true; break; }
            }
            long whaleCadence = existingWhaleWatch ? WATCH_INTERVAL_MS : NORMAL_INTERVAL_MS;
            boolean whaleDue = cachedWhaleSignals.isEmpty()
                    || lastWhaleScanAt == 0L
                    || now - lastWhaleScanAt >= whaleCadence;

            List<WhaleSignal> signals = cachedWhaleSignals;
            if (whaleDue) {
                List<BinanceTicker> batch = selectBatch(all);
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

            CatalystEngine.ScanResult catalyst = CatalystEngine.scan(this, all, signals, prefs);
            HybridEngine.ScanResult hybrid = HybridEngine.scan(this, signals, catalyst, prefs);
            next = hybrid.armedCount > 0 ? HYBRID_ARMED_MS : HYBRID_NORMAL_MS;

            String whaleDetails = formatSignals(
                    lastWhaleScanned, lastWhaleMapped, lastWhaleOnChain, signals, whaleCadence);
            long minsToWhale = Math.max(1L,
                    (whaleCadence - Math.max(0L, System.currentTimeMillis() - lastWhaleScanAt)) / 60_000L);

            String details = hybrid.summary
                    + "\n\n──────── 🐋 WHALE LAYER ────────\n" + whaleDetails
                    + "\n\n──────── ⚡ CATALYST LAYER ────────\n" + catalyst.summary
                    + "\n\n🔄 Early Hunt: " + (next / 60_000L) + " دقيقة"
                    + " • 🐋 Whale scan التالي: ~" + minsToWhale + " دقيقة"
                    + "\n✅ Background wake: ACTIVE • BSC whale RPC: enabled";

            prefs.edit().putLong("last_scan_at", System.currentTimeMillis()).apply();
            sendUi(hybrid.status, details);
            updateServiceNotification(hybrid.status + " • next ~" + (next / 60_000L) + "m");
        } catch (Exception e) {
            next = HYBRID_NORMAL_MS;
            sendUi("Hybrid V4.2 • خطأ مؤقت",
                    "سيعيد فحص السوق تلقائيًا.\n" + safe(e.getMessage()));
            updateServiceNotification("Hybrid V4.2 • إعادة محاولة ~5m");
        } finally {
            if (wakeHeld && cycleWakeLock != null && cycleWakeLock.isHeld()) cycleWakeLock.release();
            scheduleNext(next);
        }
    }

'''
s = s[:start] + method + s[end:]
s = s.replace('Whale + Catalyst Radar V4.1', 'Hybrid Explosion Radar V4.2')
s = s.replace('Whale + Catalyst Radar', 'Hybrid Explosion Radar V4.2')
p.write_text(s, encoding='utf-8')

# CatalystEngine: expose only fresh symbols and silence standalone push by default.
p = base / 'CatalystEngine.java'
c = p.read_text(encoding='utf-8')

needle = '''                    boolean seen = prefs.contains(seenKey);\n                    if (!initialized || seen) continue;\n                    prefs.edit().putLong(seenKey, System.currentTimeMillis()).apply();\n'''
replacement = '''                    boolean seen = prefs.contains(seenKey);\n                    if (!initialized || seen) continue;\n                    if (s.positive) out.freshPositiveSymbols.add(symbol);\n                    if (s.negative) out.freshNegativeSymbols.add(symbol);\n                    prefs.edit().putLong(seenKey, System.currentTimeMillis()).apply();\n'''
if needle not in c:
    raise SystemExit('Catalyst fresh-symbol anchor not found')
c = c.replace(needle, replacement, 1)

fields = '''        public final Set<String> combinedSymbols = new HashSet<>();\n'''
field_repl = '''        public final Set<String> combinedSymbols = new HashSet<>();\n        public final Set<String> freshPositiveSymbols = new HashSet<>();\n        public final Set<String> freshNegativeSymbols = new HashSet<>();\n'''
if fields not in c:
    raise SystemExit('Catalyst ScanResult anchor not found')
c = c.replace(fields, field_repl, 1)

c = re.sub(
    r'(?m)^(\s*)post\(context,',
    r'\1if (prefs.getBoolean("standalone_signal_notifications", false)) post(context,',
    c
)
p.write_text(c, encoding='utf-8')

# MainActivity: communicate the Hybrid stages clearly.
p = base / 'MainActivity.java'
m = p.read_text(encoding='utf-8')
m = m.replace('🐋⚡ Whale + Catalyst Radar V4.1', '🎯🐋⚡ Hybrid Explosion Radar V4.2')
m = m.replace('بدء Whale + Catalyst Radar V4.1…', 'بدء Hybrid Explosion Radar V4.2…')
m = m.replace(
    'FREE • Binance Spot / USDT\\nOn-chain whales + official Binance catalysts • Background Wake',
    'FREE • Binance Spot / USDT\\nEarly Hunt + 15m confirmation + Whale + Catalyst • Background Wake'
)

note_start = m.index('        note.setText("')
note_end = m.index('");', note_start) + 3
new_note = '''        note.setText("\\n🔎 EARLY HUNT: يفحص سوق Binance Spot/USDT كاملًا تقريبًا بحثًا عن RVOL وتسارع الصفقات والزخم المبكر.\\n\\n🎯 ARMED: أفضل المرشحين يخضعون لتأكيد 15m: Taker Buy + Higher Lows/Compression + Resistance/Retest.\\n\\n🐋 WHALE: التجميع On-chain أصبح عامل تقوية وليس شرطًا، مع Ethereum/Base/Arbitrum/Avalanche/Solana وBNB Smart Chain عبر RPC مجاني.\\n\\n⚡ CATALYST: الأخبار الرسمية من Binance ترفع أو تخفض ثقة المرشح، لكنها ليست شرطًا.\\n\\n📲 لا إشعارات للهاتف لـ Early Hunt أو ARMED أو Whale/Catalyst منفردة؛ Push فقط عند BUY مؤكد.\\n\\n🌙 Background Wake من V4.1 محفوظ لتستمر الدورة والشاشة مطفأة.");'''
m = m[:note_start] + new_note + m[note_end:]
p.write_text(m, encoding='utf-8')

print('V4.2 Hybrid patch applied: Early Hunt + 15m + Whale/Catalyst bonuses + BSC')
