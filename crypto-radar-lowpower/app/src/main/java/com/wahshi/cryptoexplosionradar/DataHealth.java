package com.wahshi.cryptoexplosionradar;
/** Display-only transport health. Never changes engine thresholds. */
public final class DataHealth {
 public static long maxAge(String source){return source.equals("depth")?3000:source.equals("candles")?60000:source.equals("price")?15000:source.equals("market")?120000:1200000;}
 public static String state(long success,long failure,long now,long maxAge){
  if(success<=0)return "لم تصل بيانات ناجحة";
  if(now<success)return "وقت الجهاز غير متسق";
  if(failure>=success)return "تعذر التحديث — البيانات السابقة فقط";
  if(now-success>maxAge)return "بيانات قديمة";
  return "استجابة حديثة";
 }
 public static String source(String url){
  if(url.contains("/ticker/24hr"))return "market";
  if(url.contains("/klines"))return "candles";
  if(url.contains("/depth"))return "depth";
  if(url.contains("/exchangeInfo"))return "eligibility";
  return "price";
 }
}
