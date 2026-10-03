from pathlib import Path

p = Path('app/src/main/java/com/wahshi/cryptoexplosionradar/ScannerService.java')
s = p.read_text(encoding='utf-8')

# Imports needed for OS wake-up scheduling and bounded CPU wake lock.
s = s.replace('import android.app.Notification;\n', 'import android.app.AlarmManager;\nimport android.app.Notification;\n', 1)
s = s.replace('import android.os.Build;\n', 'import android.os.Build;\nimport android.os.PowerManager;\nimport android.os.SystemClock;\n', 1)

# Alarm action delivered directly back to the running foreground service.
s = s.replace('    public static final String ACTION_STOP = "com.wahshi.cryptoexplosionradar.STOP";\n',
              '    public static final String ACTION_STOP = "com.wahshi.cryptoexplosionradar.STOP";\n'
              '    public static final String ACTION_TICK = "com.wahshi.cryptoexplosionradar.TICK";\n', 1)

field_anchor = '    private SharedPreferences prefs;\n'
fields = '''    private SharedPreferences prefs;\n    private AlarmManager alarmManager;\n    private PendingIntent tickIntent;\n    private PowerManager.WakeLock cycleWakeLock;\n    private volatile boolean cycleInFlight = false;\n    private volatile long lastWhaleScanAt = 0L;\n    private volatile List<WhaleSignal> cachedWhaleSignals = new ArrayList<>();\n    private volatile int lastWhaleScanned = 0;\n    private volatile int lastWhaleMapped = 0;\n    private volatile int lastWhaleOnChain = 0;\n'''
if field_anchor not in s:
    raise SystemExit('SharedPreferences anchor not found')
s = s.replace(field_anchor, fields, 1)

old_oncreate = '''    @Override public void onCreate() {\n        super.onCreate();\n        prefs = getSharedPreferences("free_whale_radar_v3", MODE_PRIVATE);\n        createChannels();\n    }\n'''
new_oncreate = '''    @Override public void onCreate() {\n        super.onCreate();\n        prefs = getSharedPreferences("free_whale_radar_v3", MODE_PRIVATE);\n        createChannels();\n        alarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);\n        Intent tick = new Intent(this, ScannerService.class).setAction(ACTION_TICK);\n        tickIntent = PendingIntent.getService(this, 4202, tick, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);\n        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);\n        if (pm != null) {\n            cycleWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "WhaleCatalystRadar:scan");\n            cycleWakeLock.setReferenceCounted(false);\n        }\n    }\n'''
if old_oncreate not in s:
    raise SystemExit('onCreate anchor not found')
s = s.replace(old_oncreate, new_oncreate, 1)

old_startcmd = '''    @Override public int onStartCommand(Intent intent, int flags, int startId) {\n        String action = intent == null ? null : intent.getAction();\n        if (ACTION_STOP.equals(action)) {\n            stopScanner();\n            return START_NOT_STICKY;\n        }\n        startScanner();\n        return START_STICKY;\n    }\n'''
new_startcmd = '''    @Override public int onStartCommand(Intent intent, int flags, int startId) {\n        String action = intent == null ? null : intent.getAction();\n        if (ACTION_STOP.equals(action)) {\n            stopScanner();\n            return START_NOT_STICKY;\n        }\n        if (ACTION_TICK.equals(action)) {\n            if (!prefs.getBoolean("radar_enabled", false)) {\n                stopSelf();\n                return START_NOT_STICKY;\n            }\n            if (!running) startScanner();\n            triggerCycle();\n            return START_STICKY;\n        }\n        if (intent == null && !prefs.getBoolean("radar_enabled", false)) {\n            stopSelf();\n            return START_NOT_STICKY;\n        }\n        startScanner();\n        return START_STICKY;\n    }\n'''
if old_startcmd not in s:
    raise SystemExit('onStartCommand anchor not found')
s = s.replace(old_startcmd, new_startcmd, 1)

# Persist user-enabled state and use an OS wake-up alarm instead of an in-process timer.
s = s.replace(
    '        running = true;\n        startForeground(SERVICE_NOTIFICATION_ID, buildServiceNotification("Free Whale Radar يعمل • On-chain"));',
    '        running = true;\n        prefs.edit().putBoolean("radar_enabled", true).putBoolean("service_alive", true).apply();\n        startForeground(SERVICE_NOTIFICATION_ID, buildServiceNotification("Whale + Catalyst Radar V4.1 يعمل • Background wake"));',
    1
)

old_stop = '''    private synchronized void stopScanner() {\n        running = false;\n        sendUi("الرادار متوقف", "لن تتم مراقبة حركة الحيتان حتى تشغيله من جديد.");\n        if (Build.VERSION.SDK_INT >= 24) stopForeground(STOP_FOREGROUND_REMOVE); else stopForeground(true);\n        stopSelf();\n    }\n'''
new_stop = '''    private synchronized void stopScanner() {\n        running = false;\n        prefs.edit().putBoolean("radar_enabled", false).putBoolean("service_alive", false).apply();\n        if (alarmManager != null && tickIntent != null) alarmManager.cancel(tickIntent);\n        if (cycleWakeLock != null && cycleWakeLock.isHeld()) cycleWakeLock.release();\n        sendUi("الرادار متوقف", "تم إيقاف Whale + Catalyst Radar بواسطة المستخدم.");\n        if (Build.VERSION.SDK_INT >= 24) stopForeground(STOP_FOREGROUND_REMOVE); else stopForeground(true);\n        stopSelf();\n    }\n'''
if old_stop not in s:
    raise SystemExit('stopScanner anchor not found')
s = s.replace(old_stop, new_stop, 1)

old_schedule = '''    private void scheduleNext(long delayMs) {\n        if (!running) return;\n        executor.schedule(this::runCycleSafe, delayMs, TimeUnit.MILLISECONDS);\n    }\n'''
new_schedule = '''    private synchronized void scheduleNext(long delayMs) {\n        if (!running || alarmManager == null || tickIntent == null) return;\n        long when = SystemClock.elapsedRealtime() + Math.max(1_000L, delayMs);\n        alarmManager.cancel(tickIntent);\n        if (Build.VERSION.SDK_INT >= 23) {\n            alarmManager.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, when, tickIntent);\n        } else {\n            alarmManager.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, when, tickIntent);\n        }\n        prefs.edit().putLong("next_wake_elapsed", when).apply();\n    }\n\n    private synchronized void triggerCycle() {\n        if (!running || cycleInFlight) return;\n        cycleInFlight = true;\n        executor.execute(() -> {\n            try {\n                runCycleSafe();\n            } finally {\n                cycleInFlight = false;\n            }\n        });\n    }\n'''
if old_schedule not in s:
    raise SystemExit('scheduleNext anchor not found')
s = s.replace(old_schedule, new_schedule, 1)

start = s.index('    private void runCycleSafe() {')
end = s.index('    private List<BinanceTicker> loadBinanceSpotTickers()', start)
method = r'''    private void runCycleSafe() {
        if (!running) return;
        final long CATALYST_INTERVAL_MS = 5 * 60_000L;
        long next = CATALYST_INTERVAL_MS;
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
                    + "\n\n⚡ Catalyst scan: كل ~5 دقائق • 🐋 Whale scan التالي: ~" + minsToWhale + " دقيقة"
                    + "\n✅ Background wake mode: ACTIVE";
            prefs.edit().putLong("last_scan_at", System.currentTimeMillis()).apply();
            sendUi(status, details);
            updateServiceNotification(status + " • Background ACTIVE");
        } catch (Exception e) {
            next = 5 * 60_000L;
            sendUi("Whale + Catalyst Radar • خطأ مؤقت", "سيعيد المحاولة تلقائيًا.\n" + safe(e.getMessage()));
            updateServiceNotification("Whale + Catalyst Radar • إعادة محاولة ~5m");
        } finally {
            if (wakeHeld && cycleWakeLock != null && cycleWakeLock.isHeld()) cycleWakeLock.release();
            scheduleNext(next);
        }
    }

'''
s = s[:start] + method + s[end:]

old_send = '''    private void sendUi(String status, String details) {\n        Intent i = new Intent(ACTION_UI).setPackage(getPackageName());\n        i.putExtra("status", status);\n        i.putExtra("details", details);\n        sendBroadcast(i);\n    }\n'''
new_send = '''    private void sendUi(String status, String details) {\n        long now = System.currentTimeMillis();\n        if (prefs != null) {\n            prefs.edit()\n                    .putString("ui_status", safe(status))\n                    .putString("ui_details", safe(details))\n                    .putString("last_status", safe(status))\n                    .putString("last_details", safe(details))\n                    .putLong("ui_updated_at", now)\n                    .putLong("last_update_at", now)\n                    .putBoolean("service_alive", running)\n                    .apply();\n        }\n        Intent i = new Intent(ACTION_UI).setPackage(getPackageName());\n        i.putExtra("status", status);\n        i.putExtra("details", details);\n        i.putExtra("updated_at", now);\n        i.putExtra("last_scan_at", prefs == null ? 0L : prefs.getLong("last_scan_at", 0L));\n        sendBroadcast(i);\n    }\n'''
if old_send not in s:
    raise SystemExit('sendUi anchor not found')
s = s.replace(old_send, new_send, 1)

s = s.replace('Free Whale Radar يعمل • On-chain', 'Whale + Catalyst Radar V4.1 يعمل', 1)
s = s.replace('🐋 Free Whale Radar يعمل', '🐋⚡ Whale + Catalyst Radar V4.1', 1)
s = s.replace('Free Whale Radar V3', 'Whale + Catalyst Radar V4.1')
p.write_text(s, encoding='utf-8')
print('V4.1 integration + background wake patch applied')
