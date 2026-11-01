package com.wahshi.cryptoexplosionradar;
import android.content.SharedPreferences;
import org.json.*;
import java.text.SimpleDateFormat;
import java.util.*;
/** Bounded operational journal; no URLs, response bodies, credentials or stack traces. */
public final class Diagnostics {
 private static volatile SharedPreferences prefs;
 public static void init(SharedPreferences p){prefs=p;}
 public static synchronized void transport(String source,boolean ok,String reason){
  SharedPreferences p=prefs;if(p==null)return;long now=System.currentTimeMillis();
  SharedPreferences.Editor e=p.edit().putLong("health_attempt_"+source,now);
  if(ok)e.putLong("health_ok_"+source,now);
  else e.putLong("health_fail_"+source,now).putString("health_reason_"+source,reason);
  e.apply();if(!ok)event(p,source,reason);
 }
 public static void event(SharedPreferences p,String source,String reason){
  long now=System.currentTimeMillis();
  String state=source.startsWith("buy-blocked:")?(reason.equals(ProductionGate.MESSAGE)?"BLOCKED_PRODUCTION_GATE":"BLOCKED_EXECUTION"):source.contains("notification")?"DELIVERY_UNKNOWN":reason.contains("JSON")||reason.contains("تُستخرج")?"PARSE_ERROR":"DISCONNECTED";
  try { synchronized(DecisionLedger.class){
   p.edit().putString("v6_trace",DecisionLedger.operational(p.getString("v6_trace","[]"),source,state,reason,now,android.os.SystemClock.elapsedRealtime(),BuildConfig.VERSION_NAME)).apply();
  }}catch(Exception failure){android.util.Log.w("RadarData","decision journal unavailable");}
 }
 public static String overview(SharedPreferences p,long now){
  StringBuilder b=new StringBuilder("حالة مصادر HTTP — لا تثبت اكتمال كل العملات أو صحة المحتوى\n");
  String[] keys={"market","candles","price","depth","eligibility","announcements","listings","telegram"};
  String[] names={"ملخص السوق","الشموع (آخر طلب لأي رمز)","الأسعار","العمق HTTP","أهلية التداول","صفحة الإعلانات","صفحة الإدراجات","صفحة Telegram"};
  for(int i=0;i<keys.length;i++){
   String k=keys[i];long at=p.getLong("health_ok_"+k,0),failed=p.getLong("health_fail_"+k,0);
   b.append(names[i]).append(": ").append(DataHealth.state(at,failed,now,DataHealth.maxAge(k))).append("\nآخر نجاح: ").append(time(at)).append(" • آخر محاولة: ").append(time(p.getLong("health_attempt_"+k,0))).append("\n");
  }
  return b+"حد التقادم: دفتر 3 ثوانٍ؛ شموع 60 ثانية؛ أسعار 15 ثانية؛ سوق 120 ثانية؛ أهلية وإعلانات 20 دقيقة. هذه أعمار الاستلام المحلي، لا أعمار أحداث البورصة؛ لا يغير شروط صلاحية الإشارات. تدفقات WebSocket لها شاشة الرصد المبكر الخاصة بها.";
 }
 public static String journal(SharedPreferences p){
  return "سجل القرارات الموحد: آخر 300 حدث / 24 ساعة؛ ليس أرشيف دراسة كاملاً.\n\n"+DecisionLedger.history(p.getString("v6_trace","[]"),80);
 }
 public static String time(long at){return at<=0?"لم يُسجل":new SimpleDateFormat("yyyy-MM-dd HH:mm:ss",Locale.getDefault()).format(new Date(at));}
}
