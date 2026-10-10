package com.wahshi.cryptoexplosionradar;
import java.util.*;
public class DecisionLedgerTest {
 static void check(boolean b,String why){if(!b)throw new AssertionError(why);}
 public static void main(String[] args){
  long now=1800000000000L;PreExplosionEngine.Result r=new PreExplosionEngine.Result();
  r.state="EARLY_WATCH";r.score=70;r.reason="flow pending";r.closedAt=now-1000;
  String history=DecisionLedger.append("[]","MAGICUSDT",r.state,r.reason,r,now);
  history=DecisionLedger.append(history,"MAGICUSDT",r.state,r.reason,r,now+1000);
  check(new org.json.JSONArray(history).length()==1,"same candle duplicated");
  check(DecisionLedger.history(history,5).contains("flow pending"),"reason missing");
  String book=DecisionLedger.remember("{}","MAGICUSDT",r,now);
  check(DecisionLedger.recent(book,now+60000,5).contains("MAGICUSDT"),"candidate disappeared");
  check(DecisionLedger.recent(book,now+7200001,5).isEmpty(),"stale candidate not expired");
  check(DecisionLedger.recent(book,now+60000,5).contains("EARLY_WATCH"),"candidate state missing");
  System.out.println("Decision log and candidate retention tests passed");
 }
}