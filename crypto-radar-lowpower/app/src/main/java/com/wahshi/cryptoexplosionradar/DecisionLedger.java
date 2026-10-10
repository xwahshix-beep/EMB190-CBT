package com.wahshi.cryptoexplosionradar;
import java.util.*;
import org.json.*;
/** Audit trail is distinct from alerts: observed state never means executed trade. */
public final class DecisionLedger {
 private DecisionLedger(){}
 private static final int LIMIT=300;
 public static String append(String json,String symbol,String state,String reason,PreExplosionEngine.Result r,long now){
  try {
   JSONArray before=new JSONArray(json==null?"[]":json),out=new JSONArray();
   long candle=r==null?0:r.closedAt;
   JSONObject event=new JSONObject().put("symbol",symbol).put("state",state).put("reason",reason==null?"":reason)
     .put("observedAt",now).put("closedAt",candle)
     .put("score",r==null?0:r.score).put("audit",r==null?"No complete candles":r.audit());
   out.put(event);
   int count=1;
   for(int i=0;i<before.length()&&count<LIMIT;i++){
    JSONObject e=before.optJSONObject(i);if(e==null)continue;
    if(symbol.equals(e.optString("symbol"))&&candle==e.optLong("closedAt")&&state.equals(e.optString("state")))continue;
    if(now-e.optLong("observedAt")>24L*3600000)continue;
    out.put(e);count++;
   }return out.toString();
  } catch(Exception e){return json==null?"[]":json;}
 }
 public static String remember(String json,String symbol,PreExplosionEngine.Result r,long now) {
  if(r==null||!("EARLY_WATCH".equals(r.state)||"WATCH".equals(r.state)||"BUY".equals(r.state)||"TOO_LATE".equals(r.state)))return json==null?"{}":json;
  try {
   JSONObject book=new JSONObject(json==null?"{}":json);
   book.put(symbol,new JSONObject().put("symbol",symbol).put("state",r.state).put("reason",r.reason)
      .put("score",r.score).put("closedAt",r.closedAt).put("seenAt",now));
   Iterator<String> keys=book.keys();List<String> expired=new ArrayList<>();
   while(keys.hasNext()){String k=keys.next();JSONObject e=book.optJSONObject(k);
    if(e==null||now-e.optLong("seenAt")>2L*3600000)expired.add(k);
   }for(String k:expired)book.remove(k);
   return book.toString();
  }catch(Exception e){return json==null?"{}":json;}
 }
 public static String history(String json,int max){
  try{
   JSONArray a=new JSONArray(json==null?"[]":json);StringBuilder out=new StringBuilder();
   for(int i=0;i<a.length()&&i<max;i++){JSONObject x=a.optJSONObject(i);if(x==null)continue;
    out.append(x.optString("symbol")).append(" • ").append(x.optString("state")).append(" • ")
      .append(x.optInt("score")).append("/100\n").append(x.optString("reason"))
      .append("\n").append(x.optString("audit")).append("\n\n");
   }return out.length()==0?"لا توجد قرارات مسجلة بعد":out.toString();
  }catch(Exception e){return "سجل غير قابل للقراءة";}
 }
 public static String recent(String json,long now,int max){
  try{
   JSONObject book=new JSONObject(json==null?"{}":json);List<JSONObject> matches=new ArrayList<>();
   Iterator<String> keys=book.keys();
   while(keys.hasNext()){JSONObject e=book.optJSONObject(keys.next());
    if(e!=null&&now>=e.optLong("seenAt")&&now-e.optLong("seenAt")<=2L*3600000)matches.add(e);
   }
   matches.sort((a,b)->Long.compare(b.optLong("seenAt"),a.optLong("seenAt")));
   StringBuilder out=new StringBuilder();int n=Math.min(max,matches.size());
   for(int i=0;i<n;i++){JSONObject e=matches.get(i);
    out.append("• ").append(e.optString("symbol")).append(" | ").append(e.optString("state"))
      .append(" | Score ").append(e.optInt("score")).append(" | آخر رصد منذ ")
      .append((now-e.optLong("seenAt"))/60000).append(" دقيقة\n");
   }return out.toString();
  }catch(Exception e){return "";}
 }
}