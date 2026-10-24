package com.wahshi.cryptoexplosionradar;
import org.json.*;
public class PerformanceCardTest {
 static void ok(boolean b,String why){if(!b)throw new AssertionError(why);}
 public static void main(String[] args)throws Exception{
  long t=1800000000000L;
  String s=OutcomeLedger.start("[]","AAAUSDT","BUY",100,t,"reason");
  s=OutcomeLedger.observe(s,"AAAUSDT",105,t+60000,t+60001);
  s=OutcomeLedger.observe(s,"AAAUSDT",90,t+120000,t+120001);
  s=OutcomeLedger.observe(s,"AAAUSDT",200,t+60000,t+120001);
  JSONObject e=new JSONArray(s).getJSONObject(0);
  ok(Math.abs(e.getDouble("sampleMax")-5)<1e-8,"out of order peak");
  ok(Math.abs(e.getDouble("sampleMin")+10)<1e-8,"loss missing");
  ok(e.getInt("sampleCount")==2,"duplicate count");
  s=OutcomeLedger.observe(s,"AAAUSDT",110,t+3660000,t+3660001);
  ok(new JSONArray(s).getJSONObject(0).getInt("sampleCount")==2,"post horizon extremum");
  ok(PerformanceCard.net(0)<0,"cost missing");
  ok(PerformanceCard.net(-100)==-100,"loss cost math");
  ok(PerformanceCard.report(s,t+4000000).contains("reason"),"reason absent");
  ok(PerformanceCard.report(s,t+4000000).contains("-10.00%"),"loss report");
  String legacy="[{\"symbol\":\"OLD\",\"state\":\"BUY\",\"price\":100,\"at\":"+t+",\"m5\":100}]";
  ok(PerformanceCard.report(legacy,t+4000000).contains("سجلات قديمة خارج المقارنة: 1"),"legacy mixed");
  String csv=ResearchExport.csv("[]",s,"[]");ok(csv.contains(OutcomeLedger.RULES)&&csv.contains("sampleMin"),"audit missing");
  System.out.println("PerformanceCard cost, extrema, dedupe, horizon and legacy isolation passed");
 }
}
