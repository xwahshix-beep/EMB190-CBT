package com.wahshi.cryptoexplosionradar;
import java.io.*;import java.util.*;import java.util.zip.*;
/** Runs the SAME frozen Java signal/guard, with an explicitly hypothetical execution model. */
public final class LongReplay {
 static double net(double entry,double exit,double slip){return 100*(exit*(1-slip)*.999/(entry*(1+slip)*1.001)-1);}
 static boolean guard(List<PreExplosionEngine.Bar>b,int i){
  double resistance=0;for(int k=i-12;k<i;k++)resistance=Math.max(resistance,b.get(k).high);
  PreExplosionEngine.Bar x=b.get(i);double p=x.close;
  return p>=resistance*1.001&&p<=resistance*1.006&&EntryGuard.reason(p,resistance,b.get(i-2).open,b.get(i-4).open,b.get(i-14).open,b.get(i-44).open,x.high,x.low,x.time+60000,x.time+59999)==null;
 }
 static boolean path(List<PreExplosionEngine.Bar>b,int i){
  if(i+64>=b.size())return false;
  for(int k=i;k<=i+64;k++)if(!b.get(k).valid()||(k>i&&b.get(k).time-b.get(k-1).time!=60000))return false;return true;
 }
 static boolean target(List<PreExplosionEngine.Bar>b,int i){for(int k=i+1;k<=i+60;k++)if(b.get(k).close>=b.get(i).close*1.02)return true;return false;}
 public static void main(String[]args)throws Exception{
  File folder=new File(args[0]);File[]files=folder.listFiles((d,n)->n.endsWith(".zip"));Arrays.sort(files);
  try(PrintWriter out=new PrintWriter(args[1]);PrintWriter coverage=new PrintWriter(args[2])){
   out.println("symbol,month,variant,at,price,net60,stress60,baseline60,baselineStress,m5,m15,m60,target,missing");
   coverage.println("symbol,month,variant,bars,eligible,signals,missing,events,detected,missed");
   for(File f:files){String symbol=f.getName().split("-1m-")[0],month=f.getName().substring(f.getName().indexOf("-1m-")+4,f.getName().length()-4);
    List<PreExplosionEngine.Bar>b=new ArrayList<>();
    try(ZipInputStream z=new ZipInputStream(new FileInputStream(f))){z.getNextEntry();BufferedReader r=new BufferedReader(new InputStreamReader(z,"UTF-8"));String line;while((line=r.readLine())!=null){if(line.startsWith("open_time"))continue;b.add(PreExplosionEngine.Bar.csv(line));}}
    for(int i=1;i<b.size();i++)if(b.get(i).time<=b.get(i-1).time)throw new IllegalStateException("unordered "+f);
    List<Integer>eligible=new ArrayList<>();boolean[]valid=new boolean[b.size()],targets=new boolean[b.size()];
    for(int i=124;i<b.size();i++){valid[i]=path(b,i);if(valid[i]){eligible.add(i);targets[i]=target(b,i);}}
    boolean[][]signals=new boolean[2][b.size()];long[]last={0,0};int[]count={0,0},missing={0,0};
    Random[]rng={new Random(20261024L+symbol.hashCode()+month.hashCode()),new Random(20261025L+symbol.hashCode()+month.hashCode())};
    for(int i=124;i<b.size();i++){
     PreExplosionEngine.Result result=PreExplosionEngine.evaluate(b,i);if(!"BUY".equals(result.state))continue;
     for(int v=0;v<2;v++){
      if(v==1&&!guard(b,i))continue;long time=b.get(i).time+60000;if(time-last[v]<21600000)continue;last[v]=time;count[v]++;signals[v][i]=true;
      if(!valid[i]){missing[v]++;out.printf(Locale.US,"%s,%s,%d,%d,,,,,,,,,,1%n",symbol,month,v,time);continue;}
      double entry=b.get(i+2).open,exit=b.get(i+62).open,stressEntry=b.get(i+4).open,stressExit=b.get(i+64).open,base=0,stressBase=0;
      for(int j=0;j<20;j++){int x=eligible.get(rng[v].nextInt(eligible.size()));base+=net(b.get(x+2).open,b.get(x+62).open,.0005)/20;stressBase+=net(b.get(x+4).open,b.get(x+64).open,.0015)/20;}
      out.printf(Locale.US,"%s,%s,%d,%d,%.12g,%.10f,%.10f,%.10f,%.10f,%.10f,%.10f,%.10f,%d,0%n",symbol,month,v,time,entry,net(entry,exit,.0005),net(stressEntry,stressExit,.0015),base,stressBase,100*(b.get(i+7).open/entry-1),100*(b.get(i+17).open/entry-1),100*(exit/entry-1),targets[i]?1:0);
     }
    }
    for(int v=0;v<2;v++){int events=0,detected=0;long prev=0;
     for(int i:eligible){if(!targets[i]||b.get(i).time-prev<3600000)continue;prev=b.get(i).time;events++;boolean hit=false;for(int k=Math.max(124,i-15);k<=i;k++)if(signals[v][k])hit=true;if(hit)detected++;}
     coverage.printf(Locale.US,"%s,%s,%d,%d,%d,%d,%d,%d,%d,%d%n",symbol,month,v,b.size(),eligible.size(),count[v],missing[v],events,detected,events-detected);
    }out.flush();coverage.flush();System.out.println(f.getName()+" bars="+b.size()+" signals="+count[0]+"/"+count[1]);
   }
  }
 }
}
