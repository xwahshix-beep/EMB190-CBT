package com.wahshi.cryptoexplosionradar;
import java.util.*;
public class FlowTransitionTest {
 static void check(boolean b,String s){if(!b)throw new AssertionError(s);}
 static List<PreExplosionEngine.Bar> bars(){List<PreExplosionEngine.Bar> a=new ArrayList<>();for(int i=0;i<125;i++)a.add(new PreExplosionEngine.Bar(1800000000000L+i*60000,100,100.1,99.9,100,10000,100,5000));return a;}
 static void pressure(List<PreExplosionEngine.Bar> a){for(int i=122;i<125;i++){PreExplosionEngine.Bar b=a.get(i);b.quote=30000;b.trades=300;b.buy=21000;b.close=100+(i-121)*.1;b.high=b.close+.05;}}
 public static void main(String[] args){
  List<PreExplosionEngine.Bar>a=bars();check(FlowTransitionEngine.evaluate(a,124).state.equals("WAIT"),"flat activity");pressure(a);
  FlowTransitionEngine.Result good=FlowTransitionEngine.evaluate(a,124);check(good.state.equals("FLOW_READY"),good.detail());
  a.add(new PreExplosionEngine.Bar(1800000000000L+125*60000,1000,2000,999,1500,1e9,100000,1e9));
  check(good.detail().equals(FlowTransitionEngine.evaluate(a,124).detail()),"future leakage");
  for(int i=122;i<125;i++)a.get(i).close=100;
  check(FlowTransitionEngine.evaluate(a,124).state.equals("ABSORPTION_RISK"),"buying without response");
  a=bars();pressure(a);for(int i=122;i<125;i++)a.get(i).buy=10000;
  check(FlowTransitionEngine.evaluate(a,124).state.equals("EARLY_WATCH"),"buyer confirmation");
  a=bars();pressure(a);for(int i=122;i<125;i++)a.get(i).trades=100;
  check(FlowTransitionEngine.evaluate(a,124).state.equals("WAIT"),"volume alone");
  a=bars();pressure(a);a.get(124).close=103;a.get(124).high=103;
  check(FlowTransitionEngine.evaluate(a,124).state.equals("TOO_LATE"),"no chase");
  a=bars();pressure(a);a.get(123).time+=60000;
  check(FlowTransitionEngine.evaluate(a,124).state.equals("DATA"),"gap rejected");
  a=bars();a.get(120).buy=Double.NaN;check(FlowTransitionEngine.evaluate(a,124).state.equals("DATA"),"NaN rejected");
  a=bars();pressure(a);a.get(124).close=99;a.get(124).low=99;
  check(FlowTransitionEngine.evaluate(a,124).state.equals("WAIT"),"adverse price");
  a=bars();PreExplosionEngine.Bar last=a.get(124);last.quote=60000;last.trades=600;last.buy=42000;last.close=100.1;last.high=100.15;
  FlowTransitionEngine.Result impulse=FlowTransitionEngine.evaluate(a,124);
  check(impulse.path.equals("IMPULSE")&&impulse.state.equals("EARLY_WATCH"),"single burst persistence");
  System.out.println("FlowTransitionTest passed: causality, gaps, invalid data, volume-only, persistence, absorption, impulse, adverse response and no-chase");
 }
}
