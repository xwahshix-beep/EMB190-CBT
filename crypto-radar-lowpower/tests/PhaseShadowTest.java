package com.wahshi.cryptoexplosionradar;
import org.json.*;
public final class PhaseShadowTest {
 static final long BASE=1800000000000L;
 static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
 static JSONArray rows(int end,boolean shock)throws Exception{
  JSONArray rows=new JSONArray();
  for(int i=end-124;i<=end;i++){
   long t=BASE+i*60000L;double q=PhaseShadow.phase(t)==0?500000:10000;long n=PhaseShadow.phase(t)==0?5000:100;
   if(shock&&i>end-5){q*=100;n*=100;}
   rows.put(new JSONArray().put(t).put(1).put(1.001).put(.999).put(1).put(100).put(t+59999).put(q).put(n).put(50).put(q*.6));
  }return rows;
 }
 static long now(int end){return BASE+(end+1)*60000L+1000;}
 public static void main(String[] args)throws Exception {
  String s=PhaseShadow.update("{}",rows(124,false),now(124));JSONObject first=new JSONObject(s);
  check("calibrating".equals(first.getString("status")),"no premature inference");
  check(first.getLong("trained")==BASE+119*60000L,"last five excluded from calibration");
  long n=0;for(int p=0;p<15;p++)n+=first.getJSONArray("count").getLong(p);check(n==120,"unique calibration samples");
  check(s.equals(PhaseShadow.update(s,rows(124,true),now(124))),"duplicate closed minute cannot revise result or train");
  check(s.equals(PhaseShadow.update(s,rows(125,false),now(125)+76000)),"stale input rejected");
  JSONArray gap=rows(125,false);gap.getJSONArray(15).put(0,BASE);check(s.equals(PhaseShadow.update(s,gap,now(125))),"gap rejected");
  for(int end=244;end<=11044;end+=120)s=PhaseShadow.update(s,rows(end,false),now(end));
  JSONObject model=new JSONObject(s);check(model.getLong("frozen")>0,"model freezes only after sufficient calibration");
  String calibrated=model.getJSONArray("quote").toString();
  int end=11100;while(PhaseShadow.phase(BASE+end*60000L)!=4)end++;
  String rawAudit=PreExplosionJson.evaluate(rows(end,false),now(end)).audit();
  s=PhaseShadow.update(s,rows(end,false),now(end));model=new JSONObject(s);
  JSONObject l=model.getJSONObject("latest");check("raw_only".equals(l.getString("group")),"synthetic scheduled burst removed in shadow");
  check(Math.abs(l.getDouble("adjustedVol")-1)<1e-9,"phase normalization");
  check(rawAudit.equals(PreExplosionJson.evaluate(rows(end,false),now(end)).audit()),"live engine unchanged");
  s=PhaseShadow.update(s,rows(end+15,true),now(end+15));model=new JSONObject(s);
  check("both".equals(model.getJSONObject("latest").getString("group")),"abnormal burst still detected");
  check(calibrated.equals(model.getJSONArray("quote").toString()),"holdout cannot train frozen model");
  check(s.equals(PhaseShadow.update(s,rows(end,false),now(end))),"out of order rejected");
  // Test prospective settlement independent of activity group: losing observation retained.
  JSONObject sample=new JSONObject().put("at",now(end+15)).put("price",2).put("group","neither");
  model.put("samples",new JSONArray().put(sample));s=model.toString();
  s=PhaseShadow.update(s,rows(end+21,false),now(end+21));model=new JSONObject(s);
  check(model.getJSONArray("samples").getJSONObject(0).getDouble("r5")==-50,"loss included with correct forward time");
  s=PhaseShadow.update(s,rows(end+40,false),now(end+40));model=new JSONObject(s);
  check("missing".equals(model.getJSONArray("samples").getJSONObject(0).getString("r15")),"late observation stays missing");
  for(int j=1;j<80;j++)s=PhaseShadow.update(s,rows(end+40+j*60,false),now(end+40+j*60));
  model=new JSONObject(s);check(model.getJSONArray("samples").length()==72,"bounded sample storage");
  boolean controls=false;for(int j=0;j<72;j++)controls|="neither".equals(model.getJSONArray("samples").getJSONObject(j).getString("group"));check(controls,"no-activity controls sampled");
  int expired=end+40+29*1440;s=PhaseShadow.update(s,rows(expired,false),now(expired));check("complete".equals(new JSONObject(s).getString("status")),"experiment expiry");
  java.util.Map<String,Object> prefs=new java.util.HashMap<>();prefs.put("v66_phase_TESTUSDT",s);prefs.put("v66_phase_error","error");
  check(PhaseShadow.report(prefs,now(expired)).contains("لا توجد أفضلية مثبتة"),"report discloses limits");
  System.out.println("PhaseShadowTest passed: causal calibration, freeze, duplicates, gaps, synthetic periodicity, abnormal burst, losses, missing outcomes, cap, expiry, unchanged live engine");
 }
}
