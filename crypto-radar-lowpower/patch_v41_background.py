from pathlib import Path

p = Path('app/src/main/java/com/wahshi/cryptoexplosionradar/ScannerService.java')
s = p.read_text(encoding='utf-8')

# Imports for wake-up scheduling and short CPU wake lock.
s = s.replace('import android.app.PendingIntent;\n', 'import android.app.PendingIntent;\nimport android.app.AlarmManager;\n')
s = s.replace('import android.os.IBinder;\n', 'import android.os.IBinder;\nimport android.os.PowerManager;\nimport android.os.SystemClock;\n')

# Wake action used by AlarmManager receiver.
s = s.replace(
    '    public static final String ACTION_UI = "com.wahshi.cryptoexplosionradar.UI";\n',
    '    public static final String ACTION_UI = "com.wahshi.cryptoexplosionradar.UI";\n'
    '    public static final String ACTION_WAKE = "com.wahshi.cryptoexplosionradar.WAKE";\n'
)

# Prevent overlapping network scans if Samsung/Android delivers more than one wake event.
anchor = '    private volatile int lastWhaleOnChain = 0;\n'
if anchor not in s:
    raise SystemExit('V4 state-field anchor not found')
s = s.replace(anchor, anchor + '    private volatile boolean cycleBusy = false;\n', 1)

# Replace service command handling.
start = s.index('    @Override public int onStartCommand(Intent intent, int flags, int startId) {')
end = s.index('    private synchronized void startScanner() {', start)
new_cmd = '''    @Override public int onStartCommand(Intent intent, int flags, int startId) {\n        String action = intent == null ? null : intent.getAction();\n        if (ACTION_STOP.equals(action)) {\n            stopScanner();\n            return START_NOT_STICKY;\n        }\n\n        if (ACTION_WAKE.equals(action)) {\n            if (!prefs.getBoolean("radar_enabled", false)) {\n                stopSelf();\n                return START_NOT_STICKY;\n            }\n            if (!running) {\n                running = true;\n                startForeground(SERVICE_NOTIFICATION_ID, buildServiceNotification("Whale + Catalyst Radar V4.1 • استعادة الخلفية"));\n            }\n            kickCycle();\n            return START_STICKY;\n        }\n\n        // ACTION_START and sticky restarts both restore the user-enabled radar.\n        if (intent == null && !prefs.getBoolean("radar_enabled", false)) {\n            stopSelf();\n            return START_NOT_STICKY;\n        }\n        startScanner();\n        return START_STICKY;\n    }\n\n'''
s = s[:start] + new_cmd + s[end:]

# Replace start/stop/scheduling with AlarmManager wake-up scheduling.
start = s.index('    private synchronized void startScanner() {')
end = s.index('    private synchronized void stopScanner() {', start)
new_start = '''    private synchronized void startScanner() {\n        prefs.edit().putBoolean("radar_enabled", true).apply();\n        if (running) {\n            updateServiceNotification("Whale + Catalyst Radar V4.1 • الخلفية فعالة");\n            return;\n        }\n        running = true;\n        startForeground(SERVICE_NOTIFICATION_ID, buildServiceNotification("Whale + Catalyst Radar V4.1 يعمل • Background wake enabled"));\n        sendUi("🐋⚡ Whale + Catalyst Radar V4.1 يعمل", "تم تفعيل مراقبة الخلفية. أول فحص خلال ثوانٍ…");\n        scheduleNext(2_000L);\n    }\n\n'''
s = s[:start] + new_start + s[end:]

start = s.index('    private synchronized void stopScanner() {')
end = s.index('    private void scheduleNext(long delayMs) {', start)
new_stop = '''    private synchronized void stopScanner() {\n        prefs.edit().putBoolean("radar_enabled", false).apply();\n        running = false;\n        cancelNextWake();\n        sendUi("الرادار متوقف", "لن تتم مراقبة الحيتان أو المحفزات حتى تشغيله من جديد.");\n        if (Build.VERSION.SDK_INT >= 24) stopForeground(STOP_FOREGROUND_REMOVE); else stopForeground(true);\n        stopSelf();\n    }\n\n'''
s = s[:start] + new_stop + s[end:]

start = s.index('    private void scheduleNext(long delayMs) {')
end = s.index('    private void runCycleSafe() {', start)
new_sched = '''    private PendingIntent wakePendingIntent() {\n        Intent wake = new Intent(this, WakeReceiver.class).setAction(ACTION_WAKE);\n        return PendingIntent.getBroadcast(this, 4102, wake, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);\n    }\n\n    private void scheduleNext(long delayMs) {\n        if (!running) return;\n        AlarmManager am = (AlarmManager) getSystemService(ALARM_SERVICE);\n        long trigger = SystemClock.elapsedRealtime() + Math.max(2_000L, delayMs);\n        am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, trigger, wakePendingIntent());\n        prefs.edit().putLong("next_wake_elapsed", trigger).apply();\n    }\n\n    private void cancelNextWake() {\n        try {\n            AlarmManager am = (AlarmManager) getSystemService(ALARM_SERVICE);\n            am.cancel(wakePendingIntent());\n        } catch (Exception ignored) {}\n    }\n\n    private void kickCycle() {\n        synchronized (this) {\n            if (!running || cycleBusy) return;\n            cycleBusy = true;\n        }\n        executor.execute(this::runCycleWithWakeLock);\n    }\n\n    private void runCycleWithWakeLock() {\n        PowerManager.WakeLock wl = null;\n        try {\n            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);\n            wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "WhaleRadar:scan");\n            wl.acquire(3 * 60_000L);\n            runCycleSafe();\n        } finally {\n            try { if (wl != null && wl.isHeld()) wl.release(); } catch (Exception ignored) {}\n            cycleBusy = false;\n        }\n    }\n\n'''
s = s[:start] + new_sched + s[end:]

# Persist successful scan time before broadcasting status.
needle = '            sendUi(status, details);\n            updateServiceNotification(status + " • Catalyst ~5m");'
replacement = '            prefs.edit().putLong("last_scan_at", System.currentTimeMillis()).apply();\n            sendUi(status, details);\n            updateServiceNotification(status + " • Catalyst ~5m • BG V4.1");'
if needle not in s:
    raise SystemExit('V4 success UI anchor not found')
s = s.replace(needle, replacement, 1)

# Persist every UI state so reopening the Activity shows the real background state.
start = s.index('    private void sendUi(String status, String details) {')
end = s.index('    private boolean eligibleBinanceSymbol', start)
new_send = '''    private void sendUi(String status, String details) {\n        long now = System.currentTimeMillis();\n        prefs.edit()\n                .putString("last_status", status == null ? "" : status)\n                .putString("last_details", details == null ? "" : details)\n                .putLong("last_update_at", now)\n                .apply();\n        Intent i = new Intent(ACTION_UI).setPackage(getPackageName());\n        i.putExtra("status", status);\n        i.putExtra("details", details);\n        i.putExtra("last_scan_at", prefs.getLong("last_scan_at", 0L));\n        sendBroadcast(i);\n    }\n\n'''
s = s[:start] + new_send + s[end:]

# Branding in persistent notification.
s = s.replace('setContentTitle("Free Whale Radar V3")', 'setContentTitle("Whale + Catalyst Radar V4.1")')
s = s.replace('FreeWhaleRadarV3/3.0', 'WhaleCatalystRadarV4.1/4.1')

p.write_text(s, encoding='utf-8')
print('V4.1 background reliability patch applied')
