package com.wahshi.cryptoexplosionradar;
import java.util.*;
import org.json.*;
/** Public market metadata only. Unknown, stale and failed responses fail closed. */
public final class TradingEligibility {
 public interface Reader {String read() throws Exception;}
 public interface Clock {long now();}
 public static final long TTL=60000,RETRY=30000;
 private final Reader reader;private final Clock clock;
 private Map<String,String> reasons=new HashMap<>();private long checkedAt=-1,retryAt;
 public TradingEligibility(Reader r,Clock c){reader=r;clock=c;}
 public synchronized String reason(String symbol){
  long now=clock.now();
  if(checkedAt<0||now<checkedAt||now-checkedAt>=TTL){
   if(now<retryAt)return "تعذر التحقق من قابلية التداول — انتظار إعادة المحاولة";
   reasons.clear();checkedAt=-1;
   try{
    long requested=now;JSONArray rows=new JSONObject(reader.read()).getJSONArray("symbols");
    Map<String,String> next=new HashMap<>();
    for(int i=0;i<rows.length();i++){
     JSONObject row=rows.getJSONObject(i);String s=row.getString("symbol"),status=row.optString("status","");
     String reason=!"TRADING".equals(status)?"التداول غير متاح: "+(status.isEmpty()?"حالة مجهولة":status):
       !row.optBoolean("isSpotTradingAllowed",false)?"التداول الفوري غير متاح":null;
     if(next.containsKey(s))throw new IllegalArgumentException("duplicate symbol");next.put(s,reason);
    }
    if(next.isEmpty())throw new IllegalArgumentException("empty metadata");
    if(clock.now()<requested||clock.now()-requested>=TTL)throw new IllegalArgumentException("stale response");
    reasons=next;checkedAt=requested;retryAt=0;
   }catch(Exception e){retryAt=clock.now()+RETRY;return "تعذر التحقق من قابلية التداول — بيانات غير صالحة أو اتصال متعذر";}
  }
  return reasons.containsKey(symbol)?reasons.get(symbol):"تعذر التحقق من قابلية التداول — الزوج غير موجود";
 }
 public synchronized long checkedAt(){return checkedAt;}
}
