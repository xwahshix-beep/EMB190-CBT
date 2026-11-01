package com.wahshi.cryptoexplosionradar;
public final class AlertDispatch {
 public interface Sink{void send(String title,String body);}
 public static boolean send(boolean gate,String symbol,String detail,Sink sink){
  if(!gate)return false;
  sink.send("رُصد تحقق شروط بحثية — "+symbol,"بحث غير مثبت؛ ليس توصية تداول.\n"+detail);
  return true;
 }
}
