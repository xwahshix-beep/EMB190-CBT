package com.wahshi.cryptoexplosionradar;
import java.util.*;import org.json.*;
public final class MicroResearchTest {
 static final long START=1800000000000L;
 static void check(boolean b,String why){if(!b)throw new AssertionError(why);}
 static JSONArray side(double p,double q){return new JSONArray().put(new JSONArray().put(p).put(q));}
 static JSONObject root(MicroResearch m,long now)throws Exception{return new JSONObject(m.save(now));}
 static JSONObject frame(MicroResearch m,long now)throws Exception{return root(m,now).getJSONObject("latest").getJSONObject("XUSDT");}
 public static void main(String[] args)throws Exception{
  MicroResearch m=new MicroResearch("{}");m.select(Arrays.asList("XUSDT"),START);m.connected(START);
  for(int i=0;i<170;i++){long now=START+i*2000;double price=100;m.book("XUSDT",i+1,side(99.99,100),side(100.01,100),now);m.trade("XUSDT",i+1,now,now,price,1,i%2==0);}
  check(!frame(m,START+338000).getString("state").equals("WATCH"),"quiet tape is not signal");
  for(int i=170;i<195;i++){long now=START+i*2000;double p=100+(i-170)*.03;m.book("XUSDT",i+1,side(p-.01,100),side(p+.01,100),now);m.trade("XUSDT",i+1,now,now,p,8,false);}
  JSONObject f=frame(m,START+388000);check(f.getString("state").equals("WATCH"),"persistent flow confirms after ten seconds");
  long at=f.getLong("at");m.trade("XUSDT",195,at,at+2000,200,100,false);check(frame(m,at+2000).getLong("at")==at,"duplicate trade ignored");
  m.trade("XUSDT",196,at-10000,at+2000,200,100,false);check(frame(m,at+2000).getLong("at")==at,"out of order ignored");
  m.trade("XUSDT",196,at+60000,at+60000,100.75,8,false);check(frame(m,at+60000).getString("state").equals("BLOCKED"),"stale depth overrides weak-flow hysteresis immediately");
  check(root(m,at+60000).getJSONArray("events").length()>=3,"history persists after cancellation");
  m.book("XUSDT",1000,side(99,100),side(101,100),at+62000);m.trade("XUSDT",197,at+62000,at+62000,100.78,8,false);
  check(frame(m,at+62000).getString("guard").equals("WIDE_SPREAD"),"spread blocks");
  m.reset(at+64000);m.select(Arrays.asList("XUSDT"),at+64000);m.trade("XUSDT",1,at+66000,at+66000,100,1,false);
  check(frame(m,at+66000).getString("state").equals("WARMUP"),"reconnection restarts warmup");
  JSONObject sample=new JSONObject().put("symbol","XUSDT").put("at",START).put("status","PENDING_ENTRY").put("cohort","fixed_15m").put("micro","WATCH");
  MicroResearch paper=new MicroResearch(new JSONObject().put("paper",new JSONArray().put(sample)).toString());paper.select(Arrays.asList("XUSDT"),START);
  paper.book("XUSDT",1,side(99.99,100),side(100.01,100),START+500);check(root(paper,START+500).getJSONArray("paper").getJSONObject(0).getString("status").equals("PENDING_ENTRY"),"no same instant fill");
  paper.book("XUSDT",2,side(99.99,100),side(100.01,100),START+1000);
  paper.book("XUSDT",3,side(100.99,100),side(101.01,100),START+901000);
  JSONObject closed=root(paper,START+901000).getJSONArray("paper").getJSONObject(0);check(closed.getString("status").equals("CLOSED"),"forward exit");
  double expected=100*((100.99*.999)/(100.01*1.001)-1);check(Math.abs(closed.getDouble("netPct")-expected)<1e-8,"both fees and bid ask execution included");
  MicroResearch missing=new MicroResearch(new JSONObject().put("paper",new JSONArray().put(sample)).toString());missing.select(Arrays.asList("XUSDT"),START);missing.book("XUSDT",1,side(99.99,100),side(100.01,.01),START+1000);
  check(root(missing,START+11000).getJSONArray("paper").getJSONObject(0).getString("status").equals("MISSING_ENTRY"),"insufficient depth cannot fill, expires explicitly");
  MicroResearch audit=new MicroResearch("{}");for(int i=0;i<16;i++)audit.minute("YUSDT",START+i*60000,100+i*.3,START+i*60000+1);
  JSONObject move=root(audit,START+900001).getJSONArray("moves").getJSONObject(0);check(!move.getBoolean("captured")&&move.getString("reason").equals("OUTSIDE_TOP20"),"coverage miss explicit");
  MicroResearch gap=new MicroResearch("{}");for(int i=0;i<16;i++)if(i!=8)gap.minute("YUSDT",START+i*60000,100+i*.3,START+i*60000+1);
  check(root(gap,START+900001).getJSONArray("moves").length()==0,"no return across missing minutes");
  check(MicroResearch.report(paper.save(START+901000),START+901000).contains("0.10%"),"cost assumptions visible");
  System.out.println("MicroResearchTest passed: warmup, persistent flow, dedupe, stale guards, reconnection, forward fills, costs, missing depth, coverage and gaps");
 }
}
