package com.wahshi.cryptoexplosionradar;
public class DataHealthTest {
 public static void main(String[] args){
  check(DataHealth.state(0,0,1000,120).contains("لم تصل"));
  check(DataHealth.state(1000,1100,1100,120).contains("تعذر"));
  check(DataHealth.state(1200,1100,1250,120).contains("حديثة"));
  check(DataHealth.state(1200,1100,1321,120).contains("قديمة"));
  check(DataHealth.state(1200,0,1100,120).contains("غير متسق"));
  check(DataHealth.source("https://api.binance.com/api/v3/klines?symbol=BTCUSDT").equals("candles"));
  System.out.println("DataHealthTest passed");
 }
 static void check(boolean ok){if(!ok)throw new AssertionError();}
}
