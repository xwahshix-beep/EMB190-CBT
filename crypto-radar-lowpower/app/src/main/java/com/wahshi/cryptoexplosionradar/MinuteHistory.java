package com.wahshi.cryptoexplosionradar;
import java.util.*;
import org.json.*;

/** One closed-minute history. REST seeds and stream updates may arrive out of order. */
public final class MinuteHistory {
    private final TreeMap<Long,String> rows=new TreeMap<>();
    public synchronized void merge(JSONArray batch,long now)throws Exception {
        for(int i=0;i<batch.length();i++)put(batch.getJSONArray(i),now);
    }
    private void put(JSONArray row,long now)throws Exception {
        long start=row.getLong(0),end=row.getLong(6);
        if(start%60000!=0||end!=start+59999||end>=now)return;
        PreExplosionEngine.Bar b=new PreExplosionEngine.Bar(start,row.getDouble(1),row.getDouble(2),row.getDouble(3),row.getDouble(4),row.getDouble(7),row.getLong(8),row.getDouble(10));
        if(!b.valid())return;
        rows.put(start,row.toString());
        while(rows.size()>130)rows.pollFirstEntry();
    }
    public synchronized void stream(JSONObject k,long now)throws Exception {
        if(!k.getBoolean("x")||!"1m".equals(k.getString("i")))return;
        JSONArray row=new JSONArray();
        row.put(k.getLong("t")).put(k.getString("o")).put(k.getString("h")).put(k.getString("l"))
            .put(k.getString("c")).put(k.getString("v")).put(k.getLong("T"))
            .put(k.getString("q")).put(k.getLong("n")).put(k.getString("V")).put(k.getString("Q")).put("0");
        put(row,now);
    }
    public synchronized JSONArray snapshot(long now)throws Exception {
        if(rows.size()<125)return null;
        long latest=rows.lastKey()+59999;
        if(latest>=now||now-latest>75000)return null;
        long expected=rows.lastKey();int n=0;
        for(long t:rows.descendingKeySet()){if(t!=expected)return null;expected-=60000;if(++n==125)break;}
        JSONArray out=new JSONArray();for(String row:rows.values())out.put(new JSONArray(row));return out;
    }
}
