package com.wahshi.cryptoexplosionradar;
import android.app.*;
import android.content.*;
import android.graphics.Color;
import java.util.Locale;
public final class OpportunityAlerts {
 public static final String CHANNEL="all_confirmed_buy_v532";
 private static final Object LOCK=new Object();
 public static boolean send(Context context,SharedPreferences prefs,String symbol,double price,double low,double high,long quoteAt,long signalAt,EntryWindow window){
  return send(context,prefs,symbol,price,low,high,quoteAt,signalAt,window,null);
 }
 public static boolean send(Context context,SharedPreferences prefs,String symbol,double price,double low,double high,long quoteAt,long signalAt,EntryWindow window,PreExplosionEngine.Result evidence){
  synchronized(FollowGate.LOCK){
   if(!BuyAlertPolicy.inScope(prefs.getBoolean("notify_all_buys",true),FollowGate.active(prefs),symbol)){prefs.edit().putString("push_status","مستبعد من نطاق التنبيهات: "+symbol).apply();return false;}
   long checked=System.currentTimeMillis();
   if(window==null||window.reason(price,checked)!=null)return false;
   if(low<window.entryLow()-1e-10||high>window.entryHigh()+1e-10)return false;
   NotificationManager nm=(NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);
   NotificationChannel channel=new NotificationChannel(CHANNEL,"فرص الشراء المؤكدة",NotificationManager.IMPORTANCE_HIGH);
   channel.enableVibration(true);channel.setVibrationPattern(new long[]{0,220,100,320});nm.createNotificationChannel(channel);
   NotificationChannel actual=nm.getNotificationChannel(CHANNEL);
   boolean permitted=nm.areNotificationsEnabled()&&actual!=null&&actual.getImportance()!=NotificationManager.IMPORTANCE_NONE;
   if(!permitted){prefs.edit().putString("push_status","إشعارات التطبيق أو القناة محظورة في إعدادات Android").apply();return false;}
   String key="buy_push_sent_"+symbol;long now=System.currentTimeMillis();
   if(!BuyAlertPolicy.eligible(prefs.getBoolean("radar_enabled",false),permitted,true,price,low,high,now,quoteAt,signalAt,prefs.getLong(key,0)))return false;
   String body="نطاق الشراء: "+fmt(low)+" – "+fmt(high)+" USDT\nالسعر عند التنبيه: "+fmt(price)+"\nوقت الإشارة: "+new java.text.SimpleDateFormat("HH:mm:ss",Locale.getDefault()).format(new java.util.Date(signalAt))+"\nوقت الإرسال: "+new java.text.SimpleDateFormat("HH:mm:ss",Locale.getDefault()).format(new java.util.Date(now))+"\nهذه لقطة عند التأكيد؛ افتح السجل للاطلاع على آخر حالة وسبب أي إلغاء.";
   body+="\n"+ResearchExport.explanation(evidence);
   Intent open=new Intent(context,MainActivity.class).setAction("buy:"+symbol);
   PendingIntent pi=PendingIntent.getActivity(context,0,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
   Notification n=new Notification.Builder(context,CHANNEL).setSmallIcon(R.drawable.app_icon)
    .setContentTitle("🟢 فرصة شراء — "+symbol).setContentText(body).setStyle(new Notification.BigTextStyle().bigText(body))
    .setContentIntent(pi).setAutoCancel(true).setTimeoutAfter(Math.max(1,SignalChecks.expires(prefs,symbol)-now)).setColor(Color.rgb(89,220,135)).build();
   try{
    if(!BuyAlertPolicy.inScope(prefs.getBoolean("notify_all_buys",true),FollowGate.active(prefs),symbol)||!prefs.getBoolean("radar_enabled",false)||!SignalChecks.isActive(prefs,symbol))return false;
    nm.notify("buy:"+symbol,32000,n);
    prefs.edit().putLong(key,now).putString("push_status","سُلّم إشعار "+symbol+" إلى Android عند "+new java.text.SimpleDateFormat("HH:mm:ss",Locale.getDefault()).format(new java.util.Date(now))).apply();
    try{prefs.edit().putString("v67_notifications",ResearchExport.notification(prefs.getString("v67_notifications","[]"),symbol,price,quoteAt,signalAt,now,body)).apply();}catch(Exception ignored){}
    return true;
   }catch(SecurityException denied){prefs.edit().putString("push_status","رفض Android إذن الإشعار").apply();return false;}
  }
 }
 public static String test(Context context){
  NotificationManager nm=(NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);
  NotificationChannel ch=new NotificationChannel(CHANNEL,"فرص الشراء المؤكدة",NotificationManager.IMPORTANCE_HIGH);ch.enableVibration(true);nm.createNotificationChannel(ch);
  if(!nm.areNotificationsEnabled()||nm.getNotificationChannel(CHANNEL).getImportance()==0)return "الإشعارات محظورة؛ افتح إعدادات إشعارات التطبيق";
  try{nm.notify("test",32001,new Notification.Builder(context,CHANNEL).setSmallIcon(R.drawable.app_icon).setContentTitle("اختبار الإشعارات — ليس إشارة تداول").setContentText("إذا ظهر هذا التنبيه فقناة الإشعارات تعمل. لا يختبر استمرار الفحص بالخلفية.").setAutoCancel(true).build());return "سُلّم التنبيه التجريبي إلى Android؛ تحقق من لوحة الإشعارات";}catch(SecurityException e){return "إذن الإشعارات غير ممنوح";}
 }
 private static String fmt(double x){return String.format(Locale.US,x>=1?"%.4f":"%.8f",x);}
}
