package com.wahshi.cryptoexplosionradar;
import org.json.*;
import java.io.*;
import java.util.*;
public final class FollowEngineTest {
 static int tests; static final long NOW=1800000010000L;
 static void check(boolean ok,String name){tests++;if(!ok)throw new AssertionError(name);}
 static final class Fixture implements FollowEngine.Gateway,FollowEngine.Clock {
  long now=NOW;String fail="",mode="normal";List<String> requests=new ArrayList<>();
  public long now(){return now;}
  public String read(String path)throws Exception {
   requests.add(path);
   if(!fail.isEmpty()&&path.contains(fail)){if(mode.equals("slow"))now+=20000;throw new java.net.SocketTimeoutException();}
   if(path.contains("bookTicker"))return new JSONObject().put("symbol","ETCUSDT").put("bidPrice","100.19").put("askPrice",mode.equals("invalid")?"0":"100.20").toString();
   if(path.contains("klines")){
    long interval=path.contains("interval=3m")?180000:path.contains("interval=5m")?300000:60000;
    int count=interval==60000?130:3;long end=now/interval*interval;
    if(mode.equals("stale")&&interval==60000)end-=180000;
    JSONArray rows=new JSONArray();
    for(int i=0;i<count;i++){
     long open=end-(count-i)*interval;boolean last=i==count-1;
     double close=last?100.2:100,high=last?100.3:100;
     rows.put(new JSONArray().put(open).put(100).put(high).put(99.9).put(close).put(1).put(open+interval-1).put(i>=count-5?8000:1000).put(i>=count-5?80:10).put(1).put(mode.equals("quiet")?0:(i>=count-5?5200:600)).put(0));
    }
    return rows.toString();
   }
   if(path.contains("aggTrades")){
    if(mode.equals("quiet"))return "[]";
    long ts=mode.equals("oldTrades")?now-20000:now-1000;
    return new JSONArray().put(new JSONObject().put("a",1).put("T",ts).put("p",100).put("q",7).put("m",false))
      .put(new JSONObject().put("a",2).put("T",ts).put("p",100).put("q",3).put("m",true)).toString();
   }
   if(path.contains("depth"))return "{\"bids\":[[\"100.19\",\"100\"]],\"asks\":[[\"100.20\",\"100\"]]}";
   throw new IOException("unexpected path "+path);
  }
 }
 static FollowEngine.Result run(Fixture f,double entry,double stop){return FollowEngine.load("ETCUSDT",entry,stop,false,f,f);}
 public static void main(String[] args){
  Fixture f=new Fixture();FollowEngine.Result r=run(f,0,0);
  check(r.state.equals("BUY")&&r.price==100.2,"healthy complete input still BUY: "+r.reason);
  f=new Fixture();f.mode="quiet";r=run(f,0,0);
  check(r.state.equals("EARLY_WATCH")&&r.fresh(f.now)&&r.reason.contains("buyer confirmation pending"),"accelerating activity without buyer flow is EARLY_WATCH, never BUY");
  f=new Fixture();f.fail="depth";r=run(f,0,0);
  check(r.state.equals("WAIT")&&r.price>0&&r.reason.contains("دفتر الأوامر"),"depth failure retains price and cause");
  f=new Fixture();f.fail="bookTicker";r=run(f,0,0);
  check(r.state.equals("DATA")&&r.quoteAt==0&&r.reason.contains("مهلة"),"quote failure must not fake freshness");
  f=new Fixture();f.fail="klines";r=run(f,100,97);
  check(r.state.equals("HOLD")&&r.stop==97&&r.reason.contains("شموع الدقيقة"),"existing stop survives candle failure");
  f=new Fixture();f.fail="klines";r=run(f,110,106.7);
  check(r.state.equals("EXIT")&&f.requests.size()==1,"stop breach independent of candle/flow requests");
  f=new Fixture();f.fail="aggTrades";r=run(f,100,97);
  check(r.state.equals("HOLD")&&f.requests.stream().noneMatch(x->x.contains("aggTrades")),"positions do not depend on entry flow");
  f=new Fixture();f.mode="slow";f.fail="depth";r=run(f,0,0);
  check(r.state.equals("DATA")&&!r.fresh(f.now),"slow optional fetch cannot leave fresh BUY");
  f=new Fixture();f.mode="invalid";r=run(f,0,0);
  check(r.state.equals("DATA")&&r.price==0,"invalid book rejected");
  f=new Fixture();f.mode="stale";r=run(f,0,0);
  check(r.state.equals("DATA")&&r.price>0,"stale candles cannot BUY, price retained");
  f=new Fixture();f.mode="stale";f.fail="depth";r=run(f,0,0);
  check(r.state.equals("DATA")&&f.requests.stream().noneMatch(x->x.contains("depth")),"stale history rejected before order book");
  String error="ETCUSDT\nتعذر تحديث دفتر الأوامر";
  check(FollowView.render("ETCUSDT",error,NOW,NOW,true).equals(error),"UI keeps specific failure without masking it by missing quote time");
  check(!FollowView.render("ETCUSDT","🟢 شراء",NOW-70000,NOW,true).contains("🟢 شراء"),"old view cannot show active BUY");
  check(FollowView.render("ETCUSDT",error,NOW,NOW,false).contains("متوقف"),"stopped scanner not displayed as live");
  System.out.println("FollowEngine: "+tests+" tests passed");
 }
}
