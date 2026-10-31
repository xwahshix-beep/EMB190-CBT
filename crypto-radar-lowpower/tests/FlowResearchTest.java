package com.wahshi.cryptoexplosionradar;
import org.json.*;
public class FlowResearchTest {
 static void check(boolean b,String s){if(!b)throw new AssertionError(s);}
 static FlowTransitionEngine.Result r(long c,double p){FlowTransitionEngine.Result r=new FlowTransitionEngine.Result();r.closedAt=c;r.price=p;r.state="WAIT";return r;}
 public static void main(String[] args)throws Exception{
  long close=1800000000000L+59999,now=close+10;FlowTransitionEngine.Result r=r(close,100);
  FlowTiming t=new FlowTiming(close,close+2,close+5,"WEBSOCKET");JSONObject timing=t.json(close,now);
  check(timing.getLong("sourceToReceiveMs")==3&&timing.getLong("decisionDelayMs")==5,"latency arithmetic");
  check(new FlowTiming(close,0,now,"REST").json(close,now).isNull("eventAt"),"REST event time unknown");
  check(t.json(close+60000,now+60000).isNull("receivedAt"),"wrong candle timing unavailable");
  check(new FlowTiming(close,now+100,now,"WEBSOCKET").json(close,now).getBoolean("clockSkew"),"clock skew visible");
  String s=FlowResearch.update("{}","BTCUSDT",r,"WAIT",now,timing);
  check(new JSONObject(s).getJSONArray("samples").length()==1,"negative control included");
  check(s.equals(FlowResearch.update(s,"BTCUSDT",r,"WAIT",now+10,timing)),"same candle immutable");
  check(s.equals(FlowResearch.update(s,"BTCUSDT",r,"WAIT",now+90000,timing)),"stale rejected");
  String sampled=s;r=r(close+120000,101);s=FlowResearch.update(s,"BTCUSDT",r,"WAIT",now+120000,timing);
  JSONObject first=new JSONObject(s).getJSONArray("samples").getJSONObject(0);
  check(Math.abs(first.getDouble("r1")-1)<1e-8,"forward observation");
  check(first.getLong("observedAt")==now&&first.getDouble("price")==100,"original preserved");
  r=r(close+600000,110);String missed=FlowResearch.update(sampled,"BTCUSDT",r,"WAIT",now+600000,timing);
  check(new JSONObject(missed).getJSONArray("samples").getJSONObject(0).getString("r5").equals("missing"),"missing not backfilled");
  r=r(close+60000,100);r.state="FLOW_READY";s=FlowResearch.update(sampled,"BTCUSDT",r,"WAIT",now+60000,timing);
  check(new JSONObject(s).getJSONArray("samples").length()==2,"event cohort included");
  check(new JSONObject(s).getJSONArray("samples").getJSONObject(1).getString("cohort").equals("transition"),"cohorts distinct");
  check(FlowResearch.report(s,now+60000).contains("FLOW_READY"),"report");
  System.out.println("FlowResearchTest passed: paired controls, immutable samples, missing windows, dedupe, stale rejection, timing and cohorts");
 }
}
