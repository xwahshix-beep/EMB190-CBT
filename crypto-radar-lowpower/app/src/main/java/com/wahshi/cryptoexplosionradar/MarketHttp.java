package com.wahshi.cryptoexplosionradar;
import javax.net.ssl.HttpsURLConnection;
import java.io.*;
import java.net.URL;
public final class MarketHttp {
 private static volatile long retryAt;
 public static long retryDelay(){return Math.max(0,retryAt-System.currentTimeMillis());}
 public static String read(String url)throws Exception{
  if(Thread.currentThread().isInterrupted())throw new IOException("الفحص متوقف");
  if(retryDelay()>0)throw new IOException("انتظار حدود طلبات Binance");
  HttpsURLConnection c=(HttpsURLConnection)new URL(url).openConnection();
  c.setConnectTimeout(2000);c.setReadTimeout(2500);
  try{
   int code=c.getResponseCode();
   if(code==429||code==418){long seconds=120;try{seconds=Math.max(seconds,Long.parseLong(c.getHeaderField("Retry-After")));}catch(Exception ignored){}
    retryAt=System.currentTimeMillis()+seconds*1000;throw new IOException("انتظار حدود طلبات Binance");}
   if(code!=200)throw new IOException("Binance HTTP "+code);
   try(BufferedReader r=new BufferedReader(new InputStreamReader(c.getInputStream(),"UTF-8"))){StringBuilder s=new StringBuilder();String l;while((l=r.readLine())!=null)s.append(l);return s.toString();}
  }finally{c.disconnect();}
 }
}
