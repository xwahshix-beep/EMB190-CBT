from pathlib import Path
import re
base=Path('app/src/main/java/com/wahshi/cryptoexplosionradar')
p=base/'HybridEngine.java';s=p.read_text()
def replace(old,new):
 global s
 if s.count(old)!=1:raise SystemExit('Notification patch anchor: '+old)
 s=s.replace(old,new,1)
replace('                    c.invalidation = a.invalidation;', '''                    if (!BuyAlertPolicy.validRange(c.entryLow,c.entryHigh)) continue;
                    c.invalidation = a.invalidation;''')
replace('        int activeCount = 0;', '''        // Notify on active, recently confirmed signals across the scanned universe.
        // Revalidate the quote after slow optional market layers have completed.
        for (Candidate c : candidates) {
            long checked=System.currentTimeMillis();
            if(!prefs.getBoolean("radar_enabled",false))break;
            if(!c.liveSignal || !"ACTIVE".equals(c.liveState) || c.catalystNegative
                    || !BuyAlertPolicy.validRange(c.entryLow,c.entryHigh)
                    || checked-c.liveSignalAt>600000
                    || prefs.getLong("live_invalidated_at_"+c.symbol,0)>0)continue;
            long sent=prefs.getLong("buy_push_sent_"+c.symbol,0);
            if(sent>0 && checked-sent<BuyAlertPolicy.COOLDOWN)continue;
            try {
                long quoteAt=System.currentTimeMillis();
                JSONObject quote=new JSONObject(readEarlyUrl(API+"/api/v3/ticker/price?symbol="+c.symbol));
                c.lastPrice=Double.parseDouble(quote.getString("price"));
                classifyLiveState(c);
                classifyManagement(c);
                if("ACTIVE".equals(c.liveState))
                    OpportunityAlerts.send(context,prefs,c.symbol,c.lastPrice,c.entryLow,c.entryHigh,quoteAt,c.liveSignalAt);
            } catch(Exception ignored) { /* No alert from an unverified quote. */ }
        }

        int activeCount = 0;''')
replace('if (c.liveSignal && c.entryLow > 0 && c.entryHigh > 0) {','if (c.liveSignal && BuyAlertPolicy.validRange(c.entryLow,c.entryHigh)) {')
p.write_text(s)
p=base/'FastWatch.java';s=p.read_text()
s=s.replace('if((state.equals("BUY")||state.equals("WATCH")||state.equals("EXIT"))', '''if(state.equals("BUY")) OpportunityAlerts.send(c,p,s,price,resistance*1.001,resistance*1.008,System.currentTimeMillis(),System.currentTimeMillis());
    if((state.equals("WATCH")||state.equals("EXIT"))''')
p.write_text(s)
p=base/'MainActivity.java';s=p.read_text()
s=s.replace('Explosion Radar V5.3.1','Explosion Radar V5.3.2')
s=s.replace('تنبيهات Watch / Buy لهذه العملة فقط. إذا اشتريتها اختر سعر الشراء.','تنبيهات الشراء تشمل جميع العملات المفحوصة. اختر مراقبة لهذه العملة، أو سعر الشراء إذا اشتريتها.')
s=s.replace('            updateStartButton(true);','            updateStartButton(true);\n            showNotificationHint();')
s=s.replace('    private void updateStartButton(boolean running) {', '''    private void showNotificationHint() {
        android.app.NotificationManager nm=(android.app.NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        android.app.NotificationChannel ch=nm.getNotificationChannel(OpportunityAlerts.CHANNEL);
        if(nm.areNotificationsEnabled()&&(ch==null||ch.getImportance()!=0))return;
        new AlertDialog.Builder(this).setTitle("تفعيل إشعارات الشراء")
            .setMessage("اسمح بإشعارات التطبيق وقناة فرص الشراء المؤكدة حتى تصلك التنبيهات.")
            .setNegativeButton("لاحقًا",null)
            .setPositiveButton("الإعدادات",(d,w)->{
                Intent i=new Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS);
                i.putExtra(android.provider.Settings.EXTRA_APP_PACKAGE,getPackageName());startActivity(i);
            }).show();
    }

    private void updateStartButton(boolean running) {''')
p.write_text(s)
p=Path('app/src/main/AndroidManifest.xml');s=p.read_text()
if 'android.permission.VIBRATE' not in s:s=s.replace('<uses-permission android:name="android.permission.INTERNET" />','<uses-permission android:name="android.permission.INTERNET" />\n    <uses-permission android:name="android.permission.VIBRATE" />')
p.write_text(s)
p=Path('app/build.gradle');s=p.read_text();s=re.sub(r'versionCode\s+\d+','versionCode 55',s);s=re.sub(r'versionName\s+"[^"]+"','versionName "5.3.2-buy-alerts"',s);p.write_text(s)
print('V5.3.2 global confirmed BUY notifications; fresh quote, valid range, shared cooldown')
