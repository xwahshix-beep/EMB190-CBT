package com.wahshi.cryptoexplosionradar;
import org.json.*;
public class AlertDispatchTest {
 public static void main(String[] args)throws Exception{
  int[] calls={0};AlertDispatch.Sink sink=(title,body)->{calls[0]++;if(!title.startsWith("رُصد")||!body.contains("ليس توصية تداول")||title.contains("شراء الآن"))throw new AssertionError("wording");};
  boolean off=ProductionPolicy.enabled(new JSONObject().put("recommended_production_buy_enabled",false).toString());
  boolean on=ProductionPolicy.enabled(new JSONObject().put("recommended_production_buy_enabled",true).toString());
  if(AlertDispatch.send(off,"TESTUSDT","سبب الرصد",sink)||calls[0]!=0)throw new AssertionError("false delivered");
  if(!AlertDispatch.send(on,"TESTUSDT","سبب الرصد",sink)||calls[0]!=1)throw new AssertionError("true not delivered");
  JSONObject e=new JSONArray(DecisionLedger.operational("[]","market","DISCONNECTED","IOException",10000,5000,"test")).getJSONObject(0);
  if(!e.has("decision_at")||!e.isNull("data_age")||!e.isNull("received_at"))throw new AssertionError("unknown timestamps fabricated");
  System.out.println("AlertDispatchTest passed: false/true dispatch, neutral wording, shared event schema");
 }
}
