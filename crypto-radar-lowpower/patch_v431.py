from pathlib import Path
import re

base = Path('app/src/main/java/com/wahshi/cryptoexplosionradar')
p = base / 'HybridEngine.java'
s = p.read_text(encoding='utf-8')

# Fire one high-priority EXIT push when a triggered trade reaches EXIT state.
old = '''            classifyManagement(c);\n\n            if ("INVALIDATED".equals(c.liveState) && invalidatedAt <= 0L) {\n'''
new = '''            classifyManagement(c);\n            if (c.tradeTriggered && "EXIT".equals(c.managementState)) {\n                maybeExitAlert(context, prefs, c);\n            }\n\n            if ("INVALIDATED".equals(c.liveState) && invalidatedAt <= 0L) {\n'''
if old not in s:
    raise SystemExit('V4.3.1 persisted management anchor not found')
s = s.replace(old, new, 1)

# applyPersistedLiveSignals needs Context for notifications.
s = s.replace(
    'applyPersistedLiveSignals(candidates, day, prefs, now);',
    'applyPersistedLiveSignals(context, candidates, day, prefs, now);',
    1
)
s = s.replace(
    'private static void applyPersistedLiveSignals(List<Candidate> candidates,',
    'private static void applyPersistedLiveSignals(Context context, List<Candidate> candidates,',
    1
)

# Add EXIT notification helper before the summary formatter.
anchor = '    private static String formatSummary(List<Candidate> candidates, int armedCount, int buyCount) {\n'
helper = r'''    private static void maybeExitAlert(Context context, SharedPreferences prefs, Candidate c) {
        String key = "hybrid_exit_" + c.symbol + "_" + c.liveSignalAt;
        if (prefs.getBoolean(key, false)) return;
        prefs.edit().putBoolean(key, true).apply();

        String title = "🔴 خروج — " + c.base + "/USDT";
        String body = "تم كسر مستوى الحماية • السعر " + fmt(c.lastPrice)
                + " • مستوى الخروج " + fmt(c.invalidation);

        Intent open = new Intent(context, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(context,
                20000 + Math.abs(c.symbol.hashCode() % 5000), open,
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
                .setColor(Color.rgb(255, 90, 90))
                .build();
        ((NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE))
                .notify(18000 + Math.abs(c.symbol.hashCode() % 1000), n);
    }

'''
if anchor not in s:
    raise SystemExit('V4.3.1 summary anchor not found')
s = s.replace(anchor, helper + anchor, 1)
p.write_text(s, encoding='utf-8')

# Version metadata.
gradle = Path('app/build.gradle')
g = gradle.read_text(encoding='utf-8')
g = re.sub(r'versionCode\s+\d+', 'versionCode 10', g, count=1)
g = re.sub(r'versionName\s+"[^"]+"', 'versionName "4.3.1-buy-exit-alerts"', g, count=1)
gradle.write_text(g, encoding='utf-8')

print('V4.3.1 applied: phone push notifications only for BUY and EXIT')
