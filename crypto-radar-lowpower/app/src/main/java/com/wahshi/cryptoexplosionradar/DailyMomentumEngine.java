package com.wahshi.cryptoexplosionradar;
import java.util.List;

/** Research-only daily signal. No trading, position sizing or profit claim. */
public final class DailyMomentumEngine {
 public static final long DAY=86400000L;
 public static final class Bar {
  public final long time; public final double open,high,low,close,quote;
  public Bar(long t,double o,double h,double l,double c,double q){time=t;open=o;high=h;low=l;close=c;quote=q;}
  public boolean valid(){return Double.isFinite(open+high+low+close+quote)&&low>0&&high>=Math.max(open,close)&&low<=Math.min(open,close)&&quote>=0;}
 }
 public static final class Result {
  public String state="DATA",reason="يلزم سجل يومي مكتمل";
  public double close,upper20,lower10,sma50,atr,priceCap;
  public long closedAt;
  public boolean breakout,trend,exit;
 }
 public static Result evaluate(List<Bar> bars,int at,long serverTime){
  Result r=new Result(); if(at<89||at>=bars.size())return r;
  for(int i=0;i<=at;i++)if(!bars.get(i).valid()){r.reason="بيانات أسعار غير صالحة";return r;}
  for(int i=at-88;i<=at;i++)if(bars.get(i).time-bars.get(i-1).time!=DAY){r.reason="فجوة في السجل اليومي";return r;}
  Bar last=bars.get(at);
  if(last.time+DAY>serverTime){r.reason="الشمعة اليومية لم تغلق";return r;}
  if(last.time!=serverTime/DAY*DAY-DAY){r.reason="السجل قديم؛ لا توجد إشارة حديثة";return r;}
  for(int i=at-29;i<=at;i++)if(bars.get(i).quote<=0){r.reason="سجل حجم التداول غير مكتمل";return r;}
  r.close=last.close;r.closedAt=last.time+DAY;
  r.lower10=Double.POSITIVE_INFINITY;
  for(int i=at-20;i<at;i++)r.upper20=Math.max(r.upper20,bars.get(i).high);
  for(int i=at-10;i<at;i++)r.lower10=Math.min(r.lower10,bars.get(i).low);
  for(int i=at-49;i<=at;i++)r.sma50+=bars.get(i).close/50.;
  double sum=0,atr=0;
  for(int i=0;i<=at;i++){
   Bar b=bars.get(i);double tr=b.high-b.low;
   if(i>0)tr=Math.max(tr,Math.max(Math.abs(b.high-bars.get(i-1).close),Math.abs(b.low-bars.get(i-1).close)));
   if(i<14){sum+=tr;if(i==13)atr=sum/14.;}else atr=(atr*13+tr)/14.;
  }
  r.atr=atr;r.priceCap=r.close+.25*atr;
  r.breakout=r.close>r.upper20;r.trend=r.close>r.sma50;r.exit=r.close<r.lower10;
  r.state=r.breakout&&r.trend?"WATCH":"WAIT";
  r.reason=r.state.equals("WATCH")?"تحققت شروط السعر؛ مرشح بحثي فقط، دون تحقق من ترتيب السيولة أو التنفيذ":r.exit?"إغلاق دون أدنى عشرة أيام؛ شرط خروج بحثي لمن لديه مركز افتراضي":"شروط الاختراق والاتجاه غير مكتملة";
  return r;
 }
 private DailyMomentumEngine(){}
}
