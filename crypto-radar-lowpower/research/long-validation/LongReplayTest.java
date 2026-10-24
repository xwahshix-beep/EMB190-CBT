package com.wahshi.cryptoexplosionradar;
import java.util.*;
public class LongReplayTest{
 public static void main(String[]a){
  if(!(LongReplay.net(100,100,.0005)<0))throw new AssertionError("costs");
  if(LongReplay.net(100,0,.0005)!=-100)throw new AssertionError("loss");
  List<PreExplosionEngine.Bar>b=new ArrayList<>();long t=1700000000000L;
  for(int i=0;i<200;i++)b.add(new PreExplosionEngine.Bar(t+i*60000,100,101,99,100,100000,1000,50000));
  if(!LongReplay.path(b,124))throw new AssertionError("valid path");
  if(LongReplay.target(b,124))throw new AssertionError("false target");
  String before=PreExplosionEngine.evaluate(b,124).audit();b.set(150,new PreExplosionEngine.Bar(t+150*60000,200,201,199,200,100000,1000,50000));
  if(!before.equals(PreExplosionEngine.evaluate(b,124).audit()))throw new AssertionError("lookahead");
  if(!LongReplay.target(b,124))throw new AssertionError("missed target");
  b.remove(140);if(LongReplay.path(b,124))throw new AssertionError("gap allowed");
  System.out.println("costs, complete loss, causal features, target and gap checks passed");
 }
}
