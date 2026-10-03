from pathlib import Path

p = Path('app/src/main/java/com/wahshi/cryptoexplosionradar/ScannerService.java')
s = p.read_text(encoding='utf-8')

field_anchor = '    private SharedPreferences prefs;\n'
fields = '''    private SharedPreferences prefs;\n    private volatile long lastWhaleScanAt = 0L;\n    private volatile List<WhaleSignal> cachedWhaleSignals = new ArrayList<>();\n    private volatile int lastWhaleScanned = 0;\n    private volatile int lastWhaleMapped = 0;\n    private volatile int lastWhaleOnChain = 0;\n'''
if field_anchor not in s:
    raise SystemExit('SharedPreferences anchor not found')
s = s.replace(field_anchor, fields, 1)

start = s.index('    private void runCycleSafe() {')
end = s.index('    private List<BinanceTicker> loadBinanceSpotTickers()', start)
method = r'''    private void runCycleSafe() {
        if (!running) return;
        final long CATALYST_INTERVAL_MS = 5 * 60_000L;
        long next = CATALYST_INTERVAL_MS;
        try {
            long now = System.currentTimeMillis();
            List<BinanceTicker> all = loadBinanceSpotTickers();

            boolean existingWhaleWatch = false;
            for (WhaleSignal w : cachedWhaleSignals) {
                if (w.score >= 4.25) { existingWhaleWatch = true; break; }
            }
            long whaleCadence = existingWhaleWatch ? WATCH_INTERVAL_MS : NORMAL_INTERVAL_MS;
            boolean whaleDue = cachedWhaleSignals.isEmpty() || lastWhaleScanAt == 0L || now - lastWhaleScanAt >= whaleCadence;

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
                        // Public RPC endpoints are best-effort; isolate failures per token.
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

            for (WhaleSignal w : signals) {
                if (!catalyst.combinedSymbols.contains(w.symbol)) maybeAlert(w);
            }

            String status;
            if (!catalyst.combinedSymbols.isEmpty()) status = "🐋⚡ Double Signal • حيتان + محفز";
            else if (catalyst.hot) status = "⚡ Catalyst Watch • حدث جديد";
            else if (whaleWatch) status = "🐋 Whale Watch • تجميع محتمل";
            else status = "Whale + Catalyst Radar • مراقبة هادئة";

            String whaleDetails = formatSignals(lastWhaleScanned, lastWhaleMapped, lastWhaleOnChain, signals, whaleCadence);
            long minsToWhale = Math.max(1L, (whaleCadence - Math.max(0L, System.currentTimeMillis() - lastWhaleScanAt)) / 60_000L);
            String details = whaleDetails
                    + "\n\n" + catalyst.summary
                    + "\n\n⚡ Catalyst scan: كل ~5 دقائق • 🐋 Whale scan التالي: ~" + minsToWhale + " دقيقة";
            sendUi(status, details);
            updateServiceNotification(status + " • Catalyst ~5m");
        } catch (Exception e) {
            next = 5 * 60_000L;
            sendUi("Whale + Catalyst Radar • خطأ مؤقت", "سيعيد المحاولة تلقائيًا.\n" + safe(e.getMessage()));
            updateServiceNotification("Whale + Catalyst Radar • إعادة محاولة ~5m");
        } finally {
            scheduleNext(next);
        }
    }

'''
s = s[:start] + method + s[end:]

# Background reliability: persist state, survive task removal, and allow restart receiver.
s = s.replace('import android.app.Notification;\n', 'import android.app.AlarmManager;\nimport android.app.Notification;\n', 1)
s = s.replace('import android.os.IBinder;\n', 'import android.os.IBinder;\nimport android.os.SystemClock;\n', 1)

s = s.replace(
    '        running = true;\n        startForeground(SERVICE_NOTIFICATION_ID, buildServiceNotification("Free Whale Radar يعمل • On-chain"));',
    '        running = true;\n        prefs.edit().putBoolean("radar_enabled", true).putBoolean("service_alive", true).apply();\n        startForeground(SERVICE_NOTIFICATION_ID, buildServiceNotification("Free Whale Radar يعمل • On-chain"));',
    1
)

s = s.replace(
    '        running = false;\n        sendUi("الرادار متوقف", "لن تتم مراقبة حركة الحيتان حتى تشغيله من جديد.");',
    '        running = false;\n        prefs.edit().putBoolean("radar_enabled", false).putBoolean("service_alive", false).apply();\n        sendUi("الرادار متوقف", "لن تتم مراقبة حركة الحيتان حتى تشغيله من جديد.");',
    1
)

old_send = '''    private void sendUi(String status, String details) {\n        Intent i = new Intent(ACTION_UI).setPackage(getPackageName());\n        i.putExtra("status", status);\n        i.putExtra("details", details);\n        sendBroadcast(i);\n    }\n'''
new_send = '''    private void sendUi(String status, String details) {\n        long now = System.currentTimeMillis();\n        if (prefs != null) {\n            prefs.edit()\n                    .putString("ui_status", status == null ? "" : status)\n                    .putString("ui_details", details == null ? "" : details)\n                    .putLong("ui_updated_at", now)\n                    .putBoolean("service_alive", running)\n                    .apply();\n        }\n        Intent i = new Intent(ACTION_UI).setPackage(getPackageName());\n        i.putExtra("status", status);\n        i.putExtra("details", details);\n        i.putExtra("updated_at", now);\n        sendBroadcast(i);\n    }\n'''
if old_send not in s:
    raise SystemExit('sendUi anchor not found')
s = s.replace(old_send, new_send, 1)

old_destroy = '''    @Override public void onDestroy() {\n        running = false;\n        executor.shutdownNow();\n        super.onDestroy();\n    }\n'''
new_destroy = '''    @Override public void onTaskRemoved(Intent rootIntent) {\n        scheduleSelfRestart();\n        super.onTaskRemoved(rootIntent);\n    }\n\n    private void scheduleSelfRestart() {\n        if (prefs == null || !prefs.getBoolean("radar_enabled", false)) return;\n        try {\n            Intent restart = new Intent(this, RestartReceiver.class).setAction(RestartReceiver.ACTION_RESTART);\n            PendingIntent pi = PendingIntent.getBroadcast(this, 811, restart,\n                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);\n            AlarmManager am = (AlarmManager) getSystemService(ALARM_SERVICE);\n            if (am != null) {\n                am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP,\n                        SystemClock.elapsedRealtime() + 15_000L, pi);\n            }\n        } catch (Exception ignored) {}\n    }\n\n    @Override public void onDestroy() {\n        running = false;\n        if (prefs != null) prefs.edit().putBoolean("service_alive", false).apply();\n        scheduleSelfRestart();\n        executor.shutdownNow();\n        super.onDestroy();\n    }\n'''
if old_destroy not in s:
    raise SystemExit('onDestroy anchor not found')
s = s.replace(old_destroy, new_destroy, 1)

s = s.replace('Free Whale Radar يعمل • On-chain', 'Whale + Catalyst Radar V4.1 يعمل', 1)
s = s.replace('🐋 Free Whale Radar يعمل', '🐋⚡ Whale + Catalyst Radar V4.1', 1)
s = s.replace('Free Whale Radar V3', 'Whale + Catalyst Radar V4.1')
p.write_text(s, encoding='utf-8')
print('V4.1 integration/background patch applied')
