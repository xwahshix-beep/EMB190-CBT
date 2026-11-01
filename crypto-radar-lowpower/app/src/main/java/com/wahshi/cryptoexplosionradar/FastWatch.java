package com.wahshi.cryptoexplosionradar;
import android.app.*;
import android.content.*;
public final class FastWatch {
 public static void scan(Context context,SharedPreferences prefs){
  final String symbol;final long generation;final double entry,stop;final boolean exitLatched;
  synchronized(FollowGate.LOCK){
   symbol=FollowGate.active(prefs);generation=prefs.getLong("follow_generation",0);
   if(!symbol.matches("[A-Z0-9]{2,20}USDT")||!prefs.getBoolean("radar_enabled",false))return;
   entry=Double.longBitsToDouble(prefs.getLong("follow_entry_"+symbol,0));
   stop=Double.longBitsToDouble(prefs.getLong("follow_stop_"+symbol,0));
   exitLatched="EXIT".equals(prefs.getString("fast_exit_state",""))||"EXIT".equals(prefs.getString("fast_alert_state",""));
  }
  FollowEngine.Result result=FollowEngine.load(symbol,entry,stop,exitLatched,
      path->MarketHttp.read("https://api.binance.com"+path),System::currentTimeMillis);
  long stableUntil=0;
  // Shared activation/cancellation state across market and selected-coin checks.
  if(entry<=0){
   synchronized(FollowGate.LOCK){if(!FollowGate.valid(prefs,symbol,generation))return;}
   if(result.state.equals("BUY")){
    try{
     SignalChecks.Result stable=SignalChecks.confirm(prefs,symbol,result.window,result.price,result.quoteAt,"selected");
     result.price=stable.price;result.quoteAt=stable.quoteAt;result.reason=stable.reason;stableUntil=stable.until;
     if(!stable.buy)result.state="WAIT";
    }catch(Exception ex){Diagnostics.event(prefs,"follow-confirm",ex.getClass().getSimpleName());result.state="WAIT";result.reason=SignalChecks.reject(prefs,symbol,"تعذر تأكيد السعر","selected");}
   }else result.reason=SignalChecks.reject(prefs,symbol,result.reason,"selected");
  }
  synchronized(FollowGate.LOCK){
   if(!FollowGate.valid(prefs,symbol,generation))return;
   long now=System.currentTimeMillis();
   String detail=result.detail(symbol,entry,now);
   if(result.price<=0){
    double oldPrice=Double.longBitsToDouble(prefs.getLong("fast_last_price",0));
    if(oldPrice>0)detail+="\nآخر سعر مستلم (غير محدث): "+oldPrice+"\nوقت السعر السابق: "+FollowEngine.time(prefs.getLong("fast_at",0));
   }
   SharedPreferences.Editor edit=prefs.edit().putString("fast_state",result.state)
       .putString("fast_detail",detail).putLong("fast_attempt_at",now)
       .putLong("fast_buy_until",result.state.equals("BUY")?stableUntil:0);
   if(result.price>0)edit.putLong("fast_at",result.quoteAt).putLong("fast_last_price",Double.doubleToLongBits(result.price));
   if(entry>0&&result.fresh(now))edit.putLong("follow_stop_"+symbol,Double.doubleToLongBits(result.stop));
   if(result.state.equals("EXIT"))edit.putString("fast_exit_state","EXIT");
   edit.apply();
   if(entry<=0)SignalChecks.cancelEnded(context,prefs,symbol);
   // Notification permission errors must not erase successful market data.
   try{
    if(result.state.equals("BUY"))OpportunityAlerts.send(context,prefs,symbol,result.price,result.window.entryLow(),result.window.entryHigh(),result.quoteAt,result.window.closedAt,result.window);
    String prev=prefs.getString("fast_alert_state","");long lastAlert=prefs.getLong("fast_alert_at",0);
    if((result.state.equals("WATCH")||result.state.equals("EARLY_WATCH")||result.state.equals("EXIT"))&&!result.state.equals(prev)
         &&(result.state.equals("EXIT")||now-lastAlert>60000)){
     NotificationManager nm=(NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);
     nm.createNotificationChannel(new NotificationChannel("selected_watch_v53","رصد تغيرات العملة المتابعة",NotificationManager.IMPORTANCE_HIGH));
     if(!nm.areNotificationsEnabled()||nm.getNotificationChannel("selected_watch_v53").getImportance()==0){Diagnostics.event(prefs,"notification","إشعارات المراقبة محظورة في Android");return;}
     PendingIntent pi=PendingIntent.getActivity(context,FollowGate.ALERT,new Intent(context,MainActivity.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
     String title="بحث غير مثبت — "+(result.state.equals("EXIT")?"رُصد تغير شرط المتابعة":"رُصد نشاط مبكر")+" — "+symbol;
     nm.notify(FollowGate.ALERT,new Notification.Builder(context,"selected_watch_v53").setSmallIcon(R.drawable.app_icon).setContentTitle(title).setContentText("رُصد تغير في حالة المتابعة؛ افتح التطبيق للوقت والسبب. ليس توصية بيع أو شراء.").setStyle(new Notification.BigTextStyle().bigText("رُصد تغير في حالة المتابعة عند "+Diagnostics.time(now)+"؛ افتح التطبيق للاطلاع على القياسات والسبب. ليس توصية بيع أو شراء.")).setContentIntent(pi).setAutoCancel(true).build());
     prefs.edit().putString("fast_alert_state",result.state).putLong("fast_alert_at",now).apply();
    }
   }catch(SecurityException denied){ Diagnostics.event(prefs,"notification","رفض Android إذن الإشعار"); }
  }
 }
}
