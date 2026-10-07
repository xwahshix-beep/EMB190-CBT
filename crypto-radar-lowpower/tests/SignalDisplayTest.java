package com.wahshi.cryptoexplosionradar;
import java.util.*;
public final class SignalDisplayTest {
 static int n; static void check(boolean ok){n++;if(!ok)throw new AssertionError("case "+n);}
 public static void main(String[] args){
  String a="AAA/USDT\n🟢 شراء\nالسعر: 1",b="BBB/USDT\n🟢 شراء\nالسعر: 2";
  Map<String,Long> deadlines=new HashMap<>();deadlines.put("AAAUSDT",1000L);deadlines.put("BBBUSDT",6000L);
  String result=SignalDisplay.market(a+"\n────────────────\n\n"+b,deadlines,2000,true);
  check(result.contains("AAA/USDT\n⏳ جارٍ"));
  check(result.contains("BBB/USDT\n🟢 شراء — تحديث البيانات خلال 4 ث"));
  check(!SignalDisplay.one(a,2000,2000,true).contains("🟢 شراء"));
  check(!SignalDisplay.one(a,9000,2000,false).contains("🟢 شراء"));
  check(!SignalDisplay.market(a,Collections.emptyMap(),2000,true).contains("🟢 شراء"));
  check(SignalDisplay.one("🔵 احتفاظ",0,2000,true).equals("🔵 احتفاظ"));
  check(SignalDisplay.one("🔴 خروج",0,2000,true).equals("🔴 خروج"));
  check(SignalDisplay.one(a,2001,2000,true).contains("1 ث"));
  System.out.println("SignalDisplay: "+n+" tests passed");
 }
}
