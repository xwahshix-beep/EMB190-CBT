from pathlib import Path
import re
base=Path('app/src/main/java/com/wahshi/cryptoexplosionradar')
def method(s, signature, replacement):
 start=s.index(signature); brace=s.index('{',start); depth=1;end=brace+1
 while depth:
  if s[end]=='{':depth+=1
  if s[end]=='}':depth-=1
  end+=1
 return s[:start]+replacement+s[end:]
p=base/'HybridEngine.java';s=p.read_text()
s=method(s,'    private static boolean maybeBuyAlert(','''    private static boolean maybeBuyAlert(Context context, SharedPreferences prefs, Candidate c, Analysis a, double totalScore) { return false; }''')
s=method(s,'    private static void maybeExitAlert(','''    private static void maybeExitAlert(Context context, SharedPreferences prefs, Candidate c) { }''')
p.write_text(s)
p=base/'CatalystEngine.java';s=p.read_text()
s=method(s,'    private static void post(','''    private static void post(Context context, int id, String title, String body) { }''')
p.write_text(s)
p=base/'ScannerService.java';s=p.read_text()
s=method(s,'    private void postWhaleNotification(','''    private void postWhaleNotification(String symbol, String title, String body) { }''')
s=s.replace('java.util.List<TradeManager.TradeState> followed = TradeManager.scan(this, prefs);','// Selected follow runs independently of discovery.')
s=s.replace('    @Override public void onCreate() {','''    private java.util.concurrent.ScheduledExecutorService fastWorker;
    @Override public void onCreate() {''')
s=s.replace('        scheduleNext(2_000L);','''        if(fastWorker==null || fastWorker.isShutdown()) {
            fastWorker=java.util.concurrent.Executors.newSingleThreadScheduledExecutor();
            fastWorker.scheduleWithFixedDelay(() -> {
                if(running) { FastWatch.scan(this,prefs); }
            },0,15,java.util.concurrent.TimeUnit.SECONDS);
        }
        scheduleNext(2_000L);''')
s=s.replace('        running = false;','        running = false;\n        if(fastWorker!=null)fastWorker.shutdownNow();')
s=s.replace('    @Override public void onDestroy() {','    @Override public void onDestroy() {\n        if(fastWorker!=null)fastWorker.shutdownNow();')
p.write_text(s)
p=base/'MainActivity.java';s=p.read_text().replace('Explosion Radar V5','Explosion Radar V5.3')
s=method(s,'    private void chooseBuyToFollow()', '''    private void chooseBuyToFollow() {
        EditText coin=new EditText(this);coin.setSingleLine(true);coin.setHint("BTCUSDT");
        new AlertDialog.Builder(this).setTitle("⭐ العملة المختارة").setMessage("تنبيهات Watch / Buy لهذه العملة فقط. إذا اشتريتها اختر تسجيل دخول.")
        .setView(coin).setNegativeButton("إلغاء",null)
        .setNeutralButton("تسجيل دخول",(d,w)->{String sym=coin.getText().toString().trim().toUpperCase(java.util.Locale.US).replace("/","");if(!sym.endsWith("USDT"))sym+="USDT";if(sym.matches("[A-Z0-9]{2,20}USDT"))askEntry(sym);})
        .setPositiveButton("مراقبة مبكرة",(d,w)->{String sym=coin.getText().toString().trim().toUpperCase(java.util.Locale.US).replace("/","");if(!sym.endsWith("USDT"))sym+="USDT";if(sym.matches("[A-Z0-9]{2,20}USDT"))FollowGate.select(this,prefs(),sym,0);}).show();
    }''')
s=method(s,'    private void startFollow(','''    private void startFollow(String symbol,double entry) {
        if(!Double.isFinite(entry)||entry<=0)return;
        FollowGate.select(this,prefs(),symbol,entry);
        new AlertDialog.Builder(this).setMessage("بدأت متابعة "+symbol+" فقط.\\nسعر الدخول: "+entry).setPositiveButton("حسنًا",null).show();
    }''')
s=method(s,'    private void stopFollow(','''    private void stopFollow(String symbol) { FollowGate.select(this,prefs(),"",0); }''')
s=s.replace('سيبدأ التطبيق بإرسال احتفاظ أو خروج لهذه العملة فقط.','سيبدأ التطبيق بمتابعة الصفقة وتنبيه الخروج لهذه العملة فقط.')
# Refresh selected coin view without overwriting discovery data.
s=s.replace('    private void restoreSavedState() {','''    private final android.os.Handler fastUiHandler=new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable fastUiRefresh=new Runnable(){public void run(){
        SharedPreferences p=prefs();String v=p.getString("fast_detail","");
        if(!FollowGate.active(p).isEmpty()&&!v.isEmpty()) {
            if(!p.getBoolean("radar_enabled",false)||System.currentTimeMillis()-p.getLong("fast_at",0)>60000) v="⭐ "+FollowGate.active(p)+"\\n🟡 انتظار — البيانات غير محدثة";
            details.setText(styleDetails(v));
        }
        fastUiHandler.postDelayed(this,2000);
    }};
    @Override protected void onResume(){super.onResume();fastUiHandler.removeCallbacks(fastUiRefresh);fastUiHandler.post(fastUiRefresh);}
    @Override protected void onPause(){fastUiHandler.removeCallbacks(fastUiRefresh);super.onPause();}
    private void restoreSavedState() {''')
p.write_text(s)
p=Path('app/build.gradle');s=p.read_text();s=re.sub(r'versionCode\s+\d+','versionCode 53',s);s=re.sub(r'versionName\s+"[^"]+"','versionName "5.3-early-watch"',s);p.write_text(s)
print('V5.3: isolated selected-coin monitor, exclusive notifications, fast confirmation, anti-chase')
