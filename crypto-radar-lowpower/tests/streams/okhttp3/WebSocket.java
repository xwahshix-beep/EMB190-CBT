package okhttp3;
public interface WebSocket { void cancel(); boolean close(int code,String reason); }
