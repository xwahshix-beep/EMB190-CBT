package com.wahshi.cryptoexplosionradar;
/** Explicit experimental thresholds; no profitability claim. */
public final class WatchPolicy {
 public static String state(double price,double resistance,double change5,double rvol,double taker,double book,double spread,boolean confirm3,boolean confirm5) {
  if(!Double.isFinite(price+resistance+change5+rvol+taker+book+spread)||price<=0||resistance<=0) return "DATA";
  if(change5>2.0 || price>resistance*1.008) return "TOO_LATE";
  boolean flow=rvol>=1.5 && taker>=.58 && book>=.52 && spread<=15;
  if(flow && price>resistance*1.001 && confirm3 && confirm5) return "BUY";
  if(flow && price>=resistance*.992) return "WATCH";
  return "WAIT";
 }
}
