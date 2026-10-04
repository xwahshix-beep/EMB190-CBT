from pathlib import Path
import re

base = Path('app/src/main/java/com/wahshi/cryptoexplosionradar')

# --- HybridEngine: extend live signal management and add a simple exit manager. ---
p = base / 'HybridEngine.java'
s = p.read_text(encoding='utf-8')

# Keep confirmed signals long enough to manage the move after entry.
s = s.replace(
    '    private static final long LIVE_SIGNAL_TTL_MS = 2L * 60L * 60L * 1000L;\n',
    '    private static final long LIVE_SIGNAL_TTL_MS = 6L * 60L * 60L * 1000L;\n',
    1
)

# Candidate management fields.
old = '''        boolean buyConfirmed;\n        boolean liveSignal;\n        String liveState = "";\n        long liveSignalAt;\n        double lastPrice;\n        double totalScore;\n'''
new = '''        boolean buyConfirmed;\n        boolean liveSignal;\n        boolean tradeTriggered;\n        String liveState = "";\n        String managementState = "";\n        long liveSignalAt;\n        double lastPrice;\n        double totalScore;\n        double target1;\n        double target2;\n        double managedStop;\n'''
if old not in s:
    raise SystemExit('V4.3 candidate field anchor not found')
s = s.replace(old, new, 1)

# A BUY becomes a managed trade only when the price is actually inside the entry range.
old = '''                    persistLiveSignal(prefs, c, now);\n                    classifyLiveState(c);\n                    if ("ACTIVE".equals(c.liveState)) {\n                        buyCount++;\n                        maybeBuyAlert(context, prefs, c, a, total);\n                    }\n'''
new = '''                    persistLiveSignal(prefs, c, now);\n                    classifyLiveState(c);\n                    if ("ACTIVE".equals(c.liveState)) {\n                        c.tradeTriggered = true;\n                        prefs.edit().putBoolean("live_triggered_" + c.symbol, true).apply();\n                        buyCount++;\n                        maybeBuyAlert(context, prefs, c, a, total);\n                    }\n                    classifyManagement(c);\n'''
if old not in s:
    raise SystemExit('V4.3 confirmed BUY anchor not found')
s = s.replace(old, new, 1)

# Restore whether a confirmed entry was ever actually active, then classify management state.
old = '''            c.lastPrice = d.last;\n            classifyLiveState(c);\n\n            if ("INVALIDATED".equals(c.liveState) && invalidatedAt <= 0L) {\n'''
new = '''            c.lastPrice = d.last;\n            c.tradeTriggered = prefs.getBoolean("live_triggered_" + symbol, false);\n            classifyLiveState(c);\n            if ("ACTIVE".equals(c.liveState) && !c.tradeTriggered) {\n                c.tradeTriggered = true;\n                prefs.edit().putBoolean("live_triggered_" + symbol, true).apply();\n            }\n            classifyManagement(c);\n\n            if ("INVALIDATED".equals(c.liveState) && invalidatedAt <= 0L) {\n'''
if old not in s:
    raise SystemExit('V4.3 persisted state anchor not found')
s = s.replace(old, new, 1)

# Clean the new persisted flag when a signal expires.
s = s.replace(
    '                .remove("live_invalidated_at_" + symbol)\n                .apply();',
    '                .remove("live_invalidated_at_" + symbol)\n                .remove("live_triggered_" + symbol)\n                .apply();',
    1
)

# Insert the simple R-multiple exit manager before livePriority.
anchor = '    private static int livePriority(Candidate c) {\n'
manager = r'''    private static void classifyManagement(Candidate c) {
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

'''
if anchor not in s:
    raise SystemExit('V4.3 livePriority anchor not found')
s = s.replace(anchor, manager + anchor, 1)

# Replace the V4.2.4 glance-first summary with equally simple entry + exit management.
start = s.index('    private static String formatSummary(List<Candidate> candidates, int armedCount, int buyCount) {')
end = s.index('    private static void ensureChannel(Context context) {', start)
new_summary = r'''    private static String formatSummary(List<Candidate> candidates, int armedCount, int buyCount) {
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

            if (!buyNow && !wait && !exit && !tp2 && !tp1 && !hold) continue;

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
                sb.append("🟡 انتظار\n");
                if (c.liveSignal && c.entryLow > 0 && c.entryHigh > 0) {
                    sb.append("نطاق الشراء: ")
                            .append(fmt(c.entryLow)).append(" – ").append(fmt(c.entryHigh));
                } else {
                    sb.append("نطاق الشراء: بانتظار التأكيد");
                }
            }
            shown++;
        }

        if (shown == 0) sb.append("لا توجد فرصة جاهزة الآن");
        return sb.toString();
    }

'''
s = s[:start] + new_summary + s[end:]
p.write_text(s, encoding='utf-8')

# --- ScannerService: persistent foreground notification is generic; actionable push remains BUY only. ---
p = base / 'ScannerService.java'
sc = p.read_text(encoding='utf-8')
sc = sc.replace(
    '            updateServiceNotification(hybrid.status + " • next ~" + (next / 60_000L) + "m");\n',
    '            updateServiceNotification("Explosion Radar يعمل • الفحص مستمر في الخلفية");\n',
    1
)
sc = sc.replace(
    '            updateServiceNotification("Hybrid V4.2 • إعادة محاولة ~5m");\n',
    '            updateServiceNotification("Explosion Radar يعمل • إعادة فحص تلقائية");\n',
    1
)
p.write_text(sc, encoding='utf-8')

# --- MainActivity: retain the clean design and color only actionable states. ---
p = base / 'MainActivity.java'
m = p.read_text(encoding='utf-8')
m = m.replace(
    '        styleAll(sp, "🟡 انتظار", Color.rgb(245, 183, 43), true);\n',
    '        styleAll(sp, "🟡 انتظار", Color.rgb(245, 183, 43), true);\n'
    '        styleAll(sp, "🔵 احتفاظ", Color.rgb(94, 168, 255), true);\n'
    '        styleAll(sp, "🟣 بيع جزئي", Color.rgb(190, 125, 255), true);\n'
    '        styleAll(sp, "💰 خذ ربحًا إضافيًا", Color.rgb(89, 220, 135), true);\n'
    '        styleAll(sp, "🔴 خروج", Color.rgb(255, 105, 105), true);\n',
    1
)
m = m.replace(
    '        styleAll(sp, "نطاق الشراء:", Color.rgb(160, 172, 190), false);\n',
    '        styleAll(sp, "نطاق الشراء:", Color.rgb(160, 172, 190), false);\n'
    '        styleAll(sp, "الهدف الأول:", Color.rgb(160, 172, 190), false);\n'
    '        styleAll(sp, "وقف الحماية الآن:", Color.rgb(160, 172, 190), false);\n'
    '        styleAll(sp, "كسر مستوى الحماية:", Color.rgb(160, 172, 190), false);\n',
    1
)
p.write_text(m, encoding='utf-8')

# Version metadata.
gradle = Path('app/build.gradle')
g = gradle.read_text(encoding='utf-8')
g = re.sub(r'versionCode\s+\d+', 'versionCode 9', g, count=1)
g = re.sub(r'versionName\s+"[^"]+"', 'versionName "4.3-exit-manager"', g, count=1)
gradle.write_text(g, encoding='utf-8')

print('V4.3 applied: BUY-only push + simple Exit Manager (HOLD/TP1/TP2/EXIT)')
