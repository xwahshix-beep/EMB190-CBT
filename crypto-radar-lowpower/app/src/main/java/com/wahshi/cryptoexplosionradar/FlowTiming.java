package com.wahshi.cryptoexplosionradar;
import org.json.*;
/** Wall-clock differences are diagnostic, not network RTT; negative values flag clock skew. */
public final class FlowTiming {
 public final long closedAt,eventAt,receivedAt;public final String source;
 public FlowTiming(long c,long e,long r,String s){closedAt=c;eventAt=e;receivedAt=r;source=s;}
 public JSONObject json(long candle,long now)throws Exception{
  boolean match=candle==closedAt;
  return new JSONObject().put("source",match?source:"UNKNOWN").put("closedAt",candle).put("decisionAt",now).put("candleAgeMs",now-candle)
   .put("eventAt",match&&eventAt>0?eventAt:JSONObject.NULL).put("receivedAt",match?receivedAt:JSONObject.NULL)
   .put("sourceToReceiveMs",match&&eventAt>0?receivedAt-eventAt:JSONObject.NULL).put("decisionDelayMs",match?now-receivedAt:JSONObject.NULL)
   .put("clockSkew",now<candle||(match&&(now<receivedAt||(eventAt>0&&receivedAt<eventAt))));
 }
}
