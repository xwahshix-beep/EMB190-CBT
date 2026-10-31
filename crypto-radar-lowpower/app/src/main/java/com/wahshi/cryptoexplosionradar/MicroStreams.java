package com.wahshi.cryptoexplosionradar;
import android.content.SharedPreferences;
import java.util.*;
import java.util.concurrent.*;
import okhttp3.*;
import org.json.*;

/** Separate, bounded stream: top 20 candidates, aggregated trades + complete depth20 snapshots. */
public final class MicroStreams {
 private static final OkHttpClient CLIENT=new OkHttpClient.Builder().connectTimeout(5,TimeUnit.SECONDS).readTimeout(0,TimeUnit.MILLISECONDS).build();
 private static MicroResearch research;private static SharedPreferences prefs;
 private static WebSocket socket;private static ScheduledExecutorService timer;
 private static List<String> wanted=new ArrayList<>(),active=new ArrayList<>();
 private static long generation,lastMessage,lastConnect,lastSave,retryAfter;private static int failures;
 private MicroStreams(){}
 public static synchronized void start(SharedPreferences p){
  if(timer!=null)return;prefs=p;research=new MicroResearch(p.getString(MicroResearch.KEY,"{}"));
  MinuteStreams.observer=(symbol,close,price,now)->{synchronized(MicroStreams.class){if(research==null)return;try{research.minute(symbol,close,price,now);}catch(Exception e){error(e);}}};
  timer=Executors.newSingleThreadScheduledExecutor();timer.scheduleWithFixedDelay(()->{synchronized(MicroStreams.class){try{maintain(System.currentTimeMillis());}catch(Exception e){error(e);}}},0,5,TimeUnit.SECONDS);
 }
 public static synchronized void select(List<String> ranked){wanted=new ArrayList<>(ranked.subList(0,Math.min(20,ranked.size())));}
 public static synchronized void baseline(String s,String old,String flow,long now){if(research!=null)research.baseline(s,old,flow,now);}
 private static void persist(long now)throws Exception{if(prefs!=null&&research!=null){prefs.edit().putString(MicroResearch.KEY,research.save(now)).apply();lastSave=now;}}
 private static void error(Exception e){if(prefs!=null)prefs.edit().putString("micro_error",e.getClass().getSimpleName()).apply();}
 public static synchronized void stop(){
  generation++;if(timer!=null)timer.shutdownNow();timer=null;if(socket!=null)socket.cancel();socket=null;
  try{if(research!=null){research.reset(System.currentTimeMillis());persist(System.currentTimeMillis());}}catch(Exception e){error(e);}
  research=null;wanted.clear();active.clear();MinuteStreams.observer=null;retryAfter=0;failures=0;
 }
 private static void maintain(long now)throws Exception{
  if(research==null)return;
  if(socket!=null&&now-Math.max(lastConnect,lastMessage)>15000)disconnect(now);
  if(socket!=null&&!new HashSet<>(wanted).equals(new HashSet<>(active))&&now-lastConnect>=900000){generation++;socket.cancel();socket=null;research.reset(now);retryAfter=now;}
  if(socket==null&&!wanted.isEmpty()&&now>=retryAfter)connect(now);
  if(now-lastSave>=10000)persist(now);
 }
 private static void disconnect(long now)throws Exception{generation++;if(socket!=null)socket.cancel();socket=null;research.reset(now);retryAfter=now+RetryPolicy.delay(++failures,Math.random());}
 private static void connect(long now)throws Exception{
  active=new ArrayList<>(wanted);research.select(active,now);lastConnect=now;lastMessage=0;final long own=++generation;
  StringBuilder streams=new StringBuilder();for(String s:active){if(streams.length()>0)streams.append('/');String name=s.toLowerCase(Locale.US);streams.append(name).append("@aggTrade/").append(name).append("@depth20");}
  socket=CLIENT.newWebSocket(new Request.Builder().url("wss://stream.binance.com:9443/stream?streams="+streams).build(),new WebSocketListener(){
   @Override public void onOpen(WebSocket ws,Response response){synchronized(MicroStreams.class){if(own!=generation||research==null){ws.cancel();return;}try{research.connected(System.currentTimeMillis());}catch(Exception e){error(e);}}}
   @Override public void onMessage(WebSocket ws,String msg){synchronized(MicroStreams.class){if(own!=generation||research==null)return;try{
    JSONObject packet=new JSONObject(msg),d=packet.optJSONObject("data");if(d==null)d=packet;
    long now=System.currentTimeMillis();if("serverShutdown".equals(d.optString("e"))){disconnect(now);return;}
    String stream=packet.optString("stream");int split=stream.indexOf('@');if(split<0)return;String s=stream.substring(0,split).toUpperCase(Locale.US);if(!active.contains(s))return;
    if("aggTrade".equals(d.optString("e")))research.trade(s,d.getLong("a"),d.getLong("T"),now,d.getDouble("p"),d.getDouble("q"),d.getBoolean("m"));
    else if(stream.contains("@depth20"))research.book(s,d.getLong("lastUpdateId"),d.getJSONArray("bids"),d.getJSONArray("asks"),now);
    else return;lastMessage=now;if(now-lastConnect>60000)failures=0;
   }catch(Exception e){error(e);}}}
   @Override public void onFailure(WebSocket ws,Throwable t,Response r){lost();}
   @Override public void onClosing(WebSocket ws,int code,String reason){ws.close(code,reason);lost();}
   @Override public void onClosed(WebSocket ws,int code,String reason){lost();}
   private void lost(){synchronized(MicroStreams.class){if(own!=generation||research==null)return;try{disconnect(System.currentTimeMillis());}catch(Exception e){error(e);}}}
  });
 }
 public static synchronized String snapshot(){try{if(research!=null)return research.save(System.currentTimeMillis());}catch(Exception e){error(e);}return prefs==null?"{}":prefs.getString(MicroResearch.KEY,"{}");}
}
