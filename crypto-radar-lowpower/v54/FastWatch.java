package com.wahshi.cryptoexplosionradar;
import android.app.*;
import android.content.*;
import org.json.*;
import java.net.*;
import java.io.*;
import java.util.*;
import javax.net.ssl.HttpsURLConnection;
public final class FastWatch {
 private static long retryAfter=0;
 private static JSONArray bars(String s,String interval,int count)throws Exception{
  JSONArray rows=new JSONArray(read("/api/v3/klines?symbol="+s+"&interval="+interval+"&limit="+count));
  JSONArray closed=new JSONArray(); long now=System.currentTimeMillis();
  for(int i=0;i<rows.length();i++)if(rows.getJSONArray(i).getLong(6)<now)closed.put(rows.getJSONArray(i));
  if(closed.length()<count-2 || now-closed.getJSONArray(closed.length()-1).getLong(6)> (interval.equals("1m")?90000:interval.equals("3m")?210000:330000))throw new IOException("stale bars");
  return closed;
 }
 public static void scan(Context c,SharedPreferences p){
  String s=FollowGate.active(p);long generation=p.getLong("follow_generation",0);
  if(!s.matches("[A-Z0-9]{2,20}USDT")||!p.getBoolean("radar_enabled",false))return;
  if(System.currentTimeMillis()<retryAfter)return;
  try{
   JSONArray one=bars(s,"1m",62);EntryWindow window=EntryWindow.fromClosed(one,System.currentTimeMillis());int n=one.length();JSONArray last=one.getJSONArray(n-1);
   double baseline=0,resistance=0;
   for(int i=n-21;i<n-1;i++){JSONArray b=one.getJSONArray(i);baseline+=b.getDouble(7);resistance=Math.max(resistance,b.getDouble(2));}
   baseline/=20;
   JSONArray trades=new JSONArray(read("/api/v3/aggTrades?symbol="+s+"&limit=1000"));
   double buy=0,total=0;long newest=0; Set<Long> ids=new HashSet<>();long now=System.currentTimeMillis();
   for(int i=0;i<trades.length();i++){JSONObject t=trades.getJSONObject(i);long ts=t.getLong("T");if(ts<now-60000||ts>now+5000||!ids.add(t.getLong("a")))continue;
    double q=t.getDouble("p")*t.getDouble("q");total+=q;if(!t.getBoolean("m"))buy+=q;newest=Math.max(newest,ts);}
   if(total<=0||now-newest>15000||baseline<=0)throw new IOException("no fresh trades");
   JSONObject depth=new JSONObject(read("/api/v3/depth?symbol="+s+"&limit=20"));
   JSONArray bids=depth.getJSONArray("bids"),asks=depth.getJSONArray("asks");
   double bid=bids.getJSONArray(0).getDouble(0),ask=asks.getJSONArray(0).getDouble(0),bq=0,aq=0;
   if(bid<=0||ask<bid)throw new IOException("invalid book");
   for(int i=0;i<bids.length();i++){JSONArray r=bids.getJSONArray(i);if(r.getDouble(0)>=bid*.998)bq+=r.getDouble(0)*r.getDouble(1);}
   for(int i=0;i<asks.length();i++){JSONArray r=asks.getJSONArray(i);if(r.getDouble(0)<=ask*1.002)aq+=r.getDouble(0)*r.getDouble(1);}
   JSONArray three=bars(s,"3m",4),five=bars(s,"5m",4);
   JSONArray b3=three.getJSONArray(three.length()-1),b5=five.getJSONArray(five.length()-1);
   double price=Double.parseDouble(new JSONObject(read("/api/v3/ticker/price?symbol="+s)).getString("price"));
   double change=(price/one.getJSONArray(n-5).getDouble(1)-1)*100;
   String state=WatchPolicy.state(price,resistance,change,last.getDouble(7)/baseline,buy/total,bq/(bq+aq),(ask-bid)/bid*10000,b3.getDouble(4)>b3.getDouble(1),b5.getDouble(4)>b5.getDouble(1));
   window.resistance=resistance;
   String guardReason=window.reason(price,System.currentTimeMillis());
   if(guardReason!=null)state="WAIT";
   double entry=Double.longBitsToDouble(p.getLong("follow_entry_"+s,0));
   if(entry>0){double stop=Double.longBitsToDouble(p.getLong("follow_stop_"+s,0));double low=Double.MAX_VALUE;for(int i=n-4;i<n;i++)low=Math.min(low,one.getJSONArray(i).getDouble(3));
    if(price>=entry*1.03)stop=Math.max(stop,Math.max(entry,low));
    synchronized(FollowGate.LOCK){if(!FollowGate.valid(p,s,generation))return;p.edit().putLong("follow_stop_"+s,Double.doubleToLongBits(stop)).apply();}
    if(price<=stop||"EXIT".equals(p.getString("fast_alert_state","")))state="EXIT";else state="HOLD";}
   String label=state.equals("BUY")?"🟢 شراء":state.equals("WATCH")?"🐋 مراقبة مبكرة":state.equals("TOO_LATE")?"🟡 انتظار — فات نطاق الدخول":state.equals("EXIT")?"🔴 خروج":state.equals("HOLD")?"🔵 احتفاظ":"🟡 انتظار";
   String detail="⭐ "+s+"\n"+label+"\nالسعر: "+price+(state.equals("BUY")?"\nنطاق الشراء: "+(resistance*1.001)+" – "+(window.entryHigh()):"");
   if(guardReason!=null&&entry<=0)detail+="\n"+guardReason;
   else if(entry<=0&&state.equals("WAIT"))detail+="\nشروط الحجم أو ضغط الشراء أو اتجاه 3 و5 دقائق غير مكتملة؛ الانتظار ليس بيعًا.";
   if(entry>0)detail+="\nسعر المتابعة: "+entry+"\nوقف الحماية: "+Double.longBitsToDouble(p.getLong("follow_stop_"+s,0));
   synchronized(FollowGate.LOCK){
    if(!FollowGate.valid(p,s,generation))return;
    String prev=p.getString("fast_alert_state","");long lastAlert=p.getLong("fast_alert_at",0);
    p.edit().putString("fast_state",state).putString("fast_detail",detail).putLong("fast_at",System.currentTimeMillis()).putLong("fast_buy_until",state.equals("BUY")?window.closedAt+75000:0).apply();
    if(state.equals("BUY")) OpportunityAlerts.send(c,p,s,price,resistance*1.001,window.entryHigh(),System.currentTimeMillis(),window.closedAt,window);
    if((state.equals("WATCH")||state.equals("EXIT"))&&!state.equals(prev)&&(state.equals("EXIT")||System.currentTimeMillis()-lastAlert>60000)){
     NotificationManager nm=(NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE);
     nm.createNotificationChannel(new NotificationChannel("selected_watch_v53","Selected coin Watch / Buy / Exit",NotificationManager.IMPORTANCE_HIGH));
     PendingIntent pi=PendingIntent.getActivity(c,FollowGate.ALERT,new Intent(c,MainActivity.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
     nm.notify(FollowGate.ALERT,new Notification.Builder(c,"selected_watch_v53").setSmallIcon(R.drawable.app_icon).setContentTitle(label+" — "+s).setContentText(detail).setStyle(new Notification.BigTextStyle().bigText(detail)).setContentIntent(pi).setAutoCancel(true).build());
     p.edit().putString("fast_alert_state",state).putLong("fast_alert_at",System.currentTimeMillis()).apply();
    }
   }
  }catch(Exception e){synchronized(FollowGate.LOCK){if(FollowGate.valid(p,s,generation))p.edit().putString("fast_state","DATA").putString("fast_detail","⭐ "+s+"\n⏳ تعذر تحديث البيانات؛ هذا ليس تنبيه بيع").apply();}}
 }
 private static String read(String path)throws Exception{
  HttpsURLConnection c=(HttpsURLConnection)new URL("https://api.binance.com"+path).openConnection();c.setConnectTimeout(2500);c.setReadTimeout(3000);
  try{int code=c.getResponseCode();if(code==429||code==418){long seconds=120;try{seconds=Math.max(seconds,Long.parseLong(c.getHeaderField("Retry-After")));}catch(Exception ignored){}retryAfter=System.currentTimeMillis()+seconds*1000;throw new IOException("rate limit");}if(code!=200)throw new IOException("HTTP "+code);
   try(BufferedReader r=new BufferedReader(new InputStreamReader(c.getInputStream(),"UTF-8"))){StringBuilder b=new StringBuilder();String line;while((line=r.readLine())!=null)b.append(line);return b.toString();}
  }finally{c.disconnect();}
 }
}
