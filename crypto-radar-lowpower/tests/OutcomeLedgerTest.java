package com.wahshi.cryptoexplosionradar;
import org.json.*;
public class OutcomeLedgerTest {
 static void ok(boolean b,String why){if(!b)throw new AssertionError(why);}
 public static void main(String[] args)throws Exception{
  long t=1800000000000L;
  String a=OutcomeLedger.start("[]","ARKUSDT","BUY",100,t);
  ok(new JSONArray(OutcomeLedger.start(a,"ARKUSDT","BUY",110,t+60000)).length()==1,"duplicate resets reference");
  a=OutcomeLedger.observe(a,"ARKUSDT",120,t+299999,t+300000);
  ok(!new JSONArray(a).getJSONObject(0).has("m5"),"future horizon filled early");
  a=OutcomeLedger.observe(a,"OTHERUSDT",200,t+300000,t+300001);
  ok(!new JSONArray(a).getJSONObject(0).has("m5"),"wrong symbol");
  a=OutcomeLedger.observe(a,"ARKUSDT",102,t+300000,t+300001);
  ok(Math.abs(new JSONArray(a).getJSONObject(0).getDouble("m5")-2)<1e-9,"return");
  a=OutcomeLedger.observe(a,"ARKUSDT",150,t+330000,t+330001);
  ok(Math.abs(new JSONArray(a).getJSONObject(0).getDouble("m5")-2)<1e-9,"overwritten result");
  a=OutcomeLedger.observe(a,"ARKUSDT",130,t+1200000,t+1200001);
  ok("missing".equals(new JSONArray(a).getJSONObject(0).getString("m15")),"late price substituted");
  a=OutcomeLedger.observe(a,"ARKUSDT",98,t+3600000,t+3600001);
  ok(Math.abs(new JSONArray(a).getJSONObject(0).getDouble("m60")+2)<1e-9,"loss omitted");
  String b=OutcomeLedger.start("[]","ARKUSDT","WATCH",100,t);
  b=OutcomeLedger.observe(b,"ARKUSDT",110,t+300000,t+390001);
  ok(!new JSONArray(b).getJSONObject(0).has("m5"),"stale observation accepted");
  ok(OutcomeLedger.start("[]","ARKUSDT","BUY",Double.NaN,t).equals("[]"),"NaN");
  ok(new JSONArray(OutcomeLedger.start(a,"ARKUSDT","BUY",120,t+3600000)).length()==2,"new hour suppressed");
  ok(OutcomeLedger.report(a,t+4000000).contains("-2.00%"),"loss absent in report");
  System.out.println("OutcomeLedger forward timing, missing data, deduplication and loss tests passed");
 }
}
