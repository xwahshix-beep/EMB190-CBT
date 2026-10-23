package com.wahshi.cryptoexplosionradar;
public class RetryPolicyTest {
 public static void main(String[] args){
  long last=0;
  for(int i=1;i<=100;i++){
   long d=RetryPolicy.delay(i,1),low=RetryPolicy.delay(i,0);
   if(d<last||d>300000||low!=d/2)throw new AssertionError("bounds/growth");last=d;
  }
  if(RetryPolicy.delay(1,1)!=5000||RetryPolicy.delay(2,1)!=10000||RetryPolicy.delay(-1,0)!=2500||RetryPolicy.delay(1,Double.NaN)!=3750)throw new AssertionError("initial/clamping");
  System.out.println("RetryPolicyTest passed");
 }
}
