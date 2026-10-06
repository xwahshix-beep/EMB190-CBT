package com.wahshi.cryptoexplosionradar;
public class EntryGuardTest {
 static int count;static final long NOW=100000000;
 static void test(boolean allowed,double p,double res,double b3,double b5,double b15,double b45,double hi,double lo,long stamp){
  String reason=EntryGuard.reason(p,res,b3,b5,b15,b45,hi,lo,NOW,stamp);
  if((reason==null)!=allowed)throw new AssertionError("Case "+count+": "+reason);count++;
 }
 public static void main(String[] args){
  test(true,100.2,100,99.8,99.5,99,98,100.25,99.8,NOW-2000);
  test(false,100.7,100,99.8,99.5,99,98,100.25,99.8,NOW-2000);
  // Within the entry band but an already extended move: reject, unlike V5.3.2.
  test(false,100.2,100,99.8,98,99,98,100.25,99.8,NOW-2000);
  test(false,100.2,100,99.8,99.5,97,98,100.25,99.8,NOW-2000);
  test(false,100.2,100,99.8,99.5,99,96,100.25,99.8,NOW-2000);
  test(false,100.2,100,99.8,99.5,99,98,101.5,99.8,NOW-2000);
  test(false,100.2,100,99.8,99.5,99,98,100.25,99.8,NOW-75001);
  test(false,100.2,100,99.8,99.5,99,98,100.25,99.8,NOW+1);
  test(false,100.2,100,101,99.5,99,98,100.25,99.8,NOW-2000);
  test(false,100.2,100,99.8,101,99,98,100.25,99.8,NOW-2000);
  test(false,Double.NaN,100,99.8,99.5,99,98,100.25,99.8,NOW-2000);
  test(false,100.2,0,99.8,99.5,99,98,100.25,99.8,NOW-2000);
  test(false,100.2,100,99.8,99.5,99,98,99,100,NOW-2000);
  test(true,100.6,100,99.8,99.5,99,98,100.6,100,NOW-75000);
  // Synthetic INJ-like late move, NOT a reconstruction of the user's trade.
  test(false,8.089,8.05,8.02,7.90,7.85,7.70,8.10,8.02,NOW-1000);
  test(true,8.012,8,8,7.99,7.96,7.90,8.015,7.99,NOW-1000);
  System.out.println("EntryGuard: "+count+" cases passed");
 }
}
