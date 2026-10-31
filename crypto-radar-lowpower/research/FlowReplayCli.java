package com.wahshi.cryptoexplosionradar;
import java.io.*;import java.util.*;import java.util.zip.GZIPInputStream;import org.json.*;
/** Exploratory fixed-universe replay. Next-minute open, 15-minute hold, fixed costs.
 * Signals separated by 15 minutes per symbol/model; not a portfolio or forward validation. */
public final class FlowReplayCli {
 public static void main(String[] args)throws Exception{
  JSONArray results=new JSONArray();File[] files=new File(args[0]).listFiles((d,n)->n.endsWith(".csv.bin"));if(files==null||files.length==0)throw new IOException("No replay files");Arrays.sort(files);
  long barsCount=0;for(File f:files){List<PreExplosionEngine.Bar> bars;
   try(Reader r=new InputStreamReader(new GZIPInputStream(new FileInputStream(f)),"UTF-8")){bars=HistoricalReplay.read(r);}barsCount+=bars.size();
   for(String model:new String[]{"baseline","transition","fixed_15m_control"}){
    int observations=0,wins=0;double gross=0,net=0,stress=0;int last=-1000;
    for(int i=124;i+17<bars.size();i++){
     boolean signal;
     if(model.equals("baseline")){String s=PreExplosionEngine.evaluate(bars,i).state;signal=s.equals("EARLY_WATCH")||s.equals("WATCH")||s.equals("BUY");}
     else if(model.equals("transition"))signal=FlowTransitionEngine.evaluate(bars,i).candidate();
     else signal=(bars.get(i).time/60000)%15==14;
     if(!signal||i-last<15)continue;
     boolean valid=true;for(int k=i+1;k<=i+17;k++)if(!bars.get(k).valid()||bars.get(k).time-bars.get(k-1).time!=60000)valid=false;if(!valid)continue;
     double entry=bars.get(i+1).open,exit=bars.get(i+15).close;
     double g=100*(exit/entry-1),n=100*(exit*(1-.0015)/(entry*(1+.0015))-1);
     double st=100*(bars.get(i+17).close*(1-.0025)/(bars.get(i+3).open*(1+.0025))-1);
     observations++;if(n>0)wins++;gross+=g;net+=n;stress+=st;last=i;
    }
    results.put(new JSONObject().put("symbol",f.getName().replace(".csv.bin","")).put("model",model).put("observations",observations).put("netPositive",wins)
      .put("meanGrossPct",observations==0?JSONObject.NULL:gross/observations).put("meanNetPct",observations==0?JSONObject.NULL:net/observations).put("meanStressPct",observations==0?JSONObject.NULL:stress/observations));
   }
  }
  JSONObject out=new JSONObject().put("rules",FlowTransitionEngine.RULES).put("bars",barsCount).put("symbols",files.length).put("productionBuyEnabled",false)
    .put("protocol","All bundled files, frozen rules; historical exploratory sample, not independent validation. Discovery comparison, no executable BUY claim. 15-minute spacing per symbol/model; next-minute open to minute 15 close; fee 0.10% + slippage 0.05% each side. Stress: two extra minutes delay, same 15-minute holding period, fee 0.10% + slippage 0.15% each side. No spread/depth/fill simulation. Fixed 15-minute control is descriptive, not matched causal treatment. Correlated observations; no portfolio return.")
    .put("results",results);
  try(Writer w=new FileWriter(args[1])){w.write(out.toString(2));}System.out.println("Flow replay: "+barsCount+" bars / "+files.length+" symbols; "+results.length()+" comparisons");
 }
}
