package com.wahshi.cryptoexplosionradar;
import android.app.*;
import android.content.*;
import android.graphics.Color;
import java.util.Locale;
public final class OpportunityAlerts {
 public static final String CHANNEL="all_confirmed_buy_v532";
 private static final Object LOCK=new Object();
 public static boolean send(Context context,SharedPreferences prefs,String symbol,double price,double low,double high,long quoteAt,long signalAt,EntryWindow window){
  synchronized(LOCK){
   long checked=System.currentTimeMillis();
   if(window==null||window.reason(price,checked)!=null)return false;
   if(low<window.entryLow()-1e-10||high>window.entryHigh()+1e-10)return false;
   NotificationManager nm=(NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);
   NotificationChannel channel=new NotificationChannel(CHANNEL,"فرص الشراء المؤكدة",NotificationManager.IMPORTANCE_HIGH);
   channel.enableVibration(true);channel.setVibrationPattern(new long[]{0,220,100,320});nm.createNotificationChannel(channel);
   NotificationChannel actual=nm.getNotificationChannel(CHANNEL);
   boolean permitted=nm.areNotificationsEnabled()&&actual!=null&&actual.getImportance()!=NotificationManager.IMPORTANCE_NONE;
   String key="buy_push_sent_"+symbol;long now=System.currentTimeMillis();
   if(!BuyAlertPolicy.eligible(prefs.getBoolean("radar_enabled",false),permitted,true,price,low,high,now,quoteAt,signalAt,prefs.getLong(key,0)))return false;
   String body="نطاق الشراء: "+fmt(low)+" – "+fmt(high)+" USDT\nالسعر عند التنبيه: "+fmt(price)+"\nوقت الإشارة: "+new java.text.SimpleDateFormat("HH:mm:ss",Locale.getDefault()).format(new java.util.Date(signalAt))+"\nوقت الإرسال: "+new java.text.SimpleDateFormat("HH:mm:ss",Locale.getDefault()).format(new java.util.Date(now))+"\nهذه لقطة عند التأكيد؛ افتح السجل للاطلاع على آخر حالة وسبب أي إلغاء.";
   Intent open=new Intent(context,MainActivity.class).setAction("buy:"+symbol);
   PendingIntent pi=PendingIntent.getActivity(context,0,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
   Notification n=new Notification.Builder(context,CHANNEL).setSmallIcon(R.drawable.app_icon)
    .setContentTitle("🟢 فرصة شراء — "+symbol).setContentText(body).setStyle(new Notification.BigTextStyle().bigText(body))
    .setContentIntent(pi).setAutoCancel(true).setTimeoutAfter(Math.max(1,SignalChecks.expires(prefs,symbol)-now)).setColor(Color.rgb(89,220,135)).build();
   try{
    if(!prefs.getBoolean("radar_enabled",false)||!SignalChecks.isActive(prefs,symbol))return false;
    nm.notify("buy:"+symbol,32000,n);
    prefs.edit().putLong(key,now).apply();
    return true;
   }catch(SecurityException denied){return false;}
  }
 }
 private static String fmt(double x){return String.format(Locale.US,x>=1?"%.4f":"%.8f",x);}
}
