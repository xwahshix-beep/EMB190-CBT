package com.wahshi.cryptoexplosionradar;
import org.json.*;
import java.util.*;
/** Latest confirmed entry per symbol, independent of candidate ranking and notifications. */
public final class SignalJournal {
 public static JSONObject record(JSONObject book,String symbol,long eventId,double price,double low,double high,long signalAt,long until,long now)throws Exception{
  if(!symbol.matches("[A-Z0-9]{2,20}USDT")||!Double.isFinite(price+low+high)||price<=0||low<=0||high<low||until<=now)throw new IllegalArgumentException("invalid signal");
  JSONObject row=book.optJSONObject(symbol);
  if(row==null||row.optLong("eventId")!=eventId){
   row=new JSONObject().put("symbol",symbol).put("eventId",eventId).put("price",price)
    .put("low",low).put("high",high).put("signalAt",signalAt).put("recordedAt",now).put("until",until);
   book.put(symbol,row);
  }
  // Preserve the original entry price/time; renew only the verified-data timestamp.
  row.put("until",until).put("checkedAt",now).put("state","BUY").put("reason","اكتملت شروط الشراء عند آخر فحص");
  return book;
 }
 public static JSONObject end(JSONObject book,String symbol,String state,String reason,long now)throws Exception{
  JSONObject row=book.optJSONObject(symbol);if(row==null)return book;
  if(!("BUY".equals(row.optString("state"))||"DATA".equals(row.optString("state"))))return book;
  row.put("state",state).put("reason",reason).put("endedAt",now);return book;
 }
 public static List<JSONObject> rows(JSONObject book){
  List<JSONObject> out=new ArrayList<>();Iterator<String> keys=book.keys();
  while(keys.hasNext()){JSONObject row=book.optJSONObject(keys.next());if(row!=null)out.add(row);}
  out.sort((a,b)->Long.compare(b.optLong("recordedAt"),a.optLong("recordedAt")));return out;
 }
 public static String state(JSONObject row,long now,boolean running){
  String state=row.optString("state");
  if(!state.equals("BUY"))return "⚪ "+row.optString("reason","انتهت الإشارة");
  if(now>=row.optLong("until"))return "⏳ جارٍ تحديث بيانات الإشارة؛ لم تُلغَ فنيًا";
  if(!running)return "⏸ الرادار متوقف؛ يلزم تحديث الحالة";
  if(now-row.optLong("checkedAt")>15000)return "⏳ يلزم تحديث الحالة؛ إشارة الشراء السابقة محفوظة";
  return "🟢 شراء مؤكّد عند آخر فحص؛ تحقق من السعر الحالي";
 }
 public static String render(JSONObject book,long now,boolean running){
  if(book.length()==0)return "لا توجد إشارات شراء محفوظة بعد. ستظهر هنا تلقائيًا عند تأكيدها، حتى إن لم يصل إشعار الهاتف.";
  StringBuilder out=new StringBuilder();
  for(JSONObject row:rows(book)){
   if(out.length()>0)out.append("\n────────────────\n\n");
   String symbol=row.optString("symbol");
   out.append(symbol.endsWith("USDT")?symbol.substring(0,symbol.length()-4)+"/USDT":symbol)
    .append("\n").append(state(row,now,running))
    .append("\nسعر الإشارة: ").append(fmt(row.optDouble("price")))
    .append(" USDT\nنطاق الدخول وقت الإشارة: ").append(fmt(row.optDouble("low"))).append(" – ").append(fmt(row.optDouble("high")))
    .append("\nوقت تأكيد الشراء: ").append(time(row.optLong("recordedAt")))
    .append("\nآخر تحقق: ").append(time(row.optLong("checkedAt")));
  }
  return out.toString();
 }
 private static String fmt(double value){return String.format(Locale.US,value>=1?"%.4f":"%.8f",value);}
 private static String time(long value){return new java.text.SimpleDateFormat("dd/MM HH:mm:ss",Locale.getDefault()).format(new Date(value));}
}
