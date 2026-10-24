package okhttp3;
import java.util.*;import java.util.concurrent.TimeUnit;
public class OkHttpClient {
 public static final List<Fake> sockets=new ArrayList<>();
 public static class Fake implements WebSocket {public final WebSocketListener listener;public boolean cancelled;Fake(WebSocketListener l){listener=l;}public void cancel(){cancelled=true;}public boolean close(int c,String r){return true;}}
 public WebSocket newWebSocket(Request r,WebSocketListener l){Fake f=new Fake(l);sockets.add(f);return f;}
 public static class Builder {public Builder connectTimeout(long n,TimeUnit u){return this;}public Builder readTimeout(long n,TimeUnit u){return this;}public OkHttpClient build(){return new OkHttpClient();}}
}
