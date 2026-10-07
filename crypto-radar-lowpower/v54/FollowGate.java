package com.wahshi.cryptoexplosionradar;
import android.content.*;
import android.app.*;
public final class FollowGate {
 public static final Object LOCK=new Object();
 public static final int ALERT=31053;
 public static String active(SharedPreferences p){return p.getString("follow_symbols","");}
 public static void select(Context c,SharedPreferences p,String symbol,double entry){synchronized(LOCK){
  String old=active(p);
  NotificationManager nm=(NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE);
  for(String s:old.split(",")){ if(s.isEmpty())continue; nm.cancel(9000+Math.abs(s.hashCode()%800));nm.cancel(18000+Math.abs(s.hashCode()%1000));nm.cancel(26000+Math.abs(s.hashCode()%2000));nm.cancel(24000+Math.abs(s.hashCode()%2000)); }
  nm.cancel(ALERT);
  p.edit().putString("follow_symbols",symbol).putLong("follow_generation",p.getLong("follow_generation",0)+1)
   .putLong("follow_entry_"+symbol,Double.doubleToLongBits(entry)).putLong("follow_stop_"+symbol,Double.doubleToLongBits(entry*.97))
   .remove("fast_attempt_at").remove("fast_last_price").remove("fast_exit_state").remove("fast_at").remove("fast_buy_until").remove("fast_state").remove("fast_detail").remove("fast_alert_state").remove("follow_state_"+symbol).apply();
 }}
 public static boolean valid(SharedPreferences p,String symbol,long generation){return !symbol.isEmpty()&&symbol.equals(active(p))&&generation==p.getLong("follow_generation",0)&&p.getBoolean("radar_enabled",false);}
}
