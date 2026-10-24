package com.wahshi.cryptoexplosionradar;
import org.json.*;
/** Invalidates pre-disconnect requests and requires a contiguous, current history upstream. */
public final class StreamRecovery {
    private long epoch,requiredClose;
    public long epoch(){return epoch;}
    public void invalidate(long now){epoch++;requiredClose=(now/60000)*60000-1;}
    public boolean ready(JSONArray rows)throws Exception {
        return rows!=null&&rows.length()>=125&&rows.getJSONArray(rows.length()-1).getLong(6)>=requiredClose;
    }
    public static JSONObject payload(String message)throws Exception {
        JSONObject root=new JSONObject(message);JSONObject data=root.optJSONObject("data");return data==null?root:data;
    }
}
