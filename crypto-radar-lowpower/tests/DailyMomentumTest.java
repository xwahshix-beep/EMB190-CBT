package com.wahshi.cryptoexplosionradar;
import java.util.*;
public class DailyMomentumTest {
 static List<DailyMomentumEngine.Bar> bars(){List<DailyMomentumEngine.Bar>b=new ArrayList<>();for(int i=0;i<100;i++)b.add(new DailyMomentumEngine.Bar(i*DailyMomentumEngine.DAY,100,101,99,100,100000));return b;}
 static void check(boolean b){if(!b)throw new AssertionError();}
 public static void main(String[] args){
  long now=100*DailyMomentumEngine.DAY;List<DailyMomentumEngine.Bar>b=bars();
  check(DailyMomentumEngine.evaluate(b,99,now).state.equals("WAIT"));
  b.set(99,new DailyMomentumEngine.Bar(99*DailyMomentumEngine.DAY,100,105,99,104,200000));
  DailyMomentumEngine.Result r=DailyMomentumEngine.evaluate(b,99,now);check(r.state.equals("WATCH"));check(r.upper20==101);check(Math.abs(r.atr-(2*13+6)/14.)<1e-9);
  check(DailyMomentumEngine.evaluate(b,99,now-1).state.equals("DATA"));
  check(DailyMomentumEngine.evaluate(b,99,now+DailyMomentumEngine.DAY).state.equals("DATA"));
  b.add(new DailyMomentumEngine.Bar(now,100,999,1,900,100000));check(DailyMomentumEngine.evaluate(b,99,now).upper20==101);
  b.set(80,new DailyMomentumEngine.Bar(80*DailyMomentumEngine.DAY+1,100,101,99,100,100000));check(DailyMomentumEngine.evaluate(b,99,now).state.equals("DATA"));
  System.out.println("DailyMomentumTest OK: causal, closed-day, stale-data and gap gates");
 }
}
