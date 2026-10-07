package com.wahshi.cryptoexplosionradar;
import android.content.SharedPreferences;
import org.json.JSONObject;
public final class SignalChecks {
 private static final Object LOCK=new Object();
 public static final class Result {public boolean buy;public double price;public long quoteAt,until;public String reason;}
 private static BuyStability.State read(SharedPreferences p,String symbol){
  BuyStability.State s=new BuyStability.State();
  try{JSONObject o=new JSONObject(p.getString("stable_signal_"+symbol,"{}"));s.phase=o.optString("phase","WAIT");s.reason=o.optString("reason","");s.firstAt=o.optLong("firstAt",0);s.until=o.optLong("until",0);s.cooldownUntil=o.optLong("cooldownUntil",0);}catch(Exception ignored){}
  return s;
 }
 private static BuyStability.State observe(SharedPreferences p,String symbol,boolean valid,long until,String reason){
  synchronized(LOCK){
   BuyStability.State s=BuyStability.observe(read(p,symbol),valid,until,System.currentTimeMillis(),reason);
   try{JSONObject o=new JSONObject();o.put("phase",s.phase).put("reason",s.reason).put("firstAt",s.firstAt).put("until",s.until).put("cooldownUntil",s.cooldownUntil);p.edit().putString("stable_signal_"+symbol,o.toString()).apply();}catch(Exception ignored){}
   return s;
  }
 }
 public static long expires(SharedPreferences p,String symbol){synchronized(LOCK){return read(p,symbol).until;}}
 public static String decorate(SharedPreferences p,String text){
  String[] blocks=text.split("\n────────────────\n\n",-1);
  java.util.regex.Pattern heading=java.util.regex.Pattern.compile("(?m)^(?:⭐ )?([A-Z0-9]+?)/?USDT\\s*$");
  for(int i=0;i<blocks.length;i++){
   if(!blocks[i].contains("🟢 شراء"))continue;
   java.util.regex.Matcher m=heading.matcher(blocks[i]);if(!m.find())continue;
   BuyStability.State s; synchronized(LOCK){s=read(p,m.group(1)+"USDT");}
   if(!s.phase.equals("BUY"))blocks[i]=blocks[i].replace("🟢 شراء","🟡 انتظار — "+s.reason);
  }
  return String.join("\n────────────────\n\n",blocks);
 }
 public static String reject(SharedPreferences p,String symbol,String reason){return observe(p,symbol,false,0,reason==null?"شروط الدخول لم تعد مكتملة":reason).reason;}
 public static Result confirm(SharedPreferences p,String symbol,EntryWindow window,double price,long quoteAt)throws Exception{
  Result out=new Result();out.price=price;out.quoteAt=quoteAt;
  long now=System.currentTimeMillis();String reason=window.reason(price,now);
  boolean valid=reason==null&&price>=window.entryLow()&&price<=window.entryHigh()&&now-quoteAt<=15000&&quoteAt<=now&&p.getBoolean("radar_enabled",false);
  BuyStability.State s=observe(p,symbol,valid,window.closedAt+75000,reason==null?"السعر خارج نطاق الدخول أو غير محدث":reason);
  if(s.phase.equals("PENDING")){
   long remaining=BuyStability.CONFIRM_MS-(System.currentTimeMillis()-s.firstAt);
   if(remaining>0)Thread.sleep(remaining);
   if(!p.getBoolean("radar_enabled",false)){out.reason=reject(p,symbol,"الرادار متوقف");return out;}
   try{
    JSONObject q=new JSONObject(MarketHttp.read("https://api.binance.com/api/v3/ticker/bookTicker?symbol="+symbol));
    double bid=q.getDouble("bidPrice"),ask=q.getDouble("askPrice");out.quoteAt=System.currentTimeMillis();
    if(!symbol.equals(q.getString("symbol"))||!Double.isFinite(bid+ask)||bid<=0||ask<bid)throw new Exception("سعر غير صالح");
    out.price=ask;reason=window.reason(ask,out.quoteAt);
    valid=reason==null&&ask>=window.entryLow()&&ask<=window.entryHigh()&&(ask-bid)/bid*10000<=15;
    s=observe(p,symbol,valid,window.closedAt+75000,reason==null?"فشل التحقق الثاني من نطاق السعر أو فارق السعر":reason);
   }catch(Exception ex){s=observe(p,symbol,false,0,"تعذر التحقق الثاني من السعر");}
  }
  out.buy=s.phase.equals("BUY")&&System.currentTimeMillis()<s.until;out.until=s.until;out.reason=s.reason;
  return out;
 }
}
