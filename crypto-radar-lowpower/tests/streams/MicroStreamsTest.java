package com.wahshi.cryptoexplosionradar;
import android.content.SharedPreferences;import okhttp3.*;import org.json.*;import java.util.*;
public class MicroStreamsTest {
 static class Prefs implements SharedPreferences,SharedPreferences.Editor {
  Map<String,String> values=new HashMap<>();public String getString(String k,String f){return values.getOrDefault(k,f);}public SharedPreferences.Editor edit(){return this;}public SharedPreferences.Editor putString(String k,String v){values.put(k,v);return this;}public void apply(){}
 }
 static void check(boolean b,String m){if(!b)throw new AssertionError(m);}
 public static void main(String[] args)throws Exception{
  Prefs prefs=new Prefs();try{
   MicroStreams.start(prefs);java.lang.reflect.Method maintain=MicroStreams.class.getDeclaredMethod("maintain",long.class);maintain.setAccessible(true);
   synchronized(MicroStreams.class){MicroStreams.select(Arrays.asList("XUSDT"));maintain.invoke(null,System.currentTimeMillis());}
   OkHttpClient.Fake old=OkHttpClient.sockets.get(0);old.listener.onOpen(old,new Response());long now=System.currentTimeMillis();
   old.listener.onMessage(old,"{\"stream\":\"xusdt@depth20\",\"data\":{\"lastUpdateId\":1,\"bids\":[[\"99.99\",\"100\"]],\"asks\":[[\"100.01\",\"100\"]]}}");
   old.listener.onMessage(old,new JSONObject().put("stream","xusdt@aggTrade").put("data",new JSONObject().put("e","aggTrade").put("a",1).put("T",now).put("p","100").put("q","2").put("m",false)).toString());
   JSONObject snap=new JSONObject(MicroStreams.snapshot());check(snap.getJSONObject("latest").getJSONObject("XUSDT").getString("state").equals("WARMUP"),"combined trades and depth routed");
   old.listener.onFailure(old,new Exception(),null);check(old.cancelled,"failed socket cancelled");
   synchronized(MicroStreams.class){maintain.invoke(null,now+60000);}
   check(OkHttpClient.sockets.size()==2,"retry opens replacement");
   old.listener.onOpen(old,new Response());old.listener.onFailure(old,new Exception(),null);check(OkHttpClient.sockets.size()==2,"old callback cannot break replacement");
   check(MinuteStreams.observer!=null,"broad minute audit attached");
   MinuteStreams.observer.minute("YUSDT",now-1,100,now);
  }finally{MicroStreams.stop();}
  check(MinuteStreams.observer==null,"stop detaches audit");check(!new JSONObject(prefs.getString(MicroResearch.KEY,"{}")).getBoolean("connected"),"stop persists disconnected state");
  System.out.println("MicroStreamsTest passed: stream routing, failure recovery, generation guard, audit lifecycle and stop persistence");
 }
}
