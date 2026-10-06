package com.wahshi.cryptoexplosionradar;
/** Conservative experimental extension limits, independently testable. */
public final class EntryGuard {
 public static final double MAX_EXTENSION=.006;
 public static String reason(double price,double resistance,double base3,double base5,double base15,double base45,double barHigh,double barLow,long now,long closedAt){
  double[] values={price,resistance,base3,base5,base15,base45,barHigh,barLow};
  for(double v:values)if(!Double.isFinite(v)||v<=0)return "بيانات سعر غير صالحة";
  if(barHigh<barLow)return "بيانات شمعة غير صالحة";
  if(closedAt<=0||now<closedAt||now-closedAt>75000)return "بيانات الدقيقة غير محدثة";
  if(price>resistance*(1+MAX_EXTENSION))return "فات نطاق الدخول — ابتعد السعر عن الاختراق";
  if(price/base5-1>.015)return "فات نطاق الدخول — امتداد سريع خلال 5 دقائق";
  if(price/base15-1>.025)return "فات نطاق الدخول — امتداد خلال 15 دقيقة";
  if(price/base45-1>.04)return "فات نطاق الدخول — الحركة ممتدة خلال 45 دقيقة";
  if(barHigh/barLow-1>.012)return "انتظار تهدئة — شمعة الدقيقة ممتدة";
  if(price<base3||price<base5)return "انتظار اتجاه متوافق خلال 3 و5 دقائق";
  return null;
 }
}
