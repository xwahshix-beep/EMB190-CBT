package com.wahshi.cryptoexplosionradar;
import java.util.*;
/** Causal minute-scale challenger. Fixed experimental rules, not calibrated probabilities.
 * Baseline excludes the entire eight-minute detection window. No depth/whale claims. */
public final class FlowTransitionEngine {
 public static final String RULES="flow-transition-1";
 public static final int WARMUP=125;
 public static final class Result {
  public String state="DATA",reason="Incomplete history",path="NONE";
  public long closedAt; public double price,changeZ,volumeRatio,tradeRatio,buyShare,persistence,response,extension,range,baselineBuy;
  public boolean candidate(){return state.equals("EARLY_WATCH")||state.equals("FLOW_READY");}
  public String detail(){return String.format(Locale.US,"%s • %s • change %.2f • volume %.2fx • trades %.2fx • buy %.1f%% • persistence %.0f%% • response %+.3f%% • extension %.3f%% • %s",state,path,changeZ,volumeRatio,tradeRatio,buyShare*100,persistence*100,response*100,extension*100,reason);}
 }
 static double median(double[] a){double[] b=a.clone();Arrays.sort(b);int n=b.length;return n%2==0?(b[n/2-1]+b[n/2])/2:b[n/2];}
 static double scale(double[] a,double center){double[] d=new double[a.length];for(int i=0;i<a.length;i++)d[i]=Math.abs(a[i]-center);return Math.max(.20,1.4826*median(d));}
 public static Result evaluate(List<PreExplosionEngine.Bar> bars,int at){
  Result r=new Result();if(at<WARMUP-1||at>=bars.size())return r;
  int start=at-124,baseEnd=at-8,n=117;
  double[] q=new double[n],t=new double[n],ranges=new double[n];double quote=0,buy=0;
  for(int i=start;i<=at;i++){
   PreExplosionEngine.Bar b=bars.get(i);
   if(!b.valid()||b.time%60000!=0||(i>start&&b.time-bars.get(i-1).time!=60000)){r.reason="Invalid or missing minute";return r;}
   if(i<=baseEnd){int j=i-start;q[j]=Math.log1p(b.quote);t[j]=Math.log1p(b.trades);ranges[j]=(b.high-b.low)/b.close;quote+=b.quote;buy+=b.buy;}
  }
  PreExplosionEngine.Bar last=bars.get(at);r.closedAt=last.time+59999;r.price=last.close;
  if(quote<=0){r.reason="No baseline turnover";return r;}
  double cq=median(q),ct=median(t),sq=scale(q,cq),st=scale(t,ct);
  r.range=Math.max(.001,median(ranges));r.baselineBuy=buy/quote;
  double cusum=0,q3=0,n3=0,b3=0,resistance=0;int persistent=0;
  for(int i=at-29;i<=at;i++){
   PreExplosionEngine.Bar b=bars.get(i);
   if(i<at)resistance=Math.max(resistance,b.high);
   if(i>baseEnd){
    double zq=(Math.log1p(b.quote)-cq)/sq,zn=(Math.log1p(b.trades)-ct)/st;
    // Both turnover and actual trade count must change. Cap a single shock's contribution.
    cusum=Math.max(0,cusum+Math.max(-4,Math.min(4,Math.min(zq,zn)))-.75);
   }
   if(i>=at-2){q3+=b.quote;n3+=b.trades;b3+=b.buy;if(b.quote>0&&b.buy/b.quote>=.56)persistent++;}
  }
  r.changeZ=cusum;r.volumeRatio=q3/(3*Math.max(1,Math.expm1(cq)));r.tradeRatio=n3/(3*Math.max(1,Math.expm1(ct)));
  r.buyShare=q3>0?b3/q3:0;r.persistence=persistent/3.0;r.response=last.close/bars.get(at-2).open-1;r.extension=last.close/resistance-1;
  boolean activity=cusum>=5&&r.volumeRatio>=1.8&&r.tradeRatio>=1.5;
  boolean flow=r.buyShare>=.58&&persistent>=2&&r.buyShare-r.baselineBuy>=.05;
  double lastQ=last.quote/Math.max(1,Math.expm1(cq)),lastN=last.trades/Math.max(1,Math.expm1(ct));
  boolean sudden=lastQ>=4&&lastN>=3&&last.quote>0&&last.buy/last.quote>=.60;
  r.path=sudden?"IMPULSE":r.extension>=0?"BREAKOUT":"BUILDUP";
  r.state="WAIT";r.reason="No joint activity transition";
  if(!(activity||sudden))return r;
  if(q3<15000){r.reason="Insufficient three-minute turnover";return r;}
  double cap=Math.min(.015,Math.max(.004,3*r.range));
  if(r.response>cap||r.extension>Math.min(.008,2*r.range)) {r.state="TOO_LATE";r.reason="Extended price; wait for a new setup";return r;}
  if(r.response < -Math.max(.002,r.range)){r.state="WAIT";r.reason="Adverse price response despite activity";return r;}
  r.state="EARLY_WATCH";r.reason="Activity changed; buyer persistence pending";
  if(!flow)return r;
  if(r.response<=.1*r.range){r.state="ABSORPTION_RISK";r.reason="Persistent buying without price progress; depth not measured";return r;}
  if(r.extension<-.01){r.reason="Buyer pressure below nearby resistance";return r;}
  r.state="FLOW_READY";r.reason="Persistent buyer flow with price progress; research signal, not BUY";return r;
 }
}
