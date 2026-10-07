package com.wahshi.cryptoexplosionradar;
public final class FollowView {
 public static String render(String symbol,String detail,long attempt,long now,boolean enabled){
  if(!enabled)return "⭐ "+symbol+"\n⏸ الرادار متوقف؛ شغّله لاستئناف المتابعة";
  if(detail==null||detail.isEmpty())return "⭐ "+symbol+"\nجارٍ جلب أول تحديث…";
  if(attempt<=0||now-attempt>60000)
   return "⭐ "+symbol+"\n⏳ لم يصل تحديث جديد للمتابعة؛ اضغط تحديث المتابعة لإعادة المحاولة.\nآخر حالة (غير محدثة):\n"+detail.replace("🟢 شراء","تأكيد شراء سابق منتهي").replace("🔴 خروج","تنبيه خروج سابق").replace("🔵 احتفاظ","حالة احتفاظ سابقة");
  return detail;
 }
}
