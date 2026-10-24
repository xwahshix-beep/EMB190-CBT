package com.wahshi.cryptoexplosionradar;
public class TradingEligibilityTest {
 static long now=100000;static int requests;static String body;
 static void check(boolean b,String m){if(!b)throw new AssertionError(m);}
 static String response(String status,boolean spot){return "{\"symbols\":[{\"symbol\":\"BTCUSDT\",\"status\":\""+status+"\",\"isSpotTradingAllowed\":"+spot+"}]}";}
 public static void main(String[] args){
  body=response("TRADING",true);TradingEligibility c=new TradingEligibility(()->{requests++;if(body==null)throw new Exception();return body;},()->now);
  check(c.reason("BTCUSDT")==null,"trading allowed");check(c.reason("UNKNOWN")!=null,"missing symbol blocked");check(requests==1,"shared cache");
  for(String status:new String[]{"CANCEL_ONLY","HALT","BREAK","END_OF_DAY","FUTURE_STATUS",""}){now+=60000;body=response(status,true);check(c.reason("BTCUSDT")!=null,"non trading blocked: "+status);}
  now+=60000;body=response("TRADING",false);check(c.reason("BTCUSDT")!=null,"spot disabled");
  now+=60000;body=response("TRADING",true);check(c.reason("BTCUSDT")==null,"trading resumes");
  now+=60000;body=null;check(c.reason("BTCUSDT")!=null,"failure cannot use expired status");int n=requests;now+=1000;check(c.reason("BTCUSDT")!=null&&requests==n,"retry bounded");
  now+=30000;body=response("TRADING",true);check(c.reason("BTCUSDT")==null,"recover after retry");
  now+=60000;body="{\"symbols\":[]}";check(c.reason("BTCUSDT")!=null,"empty metadata rejected");
  System.out.println("TradingEligibilityTest passed: status transitions, cache, expiry, failure and recovery");
 }
}
