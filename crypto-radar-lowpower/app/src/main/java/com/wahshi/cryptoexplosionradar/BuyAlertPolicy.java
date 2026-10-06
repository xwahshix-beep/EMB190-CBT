package com.wahshi.cryptoexplosionradar;
public final class BuyAlertPolicy {
 public static final long COOLDOWN=6L*60*60*1000;
 public static boolean validRange(double low,double high){return Double.isFinite(low)&&Double.isFinite(high)&&low>0&&high>=low;}
 public static boolean eligible(boolean enabled,boolean permitted,boolean confirmed,double price,double low,double high,long now,long quoteAt,long signalAt,long lastSent){
  return enabled&&permitted&&confirmed&&validRange(low,high)&&Double.isFinite(price)&&price>=low&&price<=high
   &&quoteAt>0&&now>=quoteAt&&now-quoteAt<=15000
   &&signalAt>0&&now>=signalAt&&now-signalAt<=600000
   &&(lastSent==0||(now>=lastSent&&now-lastSent>=COOLDOWN));
 }
}
