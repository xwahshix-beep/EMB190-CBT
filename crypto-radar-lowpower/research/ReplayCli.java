package com.wahshi.cryptoexplosionradar;
import java.io.*;
public final class ReplayCli {
 public static void main(String[] args)throws Exception{
  StringBuilder summary=new StringBuilder(HistoricalReplay.HEADER),alerts=new StringBuilder("symbol,variant,time_ms,outcome,net_return,volume_acceleration,trade_acceleration,buy_share,quote5\n");
  for(String s:HistoricalReplay.SYMBOLS){File f=new File(args[0],s+".csv.gz");if(!f.isFile())throw new IOException("Missing "+f);try(Reader r=new InputStreamReader(new java.util.zip.GZIPInputStream(new FileInputStream(f)),"UTF-8")){summary.append(HistoricalReplay.run(s,HistoricalReplay.read(r),alerts));}}
  try(FileWriter w=new FileWriter(args[1])){w.write(summary.toString());}try(FileWriter w=new FileWriter(args[2])){w.write(alerts.toString());}System.out.print(summary);
 }
}
