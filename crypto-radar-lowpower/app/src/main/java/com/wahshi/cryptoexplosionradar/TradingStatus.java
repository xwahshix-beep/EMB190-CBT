package com.wahshi.cryptoexplosionradar;
public final class TradingStatus {
 private static final TradingEligibility CACHE=new TradingEligibility(
  ()->MarketHttp.read("https://api.binance.com/api/v3/exchangeInfo?showPermissionSets=false"),System::currentTimeMillis);
 private TradingStatus(){}
 public static String reason(String symbol){return CACHE.reason(symbol);}
 public static long checkedAt(){return CACHE.checkedAt();}
}
