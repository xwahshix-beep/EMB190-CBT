package com.wahshi.cryptoexplosionradar;

import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.*;

/** Bounded Binance Spot sampling. Closed 1m bars + fresh aggregate taker orders. */
public final class EarlyWhaleEngine {
    public interface Reader { String read(String path) throws Exception; }
    public static final class Evidence {
        double score, rvol, buyRatio, largeBuyQuote;
        int largeBuys;
        boolean repeated;
        String label() {
            return repeated ? "🐋 ضغط شراء متكرر" : "🐋 تدفق شراء مبكر محتمل";
        }
    }
    public static final class Result {
        final Map<String,Evidence> evidence = new HashMap<>();
        int checked, failed, truncated;
        String summary() {
            return "Early Whale: فُحص " + checked + " • نشاط " + evidence.size()
                + (failed > 0 ? " • تعذر فحص " + failed : "")
                + (truncated > 0 ? " • عينات صفقات محدودة " + truncated : "")
                + "\nتدفق السوق مؤشر محتمل؛ لا يحدد هوية الحوت.";
        }
    }
    private EarlyWhaleEngine() {}
    public static Result scan(List<String> ranked, SharedPreferences prefs, Reader reader) {
        Result out = new Result();
        if (ranked.isEmpty()) return out;
        LinkedHashSet<String> chosen = new LinkedHashSet<>();
        for (int i=0;i<Math.min(4,ranked.size());i++) chosen.add(ranked.get(i));
        List<String> rotating = new ArrayList<>(ranked); Collections.sort(rotating);
        int cursor = Math.floorMod(prefs.getInt("early_whale_cursor",0), rotating.size());
        for (int i=0;i<Math.min(4,rotating.size());i++) chosen.add(rotating.get((cursor+i)%rotating.size()));
        prefs.edit().putInt("early_whale_cursor",(cursor+4)%rotating.size()).apply();
        long deadline=System.currentTimeMillis()+45000;
        for (String symbol:chosen) {
            if (System.currentTimeMillis()>=deadline) { out.failed++; continue; }
            try {
                long now=System.currentTimeMillis();
                JSONArray raw=new JSONArray(reader.read("/api/v3/klines?symbol="+symbol+"&interval=1m&limit=31"));
                List<JSONArray> bars=new ArrayList<>();
                for(int i=0;i<raw.length();i++) {
                    JSONArray b=raw.getJSONArray(i);
                    if(b.getLong(6)<now) bars.add(b);
                }
                if(bars.size()<25 || now-bars.get(bars.size()-1).getLong(6)>120000)
                    throw new Exception("stale bars");
                int n=bars.size(); double baseline=0, recent=0, buy=0;
                for(int i=n-25;i<n-5;i++) baseline+=bars.get(i).getDouble(7);
                for(int i=n-5;i<n;i++) { recent+=bars.get(i).getDouble(7); buy+=bars.get(i).getDouble(10); }
                double first=bars.get(n-5).getDouble(1), last=bars.get(n-1).getDouble(4);
                if(first<=0 || last<=0 || baseline<=0 || recent<=0) throw new Exception("invalid bars");
                JSONArray trades=new JSONArray(reader.read("/api/v3/aggTrades?symbol="+symbol+"&limit=1000"));
                List<Double> sizes=new ArrayList<>(); Set<Long> ids=new HashSet<>();
                long newest=-1, oldest=Long.MAX_VALUE;
                for(int i=0;i<trades.length();i++) {
                    JSONObject t=trades.getJSONObject(i); long ts=t.getLong("T");
                    if(ts<now-300000 || ts>now+5000 || !ids.add(t.getLong("a"))) continue;
                    double q=t.getDouble("p")*t.getDouble("q");
                    if(!Double.isFinite(q)||q<=0) continue;
                    sizes.add(q); newest=Math.max(newest,t.getLong("a")); oldest=Math.min(oldest,ts);
                }
                if(sizes.isEmpty()) throw new Exception("no fresh orders");
                Collections.sort(sizes); double threshold=Math.max(25000,sizes.get(sizes.size()/2)*8);
                ids.clear(); int large=0; double largeBuy=0,largeSell=0;
                for(int i=0;i<trades.length();i++) {
                    JSONObject t=trades.getJSONObject(i); long ts=t.getLong("T");
                    if(ts<now-300000 || ts>now+5000 || !ids.add(t.getLong("a"))) continue;
                    double q=t.getDouble("p")*t.getDouble("q");
                    if(!Double.isFinite(q)||q<threshold) continue;
                    // m=false: buyer is taker, hence aggressive buying.
                    if(!t.getBoolean("m")) { large++; largeBuy+=q; } else largeSell+=q;
                }
                if(trades.length()==1000 && oldest>now-300000) out.truncated++;
                Evidence e=new Evidence(); e.rvol=recent/(baseline/4); e.buyRatio=buy/recent;
                e.largeBuys=large; e.largeBuyQuote=largeBuy;
                e.score=EarlyWhaleMath.score(e.rvol,e.buyRatio,(last/first-1)*100,large,largeBuy,largeSell);
                String key="ew_"+symbol; long previous=prefs.getLong(key+"_at",0);
                e.repeated=e.score>=.65 && now-previous>=60000 && now-previous<=900000
                    && newest>prefs.getLong(key+"_id",-1);
                SharedPreferences.Editor edit=prefs.edit();
                if(e.score>=.65) { edit.putLong(key+"_at",now).putLong(key+"_id",newest); }
                else { edit.remove(key+"_at").remove(key+"_id"); }
                edit.apply();
                if(e.repeated) e.score=Math.min(2,e.score+.25);
                if(e.score>0) out.evidence.put(symbol,e);
                out.checked++;
            } catch(Exception ex) { out.failed++; }
        }
        return out;
    }
}
