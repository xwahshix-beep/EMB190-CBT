package com.wahshi.cryptoexplosionradar;
public class BuyAlertPolicyTest {
 static int count;
 static final long NOW=100000000L;
 static void check(boolean expected,boolean enabled,boolean permission,boolean confirmed,double price,double low,double high,long quote,long signal,long sent){
  boolean got=BuyAlertPolicy.eligible(enabled,permission,confirmed,price,low,high,NOW,quote,signal,sent);
  if(got!=expected)throw new AssertionError("case "+count);count++;
 }
 public static void main(String[] args){
  check(true,true,true,true,100,99,101,NOW-1000,NOW-10000,0);
  check(false,false,true,true,100,99,101,NOW,NOW,0);
  check(false,true,false,true,100,99,101,NOW,NOW,0);
  check(false,true,true,false,100,99,101,NOW,NOW,0);
  check(false,true,true,true,102,99,101,NOW,NOW,0);
  check(false,true,true,true,98,99,101,NOW,NOW,0);
  check(false,true,true,true,100,101,99,NOW,NOW,0);
  check(false,true,true,true,100,99,101,NOW-15001,NOW,0);
  check(false,true,true,true,100,99,101,NOW,NOW-600001,0);
  check(false,true,true,true,100,99,101,NOW,NOW,NOW-1000);
  check(true,true,true,true,100,99,101,NOW,NOW,NOW-BuyAlertPolicy.COOLDOWN);
  check(false,true,true,true,Double.NaN,99,101,NOW,NOW,0);
  check(false,true,true,true,100,99,Double.POSITIVE_INFINITY,NOW,NOW,0);
  check(false,true,true,true,100,99,101,NOW+1000,NOW,0);
  check(false,true,true,true,100,99,101,NOW,NOW+1000,0);
  check(true,true,true,true,99,99,101,NOW,NOW,0);
  check(true,true,true,true,101,99,101,NOW,NOW,0);
  System.out.println("BuyAlertPolicy: "+count+" cases passed");
 }
}
