package com.wahshi.cryptoexplosionradar;
import org.json.*;
import java.util.*;
/** Allowlisted CSV only. Never exports preferences or secrets wholesale. */
public final class ResearchExport {
 private ResearchExport(){}
 private static final String[] KEYS={"rules","engine","sampleMax","sampleMin","sampleCount","maxGapMs","lastSampleAt","symbol","state","observedAt","closedAt","price","score","vol","trades","z","buyShare","quote5","extension","reason","audit","m5","m15","m60","quoteAt","signalAt"};
 static String cell(Object value){
  if(value==null||value==JSONObject.NULL)return "\"\"";
  String s=String.valueOf(value);
  // Do not let untrusted textual reasons become spreadsheet formulas. Numeric losses stay numeric.
  if(!(value instanceof Number)&&!s.trim().isEmpty()&&"=+@-".indexOf(s.trim().charAt(0))>=0)s="'"+s;
  return "\""+s.replace("\"","\"\"")+"\"";
 }
 public static String csv(String decisions,String outcomes,String notifications)throws Exception{
  StringBuilder b=new StringBuilder("\ufeffrecord_type,schema");for(String k:KEYS)b.append(',').append(k);b.append("\r\n");
  append(b,new JSONArray(decisions),"decision");append(b,new JSONArray(outcomes),"price_observation");append(b,new JSONArray(notifications),"notification_submitted");return b.toString();
 }
 private static void append(StringBuilder b,JSONArray rows,String type)throws Exception{
  for(int i=0;i<rows.length();i++){
   JSONObject e=rows.getJSONObject(i);b.append(cell(type)).append(",\"radar-audit-v1\"");
   for(String k:KEYS){Object v=e.opt(k);if(k.equals("observedAt")&&v==null)v=e.opt("at");b.append(',').append(cell(v));}b.append("\r\n");
  }
 }
 public static String notification(String saved,String symbol,double price,long quoteAt,long signalAt,long now,String reason)throws Exception{
  JSONArray before=new JSONArray(saved),out=new JSONArray();
  out.put(new JSONObject().put("symbol",symbol).put("state","SUBMITTED_TO_ANDROID").put("price",price).put("quoteAt",quoteAt).put("signalAt",signalAt).put("observedAt",now).put("reason",reason));
  for(int i=0;i<before.length()&&out.length()<500;i++){JSONObject e=before.getJSONObject(i);if(now-e.getLong("observedAt")<=7L*86400000)out.put(e);}return out.toString();
 }
 public static String explanation(PreExplosionEngine.Result r){
  if(r==null)return "تفاصيل تدفق الصفقات غير متاحة لهذه الإشارة";
  return String.format(Locale.US,"شروط المحرك: حجم %.2fx (≥2.5)؛ صفقات %.2fx (≥1.8) أو Z %.2f (≥2.5)\nشراء مبادر %.1f%% (≥55%%)؛ تداول 5د %.0f USDT (≥25000)\nامتداد الاختراق %.2f%% (0.10–0.60%%)؛ تغير 5د %.2f%% (0–1.50%%)",r.volumeAcceleration,r.tradeAcceleration,r.tradeZ,r.buyShare*100,r.quote5,r.extension*100,r.change5*100);
 }
}
