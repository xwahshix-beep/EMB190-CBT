package com.wahshi.cryptoexplosionradar;
import org.json.*;import java.util.*;
/** Paired, forward paper observations with fixed controls, bounded storage and explicit missing data. */
public final class FlowResearch {
 public static final String KEY="flow_research_v1";
 public static final int LIMIT=1500;
 public static JSONObject frame(FlowTransitionEngine.Result r,String old,long now)throws Exception{
  return new JSONObject().put("rules",FlowTransitionEngine.RULES).put("state",r.state).put("baselineState",old).put("path",r.path).put("reason",r.reason)
   .put("closedAt",r.closedAt).put("observedAt",now).put("price",r.price).put("changeZ",r.changeZ).put("volumeRatio",r.volumeRatio).put("tradeRatio",r.tradeRatio)
   .put("buyShare",r.buyShare).put("persistence",r.persistence).put("response",r.response).put("extension",r.extension);
 }
 public static String update(String saved,String symbol,FlowTransitionEngine.Result r,String old,long now,JSONObject timing)throws Exception{
  JSONObject root=new JSONObject(saved);JSONObject latest=root.optJSONObject("latest");if(latest==null)latest=new JSONObject();
  JSONArray samples=root.optJSONArray("samples");if(samples==null)samples=new JSONArray();
  JSONObject previous=latest.optJSONObject(symbol);
  if(r.closedAt<=0||r.closedAt>now||now-r.closedAt>75000||r.state.equals("DATA"))return saved;
  if(previous!=null&&r.closedAt<=previous.optLong("closedAt"))return saved;
  JSONArray kept=new JSONArray();
  for(int i=0;i<samples.length();i++){
   JSONObject s=samples.getJSONObject(i);long observed=s.getLong("observedAt");
   if(now-observed>7L*86400000){root.put("evicted",root.optLong("evicted")+1);continue;}
   for(int m:new int[]{1,5,15}){String k="r"+m;long due=observed+m*60000L;if(s.has(k))continue;
    if(symbol.equals(s.getString("symbol"))&&r.closedAt>=due&&r.closedAt<=due+75000){s.put(k,100*(r.price/s.getDouble("price")-1)).put(k+"At",r.closedAt);}
    else if(now>due+150000)s.put(k,"missing");
   }
   kept.put(s);
  }
  JSONObject f=frame(r,old,now).put("symbol",symbol).put("timing",timing);
  long slot=r.closedAt/900000L,previousSlot=previous==null?-1:previous.optLong("sampleSlot",-1);
  boolean control=slot>previousSlot;
  boolean transition=r.candidate()&&(previous==null||!r.state.equals(previous.optString("state")));
  long lastEvent=previous==null?0:previous.optLong("eventAt");
  boolean event=transition&&now-lastEvent>=300000;
  if(control||event){JSONObject s=new JSONObject(f.toString()).put("cohort",control?"fixed_15m":"transition");kept.put(s);}
  while(kept.length()>LIMIT){kept.remove(0);root.put("evicted",root.optLong("evicted")+1);}
  f.put("sampleSlot",control?slot:previousSlot).put("eventAt",event?now:lastEvent);
  latest.put(symbol,f);
  List<String> expired=new ArrayList<>();Iterator<String> keys=latest.keys();while(keys.hasNext()){String k=keys.next();if(now-latest.getJSONObject(k).optLong("observedAt")>7L*86400000)expired.add(k);}for(String k:expired)latest.remove(k);
  root.put("schema","flow-research-v1").put("latest",latest).put("samples",kept).put("updatedAt",now);
  return root.toString();
 }
 static int priority(JSONObject f,long now){
  if(now-f.optLong("observedAt")>120000)return 0;
  String s=f.optString("state");return s.equals("FLOW_READY")?3:s.equals("EARLY_WATCH")?2:1;
 }
 public static String report(String saved,long now){
  StringBuilder b=new StringBuilder("محرك التحول وتتابع الشراء • بحث حي على دقائق مكتملة\nلا يولد أوامر أو تنبيهات شراء. FLOW_READY تعني اكتمال شروط تجريبية فقط.\n\n");
  try{
   JSONObject root=new JSONObject(saved),latest=root.optJSONObject("latest");JSONArray a=root.optJSONArray("samples");if(latest==null||a==null)return b+"شغّل الرادار لجمع البيانات؛ لا توجد نتائج بعد.";
   b.append("عملات مرصودة: ").append(latest.length()).append(" • عينات محفوظة: ").append(a.length()).append(" • محذوفة عند انتهاء المدة/السعة: ").append(root.optLong("evicted")).append("\n");
   b.append("مقارنة مقترنة من أول رصد متاح بكل ربع ساعة؛ تشمل حالات الانتظار. عينات الانتقال الإضافية لا تدخل المتوسط التالي.\n");
   for(String model:new String[]{"new","old"}){b.append(model.equals("new")?"\nالمحرك الجديد (EARLY_WATCH / FLOW_READY)\n":"\nالمحرك السابق (EARLY_WATCH / WATCH / BUY)\n");
    for(int m:new int[]{1,5,15}){int n=0,missing=0,pending=0;double sum=0;for(int i=0;i<a.length();i++){JSONObject s=a.getJSONObject(i);if(!"fixed_15m".equals(s.optString("cohort")))continue;String state=s.optString(model.equals("new")?"state":"baselineState");boolean positive=model.equals("new")?(state.equals("EARLY_WATCH")||state.equals("FLOW_READY")):(state.equals("EARLY_WATCH")||state.equals("WATCH")||state.equals("BUY"));if(!positive)continue;Object v=s.opt("r"+m);if(v instanceof Number){sum+=((Number)v).doubleValue();n++;}else if("missing".equals(v)||now>s.getLong("observedAt")+m*60000L+150000)missing++;else pending++;}
     b.append(m).append(" د: ").append(n==0?"لا نتائج":String.format(Locale.US,"%+.3f%% (%d)",sum/n,n)).append(" • مفقود ").append(missing).append(" • انتظار ").append(pending).append('\n');}
   }
   List<JSONObject> frames=new ArrayList<>();Iterator<String> it=latest.keys();while(it.hasNext())frames.add(latest.getJSONObject(it.next()));frames.sort((x,y)->{int rank=Integer.compare(priority(y,now),priority(x,now));return rank!=0?rank:Long.compare(y.optLong("observedAt"),x.optLong("observedAt"));});
   b.append("\nحتى 15 عملة؛ المرشحون الحديثون أولًا (راجع عمر الرصد):\n");for(int i=0;i<Math.min(15,frames.size());i++){JSONObject f=frames.get(i);JSONObject t=f.getJSONObject("timing");b.append(f.getString("symbol")).append(" • ").append(f.getString("state")).append(" • ").append(f.getString("path")).append(" • منذ ").append(Math.max(0,(now-f.getLong("observedAt"))/1000)).append(" ث\n").append(f.getString("reason")).append("\nعمر الشمعة عند القرار: ").append(t.optLong("candleAgeMs")).append(" ms • وصول البيانات→القرار: ").append(t.opt("decisionDelayMs")).append(" ms\n");}
   b.append("\nالمرجع إغلاق الدقيقة المتاح عند الرصد، وليس سعر تنفيذ. العوائد إجمالية قبل الرسوم والانزلاق، ولا تثبت أفضلية. الحد 1500 عينة / 7 أيام؛ صدّر دوريًا. توقيت المصدر غير المتاح يبقى null. لا يقيس دفتر الأوامر أو هوية المتداولين.");
  }catch(Exception e){b.append("تعذر قراءة بيانات المقارنة؛ لم تُستبدل بنتائج افتراضية.");}return b.toString();
 }
}
