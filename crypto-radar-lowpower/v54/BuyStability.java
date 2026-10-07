package com.wahshi.cryptoexplosionradar;
/** Debounces entry activation; never keeps an invalid BUY visible. */
public final class BuyStability {
 public static final long CONFIRM_MS=3000,MIN_REMAINING_MS=30000,COOLDOWN_MS=60000;
 public static final class State {public String phase="WAIT",reason="";public long firstAt,until,cooldownUntil;}
 public static State observe(State s,boolean valid,long proposedUntil,long now,String reason){
  if(s==null)s=new State();
  if(s.phase.equals("BUY")&&now>=s.until){s.phase="EXPIRED";s.reason="انتهت صلاحية فرصة الشراء";s.cooldownUntil=now+COOLDOWN_MS;}
  if(now<s.cooldownUntil)return s;
  if(!valid){
   if(s.phase.equals("BUY")){s.phase="CANCELLED";s.cooldownUntil=now+COOLDOWN_MS;s.reason="أُلغيت فرصة الدخول: "+reason;}
   else {s.phase="WAIT";s.reason=reason;s.firstAt=0;}
   return s;
  }
  if(s.phase.equals("BUY")){s.until=Math.min(s.until,proposedUntil);return s;}
  if(proposedUntil-now<MIN_REMAINING_MS){s.phase="WAIT";s.reason="تأكيد الشمعة قارب الانتهاء؛ انتظار تأكيد جديد";s.firstAt=0;return s;}
  if(!s.phase.equals("PENDING")||now-s.firstAt>15000||now<s.firstAt){s.phase="PENDING";s.firstAt=now;s.until=proposedUntil;s.reason="جارٍ التحقق الثاني من السعر";return s;}
  s.until=Math.min(s.until,proposedUntil);
  if(s.until-now<MIN_REMAINING_MS){s.phase="WAIT";s.reason="تأكيد الشمعة قارب الانتهاء";s.firstAt=0;return s;}
  if(now-s.firstAt>=CONFIRM_MS){s.phase="BUY";s.reason="اجتاز تأكيد السعر مرتين";}
  return s;
 }
}
