package com.wahshi.cryptoexplosionradar;
/** Equal-jitter exponential reconnect delay, capped at five minutes. */
public final class RetryPolicy {
 private RetryPolicy(){}
 public static long delay(int failures,double random){
  long cap=Math.min(300000L,5000L*(1L<<Math.min(6,Math.max(0,failures-1))));
  double u=Double.isFinite(random)?Math.max(0,Math.min(1,random)):0.5;
  return (long)(cap*(0.5+u*0.5));
 }
}
