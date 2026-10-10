package com.wahshi.cryptoexplosionradar;
import java.util.*;
public class PreExplosionTest {
 static void check(boolean b,String msg){if(!b)throw new AssertionError(msg);}
 public static void main(String[] args){
  List<PreExplosionEngine.Bar> bars=new ArrayList<>();long t=HistoricalReplay.START;
  for(int i=0;i<125;i++)bars.add(new PreExplosionEngine.Bar(t+i*60000,100,100.1,99.9,100,i<120?1000:8000,i<120?10:80,i<120?500:5200));
  bars.set(124,new PreExplosionEngine.Bar(t+124*60000,100,100.4,100,100.3,8000,80,5200));
  PreExplosionEngine.Result before=PreExplosionEngine.evaluate(bars,124);check(before.state.equals("BUY"),before.detail());
  check(Math.abs(before.cvd5-12000)<0.01,"Incorrect CVD quote delta");
  check(before.liquidityShift>0,"Buyer-side flow shift should be positive");
  for(int i=120;i<125;i++)bars.get(i).buy=3200;
  check(PreExplosionEngine.evaluate(bars,124).state.equals("EARLY_WATCH"),"activity without buyer confirmation must remain early watch");
  for(int i=120;i<125;i++)bars.get(i).trades=10;
  check(PreExplosionEngine.evaluate(bars,124).state.equals("WAIT"),"volume alone must not create early watch");
  for(int i=120;i<125;i++){bars.get(i).trades=80;bars.get(i).buy=5200;}
  bars.get(124).high=102;bars.get(124).close=102;
  check(PreExplosionEngine.evaluate(bars,124).state.equals("TOO_LATE"),"extended price must not create BUY or WATCH");
  bars.get(124).high=100.4;bars.get(124).close=100.3;
  bars.add(new PreExplosionEngine.Bar(t+125*60000,200,201,199,200,1e9,100000,1e9));
  check(PreExplosionEngine.evaluate(bars,124).detail().equals(before.detail()),"Future leakage");
  bars.get(120).time+=60000;check(PreExplosionEngine.evaluate(bars,124).state.equals("DATA"),"Gap accepted");bars.get(120).time-=60000;
  bars.get(124).buy=0;check(!PreExplosionEngine.evaluate(bars,124).state.equals("BUY"),"Flow filter failed");
  check(PreExplosionEngine.Bar.csv("1791244800000000,1,2,1,1,1,0,10,4,1,5,0").time==HistoricalReplay.START,"microsecond parser");
  BuyStability.State s=new BuyStability.State();s.established=true;s.phase="BUY";s.until=100000;
  BuyStability.observe(s,false,0,10000,"ضعف الحجم");check(s.phase.equals("RECHECK"),"soft failure flicker");
  BuyStability.observe(s,true,0,20000,"");check(s.phase.equals("BUY"),"recovery");
  BuyStability.observe(s,false,0,21000,"فات نطاق الدخول");check(s.phase.equals("CANCELLED"),"hard cancellation delayed");
  System.out.println("PreExplosion causality, gap, flow, timestamp, stability tests passed");
 }
}
