package com.wahshi.cryptoexplosionradar;
import org.json.*;
import java.util.*;
/** Descriptive observations; never used to generate signals. */
public final class PerformanceCard {
 private PerformanceCard(){}
 public static double net(double gross){return 100*((1+gross/100)*(1-.001)*(1-.0005)/((1+.001)*(1+.0005))-1);}
 public static String report(String json,long now){
  StringBuilder b=new StringBuilder("بطاقة المحرك الأساسي • "+OutcomeLedger.RULES+"\nالقواعد ثابتة؛ الرصد يبدأ من هذا الإصدار. لا يشمل المتابعة اليدوية. آخر 1000 سجل / 7 أيام؛ صدّر السجل يوميًا.\n\nمحاكاة تكاليف افتراضية: رسوم 0.10% وانزلاق 0.05% لكل طرف؛ ليست رسوم حسابك أو تنفيذًا فعليًا، ولا تحاكي العمق أو التأخير.\n");
  try{JSONArray a=new JSONArray(json);int legacy=0;
   for(int i=0;i<a.length();i++)if(!OutcomeLedger.RULES.equals(a.getJSONObject(i).optString("rules")))legacy++;
   b.append("سجلات قديمة خارج المقارنة: ").append(legacy).append("\n");
   for(String state:new String[]{"BUY","WATCH","EARLY_WATCH"}){
    b.append("\n").append(state).append("\n");
    for(int m:OutcomeLedger.MINUTES){int n=0,win=0,missing=0,pending=0;double sum=0,raw=0;
     for(int i=0;i<a.length();i++){JSONObject e=a.getJSONObject(i);if(!OutcomeLedger.RULES.equals(e.optString("rules"))||!state.equals(e.optString("state")))continue;
      Object v=e.opt("m"+m);if(v instanceof Number){double r=((Number)v).doubleValue();if(!Double.isFinite(r)){missing++;continue;}n++;raw+=r;sum+=net(r);if(net(r)>0)win++;}
      else if("missing".equals(v)||now>e.getLong("at")+m*60000L+165000)missing++;else pending++;
     }
     b.append(m).append("د: ").append(n==0?"لا نتائج":String.format(Locale.US,"متوسط خام %+.2f%%؛ بعد الافتراضات %+.2f%%؛ موجب %d/%d",raw/n,sum/n,win,n)).append(" • مفقود ").append(missing).append(" • انتظار ").append(pending).append("\n");
    }
   }
   b.append("\nأحدث 30 بطاقة: أقصى/أدنى تغير من الأسعار المرصودة فقط خلال 60د، وليسا قمة/قاع السوق. الفجوات تقلل اكتمال الرصد.\n");int shown=0;
   for(int i=0;i<a.length()&&shown<30;i++){JSONObject e=a.getJSONObject(i);if(!OutcomeLedger.RULES.equals(e.optString("rules")))continue;shown++;
    b.append("\n").append(e.optString("symbol")).append(" • ").append(e.optString("state")).append("\n").append(new Date(e.getLong("at"))).append(" • السعر ").append(e.getDouble("price")).append("\nالسبب: ").append(e.optString("reason")).append("\n");
    for(int m:OutcomeLedger.MINUTES){Object v=e.opt("m"+m);b.append(m).append("د: ").append(v instanceof Number?String.format(Locale.US,"خام %+.2f%% / افتراضي %+.2f%%",((Number)v).doubleValue(),net(((Number)v).doubleValue())):("missing".equals(v)||now>e.getLong("at")+m*60000L+165000)?"مفقود":"انتظار").append("\n");}
    int count=e.optInt("sampleCount",0);b.append(count==0?"لا عينات لاحقة":String.format(Locale.US,"أعلى مرصود %+.2f%%؛ أدنى مرصود %+.2f%%؛ عينات %d",e.optDouble("sampleMax"),e.optDouble("sampleMin"),count));
    long gap=Math.max(e.optLong("maxGapMs",0),Math.max(0,Math.min(now,e.getLong("at")+3600000)-e.optLong("lastSampleAt",e.getLong("at"))));b.append(" • أكبر فجوة/انتظار ").append(gap/1000).append("ث\n");
   }
   b.append("\nهذه متوسطات تغير السعر وليست عائد محفظة أو دليل ربحية. الفرص الفائتة والإنذارات الكاذبة لم تُقاس بعد؛ يلزم تعريف حركة مستهدفة وتغطية مستقلة لجميع العملات.");
  }catch(Exception ex){b.append("تعذر قراءة السجل");}return b.toString();
 }
}
