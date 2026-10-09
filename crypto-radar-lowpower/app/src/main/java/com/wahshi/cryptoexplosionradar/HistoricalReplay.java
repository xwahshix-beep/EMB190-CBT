package com.wahshi.cryptoexplosionradar;
import java.io.*;
import java.util.*;
/** Fixed chronological evaluation: every eligible minute, no peak-selected windows. */
public final class HistoricalReplay {
 public static final long START=1791244800000L, END=1791590400000L;
 public static final String[] SYMBOLS={"MAGICUSDT","KAIAUSDT","BATUSDT","ZKUSDT","STRKUSDT","BTCUSDT","ETHUSDT","BNBUSDT","XRPUSDT","ADAUSDT"};
 public static final String[] VARIANTS={"VOLUME","VOLUME_FLOW","VOLUME_TRADES","BREAKOUT","FULL_WATCH","FULL_BUY"};
 public static List<PreExplosionEngine.Bar> read(Reader input)throws Exception{
  List<PreExplosionEngine.Bar> out=new ArrayList<>();BufferedReader r=new BufferedReader(input);String s;
  while((s=r.readLine())!=null)if(!s.trim().isEmpty())out.add(PreExplosionEngine.Bar.csv(s));
  out.sort(Comparator.comparingLong(b->b.time));
  for(int i=1;i<out.size();i++)if(out.get(i).time==out.get(i-1).time)throw new IOException("Duplicate minute");return out;
 }
 static boolean hit(PreExplosionEngine.Result r,int v){
  if(r.state.equals("DATA"))return false;
  switch(v){case 0:return r.volumeAcceleration>=2.5;case 1:return r.volumeAcceleration>=2.5&&r.buyShare>=.55;case 2:return r.volumeAcceleration>=2.5&&r.tradeAcceleration>=1.8;case 3:return r.extension>=.001&&r.extension<=.006;case 4:return r.state.equals("WATCH")||r.state.equals("BUY");default:return r.state.equals("BUY");}
 }
 public static String run(String symbol,List<PreExplosionEngine.Bar> bars,StringBuilder alerts){
  StringBuilder out=new StringBuilder();
  for(int v=0;v<VARIANTS.length;v++){
   int count=0,success=0,failed=0,censored=0,eligible=0,events=0,detected=0;double net=0,leadSum=0;
   long next=0,nextEvent=0;List<Long> signals=new ArrayList<>();
   for(int i=124;i<bars.size();i++){
    PreExplosionEngine.Bar b=bars.get(i);if(b.time<START||b.time>=END)continue;
    PreExplosionEngine.Result r=PreExplosionEngine.evaluate(bars,i);if(r.state.equals("DATA"))continue;eligible++;
    if(b.time>=next&&hit(r,v)){
     count++;signals.add(r.closedAt);next=b.time+15*60000L;
     boolean complete=i+60<bars.size();
     for(int j=i+1;complete&&j<=i+60;j++)if(bars.get(j).time!=b.time+(j-i)*60000L||bars.get(j).time>=END)complete=false;
     String outcome="CENSORED";double ret=Double.NaN;
     if(complete){
      double entry=bars.get(i+1).open*1.001; // 10 bps adverse entry slippage.
      ret=bars.get(i+60).close/entry-1;
      outcome="TIMEOUT";
      for(int j=i+1;j<=i+60;j++){
       PreExplosionEngine.Bar f=bars.get(j);
       // Conservative: stop first if target and stop occur in the same minute.
       if(f.low<=entry*.98){ret=Math.min(-.02,f.open/entry-1);outcome="STOP";break;}
       if(f.high>=entry*1.05){ret=.05;outcome="TARGET";break;}
      }
      ret-=.003; // 20 bps round-trip fees + 10 bps exit slippage.
      net+=ret;if(outcome.equals("TARGET"))success++;else failed++;
     }else censored++;
     if(alerts!=null)alerts.append(String.format(Locale.US,"%s,%s,%d,%s,%.6f,%.4f,%.4f,%.4f,%.2f\n",symbol,VARIANTS[v],r.closedAt,outcome,ret,r.volumeAcceleration,r.tradeAcceleration,r.buyShare,r.quote5));
    }
   }
   // Independent disjoint +15%/120m episodes, defined from next-minute open.
   // Count all qualifying episodes, including those with no signal. Lead ends at first +15% touch.
   for(int i=124;i+120<bars.size();i++){
    PreExplosionEngine.Bar b=bars.get(i);if(b.time<START||b.time>=END||b.time<nextEvent)continue;
    boolean contiguous=true;for(int j=1;j<=120;j++)if(bars.get(i+j).time!=b.time+j*60000L||bars.get(i+j).time>=END){contiguous=false;break;}
    if(!contiguous)continue;double base=bars.get(i+1).open;long onset=0;
    for(int j=1;j<=120;j++)if(bars.get(i+j).high>=base*1.15){onset=bars.get(i+j).time;break;}
    if(onset==0)continue;events++;nextEvent=b.time+120*60000L;
    for(long t:signals)if(t>=b.time&&t<onset){detected++;leadSum+=(onset-t)/60000.0;break;}
   }
   int evaluated=success+failed;
   out.append(String.format(Locale.US,"%s,%s,%d,%d,%d,%d,%d,%s,%s,%d,%d,%s\n",symbol,VARIANTS[v],eligible,count,success,failed,censored,evaluated==0?"NA":String.format(Locale.US,"%.2f",100.0*success/evaluated),evaluated==0?"NA":String.format(Locale.US,"%.3f",100*net/evaluated),events,detected,detected==0?"NA":String.format(Locale.US,"%.2f",leadSum/detected)));
  }return out.toString();
 }
 public static final String HEADER="symbol,variant,eligible_minutes,alerts,target5_before_stop2,false_alerts,censored,precision_pct,mean_net_pct,episodes15pct,episodes_detected,mean_lead_minutes\n";
}
