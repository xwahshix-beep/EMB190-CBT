package com.wahshi.cryptoexplosionradar;
public class WakeMetricsTest {
 public static void main(String[] args){
 WakeMetrics m=new WakeMetrics();m.released(100);m.acquired(100,90000);m.released(600);
 if(m.totalMs()!=500||m.cycles()!=1)throw new AssertionError("duration");
 m.released(700);if(m.cycles()!=1)throw new AssertionError("double release");
 m.acquired(1000,90000);m.released(100000);if(m.totalMs()!=90500||m.expired()!=1)throw new AssertionError("timeout cap");
 m.acquired(200000,90000);m.released(199999);if(m.totalMs()!=90500)throw new AssertionError("negative duration");
 m.acquired(300000,90000);boolean caught=false;try{m.acquired(300001,90000);}catch(IllegalStateException e){caught=true;}if(!caught)throw new AssertionError("double acquire");
 System.out.println("WakeMetricsTest passed");
 }
}
