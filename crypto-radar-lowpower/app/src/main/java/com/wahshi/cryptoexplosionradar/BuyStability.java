package com.wahshi.cryptoexplosionradar;
/** Data freshness is separate from technical cancellation and position exits. */
public final class BuyStability {
 public static final long CONFIRM_MS=0,COOLDOWN_MS=60000,FRESH_MS=45000;
 public static final class State {public String phase="WAIT",reason="";public long firstAt,until,cooldownUntil,weakSince;public boolean established;}
 public static boolean acceptsOwner(String owner,String source,State s){return owner.isEmpty()||owner.equals(source)||!(s.established||s.phase.equals("PENDING"));}
 public static boolean dataIssue(String reason){
  return reason!=null&&(reason.contains("بيانات")||reason.contains("تعذر")||reason.contains("محدثة")||reason.contains("صلاحية")||reason.contains("متوقف"));
 }
 public static State observe(State s,boolean valid,long proposedUntil,long now,String reason){
  if(s==null)s=new State();
  if(s.phase.equals("BUY")&&now>=s.until){s.phase="DATA";s.reason="جارٍ تحديث بيانات الإشارة؛ لم تُلغَ فنيًا";}
  if(now<s.cooldownUntil)return s;
  if(!valid){
   if(dataIssue(reason)){s.phase="DATA";s.reason="جارٍ تحديث البيانات: "+reason;return s;}
   boolean hard=reason!=null&&(reason.contains("فات نطاق")||reason.contains("وقف")||reason.contains("فارق السعر")||reason.contains("خارج نطاق")||reason.contains("نطاق السعر"));
   if(s.established&&!hard){
    if(s.weakSince==0)s.weakSince=now;
    if(now-s.weakSince<30000){s.phase="RECHECK";s.reason="إعادة تحقق من ضعف الشروط؛ الإشارة السابقة محفوظة";return s;}
   }
   if(s.established){s.phase="CANCELLED";s.cooldownUntil=now+COOLDOWN_MS;s.reason="أُلغيت فرصة الدخول: "+reason;s.established=false;}
   else {s.phase="WAIT";s.reason=reason;s.firstAt=0;}
   return s;
  }
  s.weakSince=0;
  if(s.established){s.phase="BUY";s.until=now+FRESH_MS;s.reason="الشروط متحققة عند آخر فحص";return s;}
  if(!s.phase.equals("PENDING")||now-s.firstAt>15000||now<s.firstAt){s.phase="PENDING";s.firstAt=now;s.until=now+FRESH_MS;s.reason="جارٍ التحقق الثاني من السعر";return s;}
  s.phase="BUY";s.established=true;s.until=now+FRESH_MS;s.reason="اجتاز تحققَي السعر";return s;
 }
}
