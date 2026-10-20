package com.wahshi.cryptoexplosionradar;
public class AlertScopeTest {
 static int count;
 static void check(boolean want,boolean all,String selected,String symbol){
  if(BuyAlertPolicy.inScope(all,selected,symbol)!=want)throw new AssertionError("scope case "+count);
  count++;
 }
 public static void main(String[] args){
  check(true,true,"BTCUSDT","ETHUSDT");
  check(true,true,"","ETHUSDT");
  check(true,false,"ETHUSDT","ETHUSDT");
  check(false,false,"BTCUSDT","ETHUSDT");
  check(false,false,"","ETHUSDT");
  check(false,true,"MUBUSDT","MUBUSDT");
  check(false,false,"MUBUSDT","MUBUSDT");
  check(false,true,"",null);
  check(true,true,"","BNBUSDT");
  System.out.println("AlertScope: "+count+" cases passed");
 }
}
