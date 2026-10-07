package com.wahshi.cryptoexplosionradar;
public final class BuyStabilityTest {
 static int n; static void check(boolean ok){n++;if(!ok)throw new AssertionError("case "+n);}
 public static void main(String[] args){
  BuyStability.State s=BuyStability.observe(null,true,75000,10000,"");check(s.phase.equals("PENDING"));
  s=BuyStability.observe(s,true,75000,11000,"");check(!s.phase.equals("BUY"));
  s=BuyStability.observe(s,true,75000,13000,"");check(s.phase.equals("BUY"));
  s=BuyStability.observe(s,false,0,14000,"خرج السعر من النطاق");check(s.phase.equals("CANCELLED"));
  s=BuyStability.observe(s,true,90000,18000,"");check(!s.phase.equals("BUY"));
  s=BuyStability.observe(s,true,150000,74000,"");check(s.phase.equals("PENDING"));
  s=BuyStability.observe(s,true,150000,77000,"");check(s.phase.equals("BUY"));
  s=BuyStability.observe(s,true,200000,78000,"");check(s.until==150000);
  s=BuyStability.observe(s,true,300000,150000,"");check(s.phase.equals("EXPIRED"));
  s=BuyStability.observe(null,true,25000,10000,"");check(s.phase.equals("WAIT"));
  s=BuyStability.observe(null,true,45000,10000,"");s=BuyStability.observe(s,true,45000,20000,"");check(s.phase.equals("WAIT"));
  s=BuyStability.observe(null,true,90000,10000,"");s=BuyStability.observe(s,false,0,12000,"فشل الاتصال");check(!s.phase.equals("BUY"));
  System.out.println("BuyStability: "+n+" tests passed");
 }
}
