package com.wahshi.cryptoexplosionradar;
import android.content.Context;
import android.app.NotificationManager;
import android.service.notification.StatusBarNotification;
import java.io.*;
public final class ProductionGate {
 public static final String MESSAGE="تنبيهات الشراء معطلة — بحث غير مثبت، ليس توصية";
 public static boolean enabled(Context c){
  try(InputStream in=c.getAssets().open("research/scanner-long-summary.json")){
   ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[4096];int n;
   while((n=in.read(b))!=-1)out.write(b,0,n);
   return ProductionPolicy.enabled(out.toString("UTF-8"));
  }catch(Exception e){return false;}
 }
 public static void clearBlocked(Context c){
  if(enabled(c))return;
  NotificationManager nm=(NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE);
  if(nm==null)return;
  for(StatusBarNotification n:nm.getActiveNotifications())
   if(n.getId()==32000 && n.getTag()!=null && n.getTag().startsWith("buy:"))nm.cancel(n.getTag(),n.getId());
 }
}
