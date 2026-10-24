package com.wahshi.cryptoexplosionradar;
import org.json.*;
public final class StreamRecoveryTest {
 static void check(boolean v,String m){if(!v)throw new AssertionError(m);}
 public static void main(String[] args)throws Exception {
  StreamRecovery r=new StreamRecovery();long now=1800000000000L;
  long old=r.epoch();r.invalidate(now);check(r.epoch()!=old,"old requests invalidated");
  check(!r.ready(null),"no history blocks");
  JSONArray rows=new JSONArray();for(int i=0;i<125;i++)rows.put(new JSONArray().put(0).put(0).put(0).put(0).put(0).put(0).put(now-60001));
  check(!r.ready(rows),"pre-gap history blocks");rows.getJSONArray(124).put(6,now-1);check(r.ready(rows),"latest closed history accepted");
  r.invalidate(now+120000);check(!r.ready(rows),"second disconnect invalidates recovered history");
  check("serverShutdown".equals(StreamRecovery.payload("{\"e\":\"serverShutdown\"}").getString("e")),"raw shutdown");
  check("serverShutdown".equals(StreamRecovery.payload("{\"data\":{\"e\":\"serverShutdown\"}}").getString("e")),"combined shutdown");
  System.out.println("StreamRecoveryTest passed");
 }
}
