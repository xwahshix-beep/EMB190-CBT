package com.wahshi.cryptoexplosionradar;
import java.util.*;
import java.util.concurrent.TimeUnit;
import okhttp3.*;
import org.json.*;

/** Public Binance stream only; no keys, orders, or private account access. */
public final class MinuteStreams {
    private static final OkHttpClient CLIENT=new OkHttpClient.Builder().connectTimeout(5,TimeUnit.SECONDS).readTimeout(0,TimeUnit.MILLISECONDS).build();
    private static final Map<String,MinuteHistory> histories=new HashMap<>();
    private static Set<String> symbols=new TreeSet<>();
    private static WebSocket socket;
    private static long generation,lastConnect,lastMessage,retryAfter;
    private static boolean enabled,connected;
    private MinuteStreams(){}
    public static synchronized void start(){enabled=true;}
    public static synchronized void stop(){enabled=false;generation++;if(socket!=null)socket.cancel();socket=null;connected=false;histories.clear();symbols.clear();retryAfter=0;}
    public static synchronized void ensure(List<String> universe,long now){
        if(!enabled)return;
        Set<String> wanted=new TreeSet<>(universe);
        // Bound one connection below Binance's stream limit; REST remains available for the remainder.
        if(wanted.size()>900){wanted=new TreeSet<>(new ArrayList<>(wanted).subList(0,900));}
        boolean healthy=socket!=null&&now-Math.max(lastMessage,lastConnect)<45000;
        if(healthy&&(wanted.equals(symbols)||now-lastConnect<60000))return;
        if(now<retryAfter)return;
        generation++;final long own=generation;
        if(socket!=null)socket.cancel();socket=null;connected=false;
        symbols=wanted;histories.keySet().retainAll(new HashSet<>(universe));
        if(wanted.isEmpty())return;
        StringBuilder streams=new StringBuilder();for(String s:wanted){if(streams.length()>0)streams.append('/');streams.append(s.toLowerCase(Locale.US)).append("@kline_1m");}
        lastConnect=now;lastMessage=0;retryAfter=now+15000;
        socket=CLIENT.newWebSocket(new Request.Builder().url("wss://stream.binance.com:9443/stream?streams="+streams).build(),new WebSocketListener(){
            @Override public void onOpen(WebSocket ws,Response response){synchronized(MinuteStreams.class){if(own!=generation||!enabled){ws.cancel();return;}connected=true;}}
            @Override public void onMessage(WebSocket ws,String message){synchronized(MinuteStreams.class){
                if(own!=generation||!enabled)return;
                try{JSONObject data=new JSONObject(message).getJSONObject("data");JSONObject k=data.getJSONObject("k");String s=k.getString("s");
                    if(!symbols.contains(s))return;
                    lastMessage=System.currentTimeMillis();histories.computeIfAbsent(s,key->new MinuteHistory()).stream(k,lastMessage);
                }catch(Exception ignored){} // Bad packets never become usable candles.
            }}
            @Override public void onFailure(WebSocket ws,Throwable error,Response response){lost();}
            @Override public void onClosing(WebSocket ws,int code,String reason){ws.close(code,reason);lost();}
            @Override public void onClosed(WebSocket ws,int code,String reason){lost();}
            private void lost(){synchronized(MinuteStreams.class){if(own!=generation)return;connected=false;socket=null;retryAfter=System.currentTimeMillis()+60000;}}
        });
    }
    public static synchronized JSONArray snapshot(String symbol,long now){try{MinuteHistory h=histories.get(symbol);return h==null?null:h.snapshot(now);}catch(Exception e){return null;}}
    public static synchronized void seed(String symbol,JSONArray rows,long now)throws Exception{if(enabled)histories.computeIfAbsent(symbol,key->new MinuteHistory()).merge(rows,now);}
    public static synchronized String status(long now){return "WebSocket: "+(connected?"متصل":"غير متصل / إعادة محاولة")+" • آخر رسالة: "+(lastMessage==0?"لم تصل":Math.max(0,(now-lastMessage)/1000)+" ث");}
}
