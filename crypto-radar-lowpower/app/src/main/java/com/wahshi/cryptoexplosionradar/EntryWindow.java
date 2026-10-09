package com.wahshi.cryptoexplosionradar;
import org.json.JSONArray;
/** Uses contiguous CLOSED 1m candles. No in-progress bar in the signal baseline. */
public final class EntryWindow {
 public double resistance,base3,base5,base15,base45,high,low;
 public long closedAt;
 public static EntryWindow parse(JSONArray raw,long now)throws Exception{
  JSONArray closed=new JSONArray();
  for(int i=0;i<raw.length();i++){JSONArray row=raw.getJSONArray(i);if(row.getLong(6)<now)closed.put(row);}
  return fromClosed(closed,now);
 }
 public static EntryWindow fromClosed(JSONArray rows,long now)throws Exception{
  int n=rows.length();if(n<46)throw new Exception("سجل الدقيقة غير مكتمل");
  long previous=0;
  for(int i=n-46;i<n;i++){
   JSONArray r=rows.getJSONArray(i);long open=r.getLong(0),end=r.getLong(6);
   if(end-open!=59999||end>=now||(previous>0&&open-previous!=60000))throw new Exception("فجوة في بيانات الدقيقة");
   double h=r.getDouble(2),l=r.getDouble(3),c=r.getDouble(4);
   if(!Double.isFinite(h+l+c)||l<=0||h<l||c<l||c>h)throw new Exception("بيانات شموع غير صالحة");
   previous=open;
  }
  EntryWindow w=new EntryWindow();JSONArray last=rows.getJSONArray(n-1);
  w.closedAt=last.getLong(6);w.high=last.getDouble(2);w.low=last.getDouble(3);
  w.base3=rows.getJSONArray(n-3).getDouble(1);w.base5=rows.getJSONArray(n-5).getDouble(1);
  w.base15=rows.getJSONArray(n-15).getDouble(1);w.base45=rows.getJSONArray(n-45).getDouble(1);
  for(int i=n-13;i<n-1;i++)w.resistance=Math.max(w.resistance,rows.getJSONArray(i).getDouble(2));
  return w;
 }
 public String reason(double price,long now){return EntryGuard.reason(price,resistance,base3,base5,base15,base45,high,low,now,closedAt);}
 public double entryLow(){return resistance*1.001;}
 public double entryHigh(){return resistance*(1+EntryGuard.MAX_EXTENSION);}
}
