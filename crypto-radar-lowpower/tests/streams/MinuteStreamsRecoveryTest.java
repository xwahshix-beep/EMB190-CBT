package com.wahshi.cryptoexplosionradar;
import okhttp3.*;import org.json.*;import java.util.*;
public class MinuteStreamsRecoveryTest {
 static void check(boolean v,String m){if(!v)throw new AssertionError(m);}
 static JSONArray history(long now)throws Exception {JSONArray a=new JSONArray();long end=now/60000*60000;for(int i=130;i>0;i--){long t=end-i*60000;a.put(new JSONArray().put(t).put(1).put(1.001).put(.999).put(1).put(100).put(t+59999).put(100).put(20).put(60).put(60).put(0));}return a;}
 public static void main(String[] args)throws Exception {
  MinuteStreams.start();long now=System.currentTimeMillis();MinuteStreams.ensure(Arrays.asList("BTCUSDT"),now);
  OkHttpClient.Fake old=OkHttpClient.sockets.get(0);old.listener.onOpen(old,new Response());
  long epoch=MinuteStreams.epoch();JSONArray rows=history(now);check(MinuteStreams.seed("BTCUSDT",rows,now,epoch),"initial seed");check(MinuteStreams.ready("BTCUSDT",now),"initial ready");
  old.listener.onMessage(old,"{\"e\":\"serverShutdown\"}");
  check(old.cancelled&&OkHttpClient.sockets.size()==2,"immediate reconnect");
  check(!MinuteStreams.ready("BTCUSDT",now),"shutdown blocks cached history");
  check(!MinuteStreams.seed("BTCUSDT",rows,now,epoch),"old REST rejected");
  old.listener.onFailure(old,new Exception(),null);check(OkHttpClient.sockets.size()==2,"old callback cannot disrupt replacement");
  long next=MinuteStreams.epoch();MinuteStreams.seed("BTCUSDT",history(System.currentTimeMillis()),System.currentTimeMillis(),next);check(MinuteStreams.ready("BTCUSDT",System.currentTimeMillis()),"REST recovery unblocks only valid symbol");
  check(!MinuteStreams.ready("ETHUSDT",System.currentTimeMillis()),"unrecovered symbol blocked");
  OkHttpClient.Fake current=OkHttpClient.sockets.get(1);current.listener.onMessage(current,"{\"data\":{\"e\":\"serverShutdown\"}}");check(OkHttpClient.sockets.size()==3,"combined shutdown");
  MinuteStreams.stop();check(!MinuteStreams.seed("BTCUSDT",rows,now,next),"stopped seed rejected");
  System.out.println("MinuteStreamsRecoveryTest passed: reconnect, stale callbacks, stale REST, recovery gate, raw/combined events");
 }
}
