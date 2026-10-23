package com.wahshi.cryptoexplosionradar;
import org.json.*;
import java.util.*;
/** Forward observations only. Missing windows stay missing; prices are not fills. */
public final class OutcomeLedger {
 private OutcomeLedger(){}
 static final int[] MINUTES={5,15,60};
 public static String observe(String json,String symbol,double price,long priceAt,long now){
  try{JSONArray a=new JSONArray(json);
   for(int i=0;i<a.length();i++){JSONObject e=a.getJSONObject(i);if(!symbol.equals(e.optString("symbol")))continue;
    for(int m:MINUTES){String k="m"+m;long due=e.getLong("at")+m*60000L;
     if(e.has(k))continue;
     if(price>0&&Double.isFinite(price)&&priceAt>=due&&priceAt<=due+90000&&priceAt<=now&&now-priceAt<=75000){
      e.put(k,100*(price/e.getDouble("price")-1)).put(k+"at",priceAt);
     }else if(now>due+165000)e.put(k,"missing");
    }
   }return a.toString();
  }catch(Exception ex){return json;}
 }
 public static String start(String json,String symbol,String state,double price,long now){
  if(!(price>0)||!Double.isFinite(price)||!(state.equals("BUY")||state.equals("WATCH")||state.equals("EARLY_WATCH")))return json;
  try{JSONArray a=new JSONArray(json),out=new JSONArray();
   for(int i=0;i<a.length();i++){JSONObject e=a.getJSONObject(i);
    if(symbol.equals(e.optString("symbol"))&&state.equals(e.optString("state"))&&now-e.getLong("at")<3600000)return json;
   }
   out.put(new JSONObject().put("symbol",symbol).put("state",state).put("price",price).put("at",now));
   for(int i=0;i<a.length()&&out.length()<1000;i++){JSONObject e=a.getJSONObject(i);if(now-e.getLong("at")<=7L*86400000)out.put(e);}
   return out.toString();
  }catch(Exception ex){return json;}
 }
 public static String report(String json,long now){
  StringBuilder b=new StringBuilder("رصد الماسح العام فقط؛ لا يشمل محرك المتابعة اليدوية. قياس مستقبلي من وقت التسجيل • آخر 1000 سجل / 7 أيام\nمرجع الشراء: عرض البيع وقت التأكيد؛ المراقبة: آخر إغلاق دقيقة. النتائج: إغلاق دقيقة بعد الموعد بـ0–90 ثانية. ليست صفقات أو أرباحًا صافية؛ لا تشمل الرسوم والانزلاق. لا تُملأ الفجوات بأسعار متأخرة.\n\n");
  try{JSONArray a=new JSONArray(json);
   for(String state:new String[]{"BUY","WATCH","EARLY_WATCH"}){b.append(state).append("\n");
    for(int m:MINUTES){int valid=0,missing=0,pending=0;double sum=0;String k="m"+m;
     for(int i=0;i<a.length();i++){JSONObject e=a.getJSONObject(i);if(!state.equals(e.optString("state")))continue;
      Object v=e.opt(k);if(v instanceof Number){valid++;sum+=((Number)v).doubleValue();}
      else if("missing".equals(v)||now>e.getLong("at")+m*60000L+165000)missing++;else pending++;
     }
     b.append(m).append(" دقيقة: ").append(valid==0?"لا نتائج":String.format(Locale.US,"متوسط %+.2f%% (%d)",sum/valid,valid)).append(" • مفقود ").append(missing).append(" • قيد الانتظار ").append(pending).append("\n");
    }
   }
   b.append("\nأحدث 30 رصدًا:\n");
   for(int i=0;i<Math.min(a.length(),30);i++){JSONObject e=a.getJSONObject(i);b.append(e.getString("symbol")).append(" ").append(e.getString("state")).append(" • ").append(new java.util.Date(e.getLong("at"))).append("\n");
    for(int m:MINUTES){Object v=e.opt("m"+m);b.append(m).append("m: ").append(v instanceof Number?String.format(Locale.US,"%+.2f%%",((Number)v).doubleValue()):now>e.getLong("at")+m*60000L+165000?"مفقود":"انتظار").append(" | ");}b.append("\n");
   }
  }catch(Exception ex){b.append("تعذر قراءة السجل");}return b.toString();
 }
}
