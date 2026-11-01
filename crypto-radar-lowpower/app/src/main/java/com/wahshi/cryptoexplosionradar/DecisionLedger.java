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
     .put("schema_version",1).put("decision_at",now).put("received_at",JSONObject.NULL)
     .put("source_at",candle>0?candle:JSONObject.NULL).put("data_age",candle>0&&now>=candle?now-candle:JSONObject.NULL)
     .put("rules_version","pre-explosion-v6-frozen").put("event_type","DECISION")
     .put("observedAt",now).put("closedAt",candle)
     .put("score",r==null?0:r.score).put("audit",r==null?"No complete candles":r.audit());
   if(r!=null)event.put("vol",r.volumeAcceleration).put("trades",r.tradeAcceleration).put("z",r.tradeZ)
      .put("buyShare",r.buyShare).put("quote5",r.quote5).put("extension",r.extension);
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
 public static String operational(String json,String source,String state,String reason,long now,long monotonic,String build){
  try{
   JSONArray old=new JSONArray(json==null?"[]":json),out=new JSONArray();
   out.put(new JSONObject().put("schema_version",1).put("event_type","OPERATIONAL").put("source",source)
    .put("symbol",source.contains(":")?source.substring(source.indexOf(":")+1):"").put("state",state).put("reason",reason).put("decision_at",now).put("observedAt",now)
    .put("received_at",JSONObject.NULL).put("source_at",JSONObject.NULL).put("data_age",JSONObject.NULL)
    .put("monotonic_at",monotonic).put("build_version",build).put("rules_version",JSONObject.NULL).put("rules_baseline_commit","d27d3eb41f4784622e3ab7c6909783cac44f6ecf")
    .put("audit","مصدر: "+source+" • "+build).put("closedAt",0));
   for(int i=0;i<old.length()&&out.length()<LIMIT;i++){JSONObject e=old.optJSONObject(i);if(e!=null&&now-e.optLong("observedAt")<=86400000)out.put(e);}
   return out.toString();
  }catch(Exception e){throw new IllegalArgumentException("Invalid decision ledger",e);}
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
    if("OPERATIONAL".equals(x.optString("event_type"))){out.append(x.optLong("decision_at")).append(" ms UTC • ").append(x.optString("source")).append(" • ").append(x.optString("state")).append("\n").append(x.optString("reason")).append("\n\n");continue;}
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