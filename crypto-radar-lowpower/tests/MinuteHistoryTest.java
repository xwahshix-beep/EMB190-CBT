package com.wahshi.cryptoexplosionradar;
import org.json.*;
public final class MinuteHistoryTest {
    private static final long BASE=1800000000000L;
    private static void check(boolean v,String m){if(!v)throw new AssertionError(m);}
    private static JSONArray row(int i)throws Exception{return new JSONArray().put(BASE+i*60000L).put("1").put("1.001").put("0.999").put("1").put("100").put(BASE+i*60000L+59999).put("100").put(20).put("60").put("60").put("0");}
    private static JSONObject packet(int i,boolean closed)throws Exception{return new JSONObject().put("t",BASE+i*60000L).put("T",BASE+i*60000L+59999).put("s","AAAUSDT").put("i","1m").put("x",closed).put("o","1").put("h","1.001").put("l","0.999").put("c","1").put("v","100").put("q","100").put("n",20).put("V","60").put("Q","60");}
    public static void main(String[] args)throws Exception {
        long now=BASE+130*60000L+1000;JSONArray seed=new JSONArray();for(int i=0;i<130;i++)seed.put(row(i));
        MinuteHistory h=new MinuteHistory();h.merge(seed,now);
        check(h.snapshot(now).length()==130,"warmup");
        check(h.snapshot(now+76000)==null,"disconnect makes old data unusable");
        h.stream(packet(130,false),now+60000);check(h.snapshot(now+60000).length()==130,"open candle ignored");
        check(h.snapshot(now+60000).getJSONArray(129).getLong(0)==BASE+129*60000L,"no lookahead");
        h.stream(packet(130,true),now);check(h.snapshot(now).getJSONArray(129).getLong(0)==BASE+129*60000L,"future closed flag rejected");
        h.stream(packet(130,true),now+60000);
        check(h.snapshot(now+60000).getJSONArray(129).getLong(0)==BASE+130*60000L,"closed stream accepted");
        h.merge(seed,now+60000);check(h.snapshot(now+60000).getJSONArray(129).getLong(0)==BASE+130*60000L,"late REST cannot remove new stream bar");
        h.stream(packet(132,true),now+180000);check(h.snapshot(now+180000)==null,"gap blocks decisions");
        h.stream(packet(131,true),now+180000);check(h.snapshot(now+180000)!=null,"out of order repair");
        h.stream(packet(132,true),now+180000);check(h.snapshot(now+180000).length()==130,"duplicates do not expand history");
        JSONArray rest=new JSONArray();for(int i=3;i<=132;i++)rest.put(row(i));
        check(PreExplosionJson.evaluate(rest,now+180000).audit().equals(PreExplosionJson.evaluate(h.snapshot(now+180000),now+180000).audit()),"REST/stream feature parity");
        int usable=0;for(int s=0;s<150;s++){
            MinuteHistory market=new MinuteHistory();market.merge(seed,now);market.stream(packet(130,true),now+60000);
            if(market.snapshot(now+60000)!=null)usable++;
        }
        check(usable==150,"all 150 warmed symbols available in same cycle");
        System.out.println("MinuteHistoryTest passed: 150-symbol readiness, closed-only, stale, gap repair, duplicates, REST/stream parity");
    }
}
