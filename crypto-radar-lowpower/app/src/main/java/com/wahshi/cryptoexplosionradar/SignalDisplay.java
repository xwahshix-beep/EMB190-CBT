package com.wahshi.cryptoexplosionradar;
import java.util.Map;
import java.util.regex.*;
/** Display freshness only; never extends the underlying trading signal. */
public final class SignalDisplay {
 public static String one(String text,long until,long now,boolean running) {
  if(!text.contains("🟢 شراء"))return text;
  if(!running)return text.replace("🟢 شراء","⏸ الرادار متوقف — الإشارة غير محدثة");
  if(until<=now)return text.replace("🟢 شراء","⏳ جارٍ تحديث بيانات الإشارة؛ لم تُلغَ فنيًا");
  long seconds=(until-now+999)/1000;
  return text.replace("🟢 شراء","🟢 شراء — تحديث البيانات خلال "+seconds+" ث\nقد تتغير الإشارة قبل ذلك إذا تغير السعر أو الشروط.");
 }
 public static String market(String text,Map<String,Long> expiries,long now,boolean running) {
  String[] blocks=text.split("\n────────────────\n\n",-1);
  Pattern heading=Pattern.compile("(?m)^([A-Z0-9]+)/USDT\\s*$");
  for(int i=0;i<blocks.length;i++) {
   Matcher m=heading.matcher(blocks[i]);String symbol=m.find()?m.group(1)+"USDT":"";
   Long expiry=expiries.get(symbol);
   blocks[i]=one(blocks[i],expiry==null?0:expiry,now,running);
  }
  return String.join("\n────────────────\n\n",blocks);
 }
}
