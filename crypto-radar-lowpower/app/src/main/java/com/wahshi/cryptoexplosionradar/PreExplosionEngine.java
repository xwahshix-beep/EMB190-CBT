package com.wahshi.cryptoexplosionradar;
import java.util.*;
/** Causal, closed-minute features shared verbatim by live Android and replay. Experimental fixed thresholds. */
public final class PreExplosionEngine {
 public static final int WARMUP=125;
 public static final class Bar {
  public long time; public double open,high,low,close,quote,buy; public long trades;
  public Bar(long t,double o,double h,double l,double c,double q,long n,double b){time=t;open=o;high=h;low=l;close=c;quote=q;trades=n;buy=b;}
  public static Bar csv(String s){String[] x=s.split(",");long t=Long.parseLong(x[0]);if(t>100000000000000L)t/=1000;return new Bar(t,Double.parseDouble(x[1]),Double.parseDouble(x[2]),Double.parseDouble(x[3]),Double.parseDouble(x[4]),Double.parseDouble(x[7]),Long.parseLong(x[8]),Double.parseDouble(x[10]));}
  public boolean valid(){return Double.isFinite(open+high+low+close+quote+buy)&&low>0&&high>=Math.max(open,close)&&low<=Math.min(open,close)&&quote>=0&&trades>=0&&buy>=0&&buy<=quote*1.000001;}
 }
 public static final class Result {
  public String state="DATA",reason="Incomplete minute history";
  public double volumeAcceleration,tradeAcceleration,buyShare,quote5,resistance,extension,change5,tradeZ,flowImbalance;
  public int score;
  public long closedAt;
  public String detail(){return String.format(Locale.US,"%s | Score %d | Vol %.2fx | Trades %.2fx | Z %.2f | Buy %.1f%% | Q5 $%.0f | Ext %.2f%%",state,score,volumeAcceleration,tradeAcceleration,tradeZ,buyShare*100,quote5,extension*100);}
 }
 public static Result evaluate(List<Bar> bars,int at){
  Result r=new Result();if(at<WARMUP-1||at>=bars.size())return r;
  double baselineQ=0,baselineN=0,n5=0,b5=0;
  for(int i=at-124;i<=at;i++){
   Bar b=bars.get(i);if(!b.valid()||(i>at-124&&b.time-bars.get(i-1).time!=60000)){r.reason="Invalid or missing minute";return r;}
   if(i<=at-5){baselineQ+=b.quote;baselineN+=b.trades;}else{r.quote5+=b.quote;n5+=b.trades;b5+=b.buy;}
   if(i>=at-30&&i<at)r.resistance=Math.max(r.resistance,b.high);
  }
  Bar last=bars.get(at);r.closedAt=last.time+59999;
  r.state="WAIT";r.reason="Flow conditions incomplete";
  if(baselineQ<=0||baselineN<=0||r.quote5<=0)return r;
  r.volumeAcceleration=r.quote5/(baselineQ/24);r.tradeAcceleration=n5/(baselineN/24);r.buyShare=b5/r.quote5;
  r.extension=last.close/r.resistance-1;r.change5=last.close/bars.get(at-4).open-1;
  // Trade intensity z-score: compare 5-minute trade count with 24 non-overlapping historical windows.
  double mean=baselineN/24.0,variance=0;
  for(int j=0;j<24;j++){double count=0;for(int k=0;k<5;k++)count+=bars.get(at-124+j*5+k).trades;variance+=(count-mean)*(count-mean);}
  double sd=Math.sqrt(variance/24.0);
  r.tradeZ=sd>0?(n5-mean)/sd:0;
  r.flowImbalance=2*r.buyShare-1;
  r.score=(r.volumeAcceleration>=2.5?25:0)+(r.tradeAcceleration>=1.8?20:0)
    +(r.tradeZ>=2?15:0)+(r.buyShare>=.55?15:0)+(r.quote5>=25000?10:0)
    +(r.extension>=-.005&&r.extension<=.006?15:0);
  boolean activity=r.volumeAcceleration>=2.5&&(r.tradeAcceleration>=1.8||r.tradeZ>=2.5);
  boolean flow=activity&&r.buyShare>=.55;
  boolean liquidity=r.quote5>=25000; // Turnover proxy only; not historical order-book liquidity.
  boolean near=r.extension>=-.005;
  if(activity&&liquidity&&near){
   r.state="EARLY_WATCH";
   r.reason=flow?"Early activity and buyer flow; awaiting breakout":"Volume and trades accelerating; buyer confirmation pending";
  }
  if(flow&&liquidity&&near){r.state="WATCH";r.reason="Pre-breakout buyer flow detected";}
  if(flow&&liquidity&&r.extension>=.001&&r.extension<=.006&&r.change5<=.015&&r.change5>=0&&last.high/last.low-1<=.012){r.state="BUY";r.reason="Closed-minute breakout with sustained flow";}
  if(activity&&liquidity&&near&&(r.extension>.006||r.change5>.015)){r.state="TOO_LATE";r.reason="Price extended; no chase";}
  return r;
 }
}
