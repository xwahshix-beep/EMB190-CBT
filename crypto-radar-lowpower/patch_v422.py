from pathlib import Path

base = Path('app/src/main/java/com/wahshi/cryptoexplosionradar')

# --- HybridEngine: mark current BUY state, show symbol explicitly, sort BUY first. ---
p = base / 'HybridEngine.java'
s = p.read_text(encoding='utf-8')

old = '''                if ((retestBuy || breakoutBuy) && c.p24 <= 10.0 && !c.catalystNegative) {\n                    if (maybeBuyAlert(context, prefs, c, a, total)) buyCount++;\n                }\n'''
new = '''                boolean confirmedBuy = (retestBuy || breakoutBuy)\n                        && c.p24 <= 10.0\n                        && !c.catalystNegative;\n                if (confirmedBuy) {\n                    c.buyConfirmed = true;\n                    c.totalScore = total;\n                    c.entryLow = Math.max(a.resistance, a.lastClose * 0.997);\n                    c.entryHigh = a.lastClose * 1.003;\n                    c.invalidation = a.invalidation;\n                    buyCount++;\n                    maybeBuyAlert(context, prefs, c, a, total);\n                }\n'''
if old not in s:
    raise SystemExit('BUY confirmation anchor not found')
s = s.replace(old, new, 1)

old = '''        ScanResult out = new ScanResult();\n        out.candidates = candidates;\n        out.armedCount = armedCount;\n        out.buyCount = buyCount;\n        out.hot = armedCount > 0;\n        out.status = buyCount > 0\n                ? "🚨 BUY confirmed • Hybrid V4.2"\n                : armedCount > 0\n                ? "🎯 ARMED • " + armedCount + " مرشح"\n                : candidates.isEmpty()\n                ? "Hybrid V4.2 • مراقبة السوق"\n                : "🔎 Early Hunt • " + candidates.size() + " مرشح";\n        out.summary = formatSummary(candidates, armedCount, buyCount);\n        return out;\n'''
new = '''        // Make the actionable state impossible to miss: BUY first, then ARMED, then Early Hunt.\n        candidates.sort((a, b) -> {\n            if (a.buyConfirmed != b.buyConfirmed) return a.buyConfirmed ? -1 : 1;\n            if (a.armed != b.armed) return a.armed ? -1 : 1;\n            return Double.compare(b.score, a.score);\n        });\n\n        List<String> buySymbols = new ArrayList<>();\n        for (Candidate c : candidates) {\n            if (c.buyConfirmed) buySymbols.add(c.base + "/USDT");\n        }\n\n        ScanResult out = new ScanResult();\n        out.candidates = candidates;\n        out.armedCount = armedCount;\n        out.buyCount = buyCount;\n        out.buySymbols = buySymbols;\n        out.hot = armedCount > 0 || buyCount > 0;\n        out.status = buyCount > 0\n                ? "🚨 BUY CONFIRMED: " + join(buySymbols, " • ")\n                : armedCount > 0\n                ? "🎯 ARMED • انتظار التأكيد • " + armedCount + " مرشح"\n                : candidates.isEmpty()\n                ? "Hybrid V4.2.2 • مراقبة السوق"\n                : "🔎 Early Hunt • " + candidates.size() + " مرشح";\n        out.summary = formatSummary(candidates, armedCount, buyCount);\n        return out;\n'''
if old not in s:
    raise SystemExit('ScanResult/status anchor not found')
s = s.replace(old, new, 1)

old = '''        sb.append("🎯 Hybrid Explosion Radar V4.2\\n");\n        sb.append("Early Hunt يفحص جميع أزواج Binance Spot/USDT تقريبًا • تأكيد 15m لأفضل المرشحين.\\n");\n        sb.append("Early Hunt: ").append(candidates.size())\n                .append(" • ARMED: ").append(armedCount)\n                .append(" • BUY الآن: ").append(buyCount).append("\\n\\n");\n'''
new = '''        sb.append("🎯 Hybrid Explosion Radar V4.2.2\\n");\n        sb.append("Early Hunt يفحص جميع أزواج Binance Spot/USDT تقريبًا • تأكيد 15m لأفضل المرشحين.\\n");\n        sb.append("Early Hunt: ").append(candidates.size())\n                .append(" • ARMED: ").append(armedCount)\n                .append(" • BUY الآن: ").append(buyCount).append("\\n");\n        sb.append("⚠️ ARMED = انتظار التأكيد، وليس شراء.\\n");\n        if (buyCount > 0) {\n            sb.append("\\n🚨 BUY CONFIRMED NOW\\n");\n            for (Candidate c : candidates) {\n                if (!c.buyConfirmed) continue;\n                sb.append("✅ ").append(c.base).append("/USDT")\n                        .append(" • Entry ").append(fmt(c.entryLow)).append("–").append(fmt(c.entryHigh))\n                        .append(" • Invalidation ").append(fmt(c.invalidation))\n                        .append(" • Score ").append(String.format(Locale.US, "%.1f", c.totalScore))\n                        .append("\\n");\n            }\n        }\n        sb.append("\\n");\n'''
if old not in s:
    raise SystemExit('Summary header anchor not found')
s = s.replace(old, new, 1)

old = '''            sb.append(i + 1).append(". ").append(c.base).append("/USDT");\n            if (c.armed) sb.append("  🎯 ARMED"); else sb.append("  🔎");\n'''
new = '''            sb.append(i + 1).append(". ").append(c.base).append("/USDT");\n            if (c.buyConfirmed) sb.append("  🚨 BUY CONFIRMED");\n            else if (c.armed) sb.append("  🎯 ARMED — انتظار");\n            else sb.append("  🔎");\n'''
if old not in s:
    raise SystemExit('Candidate label anchor not found')
s = s.replace(old, new, 1)

old = '''        public int armedCount;\n        public int buyCount;\n        public List<Candidate> candidates = new ArrayList<>();\n'''
new = '''        public int armedCount;\n        public int buyCount;\n        public List<String> buySymbols = new ArrayList<>();\n        public List<Candidate> candidates = new ArrayList<>();\n'''
if old not in s:
    raise SystemExit('ScanResult fields anchor not found')
s = s.replace(old, new, 1)

old = '''        boolean catalystPositive;\n        boolean catalystNegative;\n        boolean armed;\n'''
new = '''        boolean catalystPositive;\n        boolean catalystNegative;\n        boolean armed;\n        boolean buyConfirmed;\n        double totalScore;\n        double entryLow;\n        double entryHigh;\n        double invalidation;\n'''
if old not in s:
    raise SystemExit('Candidate fields anchor not found')
s = s.replace(old, new, 1)

p.write_text(s, encoding='utf-8')

# --- MainActivity: version label and stronger candidate title. ---
p = base / 'MainActivity.java'
m = p.read_text(encoding='utf-8')
m = m.replace('Hybrid Explosion Radar V4.2.1', 'Hybrid Explosion Radar V4.2.2')
m = m.replace('📡 المرشحون الآن', '📡 المرشحون الآن • BUY يظهر أولاً')
p.write_text(m, encoding='utf-8')

print('V4.2.2 patch applied: explicit BUY symbols + BUY-first sorting + ARMED=wait')
