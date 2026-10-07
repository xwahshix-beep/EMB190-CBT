package com.wahshi.cryptoexplosionradar;
import org.json.*;
import java.util.*;
import java.io.*;
/** Pure selected-coin pipeline. A missing confirmation must not hide a valid quote. */
public final class FollowEngine {
 public interface Gateway { String read(String path)throws Exception; }
 public interface Clock { long now(); }
 public static final class Result {
  public String state="DATA",reason="",stage="السعر";
  public double price,stop;
  public long quoteAt;
  public EntryWindow window;
  public boolean fresh(long now){return price>0&&quoteAt>0&&now>=quoteAt&&now-quoteAt<=15000;}
  public String detail(String symbol,double entry,long now){
   String label=state.equals("BUY")?"🟢 شراء":state.equals("WATCH")?"🐋 مراقبة مبكرة":state.equals("EXIT")?"🔴 خروج":state.equals("HOLD")?"🔵 احتفاظ":state.equals("DATA")?"⏳ تعذر تحديث السعر":"🟡 انتظار";
   String text="⭐ "+symbol+"\n"+label;
   if(price>0)text+="\n"+(fresh(now)?"السعر: ":"آخر سعر مستلم (غير محدث): ")+price+" USDT\nوقت السعر: "+time(quoteAt);
   if(state.equals("BUY")&&window!=null)text+="\nنطاق الشراء: "+window.entryLow()+" – "+window.entryHigh();
   if(entry>0)text+="\nسعر المتابعة: "+entry+"\nوقف الحماية: "+stop;
   if(!reason.isEmpty())text+="\n"+reason;
   return text+"\nآخر محاولة: "+time(now);
  }
 }
 static String time(long t){return new java.text.SimpleDateFormat("HH:mm:ss",Locale.getDefault()).format(new Date(t));}
 public static Result load(String symbol,double entry,double stop,boolean exitLatched,Gateway io,Clock clock){
  Result r=new Result();r.stop=stop;
  try{
   quote(r,symbol,entry,io,clock);
   r.state=entry>0?(exitLatched||r.price<=stop?"EXIT":"HOLD"):"WAIT";
   if(entry>0&&r.state.equals("EXIT")){r.reason="وصل السعر إلى وقف الحماية المسجل";return finish(r,clock);}
   r.stage="شموع الدقيقة";
   JSONArray raw=new JSONArray(io.read("/api/v3/klines?symbol="+symbol+"&interval=1m&limit=62"));
   r.window=EntryWindow.parse(raw,clock.now());
   JSONArray one=closed(raw,clock.now());int n=one.length();
   if(entry>0){
    if(clock.now()-r.window.closedAt>75000){r.reason="شموع الدقيقة متأخرة؛ وقف الحماية السابق باقٍ";return finish(r,clock);}
    if(!r.fresh(clock.now()))quote(r,symbol,entry,io,clock);
    double low=Double.MAX_VALUE;for(int i=n-4;i<n;i++)low=Math.min(low,one.getJSONArray(i).getDouble(3));
    if(r.price>=entry*1.03)r.stop=Math.max(stop,Math.max(entry,low));
    r.state=exitLatched||r.price<=r.stop?"EXIT":"HOLD";
    r.reason="متابعة السعر ووقف الحماية؛ لا تعتمد على توفر صفقات شراء جديدة";
    return finish(r,clock);
   }
   double baseline=0,resistance=0;
   for(int i=n-21;i<n-1;i++){JSONArray bar=one.getJSONArray(i);baseline+=bar.getDouble(7);resistance=Math.max(resistance,bar.getDouble(2));}
   baseline/=20;r.window.resistance=resistance;
   String guard=r.window.reason(r.price,clock.now());
   if(guard!=null){r.reason=guard;return finish(r,clock);}
   if(!Double.isFinite(baseline)||baseline<=0){r.reason="انتظار توفر حجم تداول كافٍ";return finish(r,clock);}
   r.stage="الصفقات الحديثة";
   JSONArray trades=new JSONArray(io.read("/api/v3/aggTrades?symbol="+symbol+"&limit=1000"));
   double buy=0,total=0;long newest=0,now=clock.now();Set<Long> ids=new HashSet<>();
   for(int i=0;i<trades.length();i++){
    JSONObject t=trades.getJSONObject(i);long ts=t.getLong("T");
    if(ts<now-60000||ts>now||!ids.add(t.getLong("a")))continue;
    double q=t.getDouble("p")*t.getDouble("q");if(!Double.isFinite(q)||q<=0)continue;
    total+=q;if(!t.getBoolean("m"))buy+=q;newest=Math.max(newest,ts);
   }
   if(total<=0||now-newest>15000){r.reason="انتظار صفقات أحدث لتأكيد ضغط الشراء؛ اتصال السعر يعمل";return finish(r,clock);}
   r.stage="دفتر الأوامر";
   JSONObject depth=new JSONObject(io.read("/api/v3/depth?symbol="+symbol+"&limit=20"));
   JSONArray bids=depth.getJSONArray("bids"),asks=depth.getJSONArray("asks");
   double bid=bids.getJSONArray(0).getDouble(0),ask=asks.getJSONArray(0).getDouble(0),bq=0,aq=0;
   if(!Double.isFinite(bid+ask)||bid<=0||ask<bid)throw new IOException("بيانات دفتر غير صالحة");
   for(int i=0;i<bids.length();i++){JSONArray row=bids.getJSONArray(i);if(row.getDouble(0)>=bid*.998)bq+=row.getDouble(0)*row.getDouble(1);}
   for(int i=0;i<asks.length();i++){JSONArray row=asks.getJSONArray(i);if(row.getDouble(0)<=ask*1.002)aq+=row.getDouble(0)*row.getDouble(1);}
   if(!Double.isFinite(bq+aq)||bq<0||aq<0||bq+aq<=0)throw new IOException("سيولة دفتر غير صالحة");
   r.stage="شموع 3 دقائق";JSONArray three=bars(io,symbol,"3m",210000,clock);
   r.stage="شموع 5 دقائق";JSONArray five=bars(io,symbol,"5m",330000,clock);
   JSONArray b3=three.getJSONArray(three.length()-1),b5=five.getJSONArray(five.length()-1);
   r.stage="السعر النهائي";quote(r,symbol,entry,io,clock);
   double change=(r.price/one.getJSONArray(n-5).getDouble(1)-1)*100;
   r.state=WatchPolicy.state(r.price,resistance,change,one.getJSONArray(n-1).getDouble(7)/baseline,buy/total,bq/(bq+aq),(ask-bid)/bid*10000,b3.getDouble(4)>b3.getDouble(1),b5.getDouble(4)>b5.getDouble(1));
   guard=r.window.reason(r.price,clock.now());
   if(guard!=null){r.state="WAIT";r.reason=guard;}
   else r.reason=r.state.equals("BUY")?"اكتملت شروط الاختراق والحجم وضغط الشراء":r.state.equals("TOO_LATE")?"فات نطاق الدخول":"شروط الدخول غير مكتملة؛ الانتظار ليس بيعًا";
  }catch(Exception e){
   // With a recent quote, keep the known protective stop usable even if candles fail.
   r.state=r.price>0?(entry>0?(exitLatched||r.price<=stop?"EXIT":"HOLD"):"WAIT"):"DATA";
   r.reason="تعذر تحديث "+r.stage+": "+error(e)+". ستتم إعادة المحاولة تلقائيًا";
  }
  return finish(r,clock);
 }
 private static Result finish(Result r,Clock clock){
  if(!r.fresh(clock.now())){r.state="DATA";if(r.price>0)r.reason="السعر المستلم أصبح قديمًا؛ لا توجد إشارة محدثة. "+r.reason;}
  return r;
 }
 private static void quote(Result r,String symbol,double entry,Gateway io,Clock clock)throws Exception{
  JSONObject q=new JSONObject(io.read("/api/v3/ticker/bookTicker?symbol="+symbol));
  double bid=q.getDouble("bidPrice"),ask=q.getDouble("askPrice");
  if(!symbol.equals(q.getString("symbol"))||!Double.isFinite(bid+ask)||bid<=0||ask<bid)throw new IOException("سعر غير صالح");
  r.price=entry>0?bid:ask;r.quoteAt=clock.now();
 }
 private static JSONArray closed(JSONArray raw,long now)throws Exception{
  JSONArray out=new JSONArray();for(int i=0;i<raw.length();i++){JSONArray row=raw.getJSONArray(i);if(row.getLong(6)<now)out.put(row);}return out;
 }
 private static JSONArray bars(Gateway io,String symbol,String interval,long maxAge,Clock clock)throws Exception{
  JSONArray rows=closed(new JSONArray(io.read("/api/v3/klines?symbol="+symbol+"&interval="+interval+"&limit=4")),clock.now());
  if(rows.length()<2||clock.now()-rows.getJSONArray(rows.length()-1).getLong(6)>maxAge)throw new IOException("شموع غير محدثة");return rows;
 }
 private static String error(Exception e){
  if(e instanceof java.net.SocketTimeoutException)return "انتهت مهلة الاتصال";
  if(e instanceof java.net.UnknownHostException)return "تعذر الوصول إلى خادم Binance";
  if(e instanceof JSONException)return "استجابة بيانات غير صالحة";
  String msg=e.getMessage();return msg==null?"فشل الاتصال أو البيانات":msg.substring(0,Math.min(120,msg.length()));
 }
}
