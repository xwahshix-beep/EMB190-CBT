package com.wahshi.cryptoexplosionradar;
public final class BuyStabilityTest {
 static int n;static void check(boolean ok){n++;if(!ok)throw new AssertionError("case "+n);}
 public static void main(String[] args){
  BuyStability.State s=BuyStability.observe(null,true,75000,10000,"");check(s.phase.equals("PENDING"));
  s=BuyStability.observe(s,true,75000,10001,"");check(s.phase.equals("BUY"));long id=s.firstAt;
  s=BuyStability.observe(s,false,0,76000,"بيانات الدقيقة غير محدثة");check(s.phase.equals("DATA"));
  check(s.cooldownUntil==0&&s.established&&s.firstAt==id);
  s=BuyStability.observe(s,true,135000,80000,"");check(s.phase.equals("BUY")&&s.firstAt==id&&s.until==125000);
  check(!BuyStability.acceptsOwner("market","selected",s));
  check(BuyStability.acceptsOwner("market","market",s));
  check(BuyStability.acceptsOwner("","market",s));
  s=BuyStability.observe(s,false,0,81000,"السعر خارج نطاق الدخول");check(s.phase.equals("CANCELLED"));
  check(s.cooldownUntil==141000);
  s=BuyStability.observe(s,true,180000,82000,"");check(!s.phase.equals("BUY"));
  s=BuyStability.observe(null,true,75000,74000,"");check(s.phase.equals("PENDING"));
  check(BuyStability.dataIssue("تعذر التحقق الثاني من السعر"));
  check(!BuyStability.dataIssue("كسر المقاومة"));
  System.out.println("BuyStability: "+n+" tests passed");
 }
}
