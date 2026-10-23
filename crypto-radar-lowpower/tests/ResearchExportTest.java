package com.wahshi.cryptoexplosionradar;
import org.json.*;
public class ResearchExportTest {
 static void check(boolean v,String m){if(!v)throw new AssertionError(m);}
 public static void main(String[] args)throws Exception{
  check(ResearchExport.cell("=SUM(1,2)").equals("\"'=SUM(1,2)\""),"formula protection");
  check(ResearchExport.cell(" \n@formula\nnext").startsWith("\"'"),"multiline formula protection");
  check(ResearchExport.cell(-1.2).equals("\"-1.2\""),"numeric losses preserved");
  check(ResearchExport.cell("a,\"b\"\nc").equals("\"a,\"\"b\"\"\nc\""),"CSV escaping");
  long now=1800000000000L;PreExplosionEngine.Result r=new PreExplosionEngine.Result();r.closedAt=now-1000;r.volumeAcceleration=3;r.tradeAcceleration=2;r.buyShare=.6;r.quote5=30000;
  String decision=DecisionLedger.append("[]","AAAUSDT","BUY","سبب عربي",r,now);
  String prices="[{\"symbol\":\"AAAUSDT\",\"state\":\"BUY\",\"at\":1800000000000,\"m5\":-1.5,\"m15\":\"missing\"}]";
  String notices=ResearchExport.notification("[]","AAAUSDT",1,now-1000,now-5000,now,"reason");
  String csv=ResearchExport.csv(decision,prices,notices);
  check(csv.contains("notification_submitted")&&csv.contains("price_observation")&&csv.contains("سبب عربي")&&csv.contains("\"-1.5\"")&&csv.contains("missing"),"all record types and losses");
  check(new JSONArray(decision).getJSONObject(0).getDouble("vol")==3,"structured feature persistence");
  check(ResearchExport.explanation(r).contains("3.00")&&ResearchExport.explanation(null).contains("غير متاحة"),"accurate explanation or unavailable");
  String old="[{\"observedAt\":1}]";check(new JSONArray(ResearchExport.notification(old,"AAA",1,now,now,now,"x")).length()==1,"notification retention");
  boolean failed=false;try{ResearchExport.csv("broken","[]","[]");}catch(Exception e){failed=true;}check(failed,"corruption must not silently produce partial export");
  System.out.println("ResearchExportTest passed: escaping, formula safety, structured features, losses, missing data, retention");
 }
}
