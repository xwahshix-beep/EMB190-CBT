package com.wahshi.cryptoexplosionradar;

import org.json.*;
import java.util.*;

/** Frozen per-symbol minute-of-quarter-hour model. Research only: never supplies a BUY gate. */
public final class PhaseShadow {
    private PhaseShadow() {}
    static final long DAY=86400000L;
    static final int MIN_PER_PHASE=100, MAX_SAMPLES=72;
    static int phase(long time){return (int)Math.floorMod(time/60000L,15L);}
    static JSONArray zeros(){JSONArray a=new JSONArray();for(int i=0;i<15;i++)a.put(0);return a;}
    static boolean activity(double q,double n,double z){return q>=2.5&&(n>=1.8||z>=2.5);}

    public static String update(String saved,JSONArray rows,long now) throws Exception {
        JSONObject m=new JSONObject(saved);
        List<PreExplosionEngine.Bar> bars=new ArrayList<>();
        for(int i=0;i<rows.length();i++){
            JSONArray r=rows.getJSONArray(i);
            if(r.getLong(6)>=now)continue;
            bars.add(new PreExplosionEngine.Bar(r.getLong(0),r.getDouble(1),r.getDouble(2),r.getDouble(3),r.getDouble(4),r.getDouble(7),r.getLong(8),r.getDouble(10)));
        }
        int at=bars.size()-1;
        PreExplosionEngine.Result raw=PreExplosionEngine.evaluate(bars,at);
        if("DATA".equals(raw.state)||raw.closedAt>now||now-raw.closedAt>75000||raw.closedAt<=m.optLong("seen",0))return saved;
        // The current five-minute target NEVER enters its own calibration set.
        if(!m.has("count"))m.put("count",zeros()).put("quote",zeros()).put("trades",zeros()).put("days",new JSONArray());
        JSONArray count=m.getJSONArray("count"),quote=m.getJSONArray("quote"),trades=m.getJSONArray("trades"),days=m.getJSONArray("days");
        if(m.optLong("frozen",0)==0){
            long last=m.optLong("trained",0);
            for(int i=at-124;i<=at-5;i++){
                PreExplosionEngine.Bar b=bars.get(i);if(b.time<=last)continue;
                if(!m.has("first"))m.put("first",b.time);
                int p=phase(b.time);count.put(p,count.getLong(p)+1);quote.put(p,quote.getDouble(p)+b.quote);trades.put(p,trades.getDouble(p)+b.trades);
                long d=b.time/DAY;
                if(days.length()==0||days.getLong(days.length()-1)!=d){days.put(d);if(days.length()>32)days.remove(0);}
                last=b.time;
            }
            m.put("trained",last);
            boolean enough=days.length()>=7&&last-m.optLong("first",last)>=7*DAY;
            for(int p=0;p<15;p++)enough&=count.getLong(p)>=MIN_PER_PHASE&&quote.getDouble(p)>0&&trades.getDouble(p)>0;
            if(enough)m.put("frozen",raw.closedAt);
        }
        m.put("seen",raw.closedAt).put("checked",now);
        JSONArray samples=m.optJSONArray("samples");if(samples==null)samples=new JSONArray();
        // Settle existing observations even if the frozen model has expired.
        for(int i=0;i<samples.length();i++){
            JSONObject s=samples.getJSONObject(i);
            for(int minutes:new int[]{5,15,60}){
                String k="r"+minutes;long due=s.getLong("at")+minutes*60000L;if(s.has(k))continue;
                if(raw.closedAt>=due&&raw.closedAt<=due+90000)s.put(k,100*(bars.get(at).close/s.getDouble("price")-1)).put(k+"At",raw.closedAt);
                else if(now>due+165000)s.put(k,"missing");
            }
        }
        m.put("samples",samples);
        long frozen=m.optLong("frozen",0);
        if(frozen==0){m.put("status","calibrating");return m.toString();}
        if(now-frozen>28*DAY){m.put("status","complete");return m.toString();}
        m.put("status","shadow");
        double totalQ=0,totalN=0,totalCount=0;
        for(int p=0;p<15;p++){totalQ+=quote.getDouble(p);totalN+=trades.getDouble(p);totalCount+=count.getDouble(p);}
        double meanQ=totalQ/totalCount,meanN=totalN/totalCount;
        double[] q=new double[125],n=new double[125];
        for(int j=0;j<125;j++){
            PreExplosionEngine.Bar b=bars.get(at-124+j);int p=phase(b.time);
            q[j]=b.quote/(quote.getDouble(p)/count.getDouble(p)/meanQ);
            n[j]=b.trades/(trades.getDouble(p)/count.getDouble(p)/meanN);
        }
        double baseQ=0,baseN=0,targetQ=0,targetN=0;
        for(int j=0;j<125;j++){if(j<120){baseQ+=q[j];baseN+=n[j];}else{targetQ+=q[j];targetN+=n[j];}}
        if(baseQ<=0||baseN<=0){m.put("status","invalid");return m.toString();}
        double v=targetQ/(baseQ/24),t=targetN/(baseN/24),variance=0;
        for(int j=0;j<24;j++){double x=0;for(int k=0;k<5;k++)x+=n[j*5+k];variance+=Math.pow(x-baseN/24,2);}
        double sd=Math.sqrt(variance/24),z=sd>0?(targetN-baseN/24)/sd:0;
        boolean original=activity(raw.volumeAcceleration,raw.tradeAcceleration,raw.tradeZ),adjusted=activity(v,t,z);
        String group=original?(adjusted?"both":"raw_only"):(adjusted?"adjusted_only":"neither");
        JSONObject latest=new JSONObject().put("at",now).put("closedAt",raw.closedAt).put("rawVol",raw.volumeAcceleration).put("rawTrades",raw.tradeAcceleration).put("rawZ",raw.tradeZ)
            .put("adjustedVol",v).put("adjustedTrades",t).put("adjustedZ",z).put("group",group).put("price",bars.get(at).close).put("liveState",raw.state);
        m.put("latest",latest).put("frames",m.optLong("frames",0)+1).put(group,m.optLong(group,0)+1);
        // Fixed hourly sampling includes negative/no-activity controls; it is not triggered by winners.
        long hour=raw.closedAt/3600000L;
        if(hour>m.optLong("sampleHour",-1)){
            samples.put(new JSONObject(latest.toString()));if(samples.length()>MAX_SAMPLES)samples.remove(0);
            m.put("sampleHour",hour);
        }
        return m.toString();
    }

    public static String report(Map<String,?> preferences,long now){
        int calibration=0,ready=0,complete=0,stale=0;long frames=0,rawOnly=0,adjustedOnly=0;
        double[][] sums=new double[4][3];int[][] valid=new int[4][3],missing=new int[4][3],pending=new int[4][3];
        String[] groups={"both","raw_only","adjusted_only","neither"};int[] horizons={5,15,60};
        StringBuilder latest=new StringBuilder();int shown=0;
        for(Map.Entry<String,?> entry:new TreeMap<String,Object>(preferences).entrySet()){
            if(!entry.getKey().startsWith("v66_phase_"))continue;
            try{
                JSONObject m=new JSONObject(String.valueOf(entry.getValue()));String status=m.optString("status");
                if("calibrating".equals(status))calibration++;else if("complete".equals(status)||now-m.optLong("frozen",now)>28*DAY)complete++;else if("shadow".equals(status))ready++;
                if(now-m.optLong("checked",0)>120000)stale++;
                frames+=m.optLong("frames");rawOnly+=m.optLong("raw_only");adjustedOnly+=m.optLong("adjusted_only");
                JSONArray samples=m.optJSONArray("samples");if(samples==null)continue;
                for(int i=0;i<samples.length();i++){
                    JSONObject s=samples.getJSONObject(i);int group=Arrays.asList(groups).indexOf(s.optString("group"));if(group<0)continue;
                    for(int h=0;h<3;h++){Object r=s.opt("r"+horizons[h]);
                        if(r instanceof Number){valid[group][h]++;sums[group][h]+=((Number)r).doubleValue();}
                        else if("missing".equals(r)||now>s.getLong("at")+horizons[h]*60000L+165000)missing[group][h]++;else pending[group][h]++;
                    }
                }
                if(m.has("latest")&&shown++<12){JSONObject l=m.getJSONObject("latest");latest.append(entry.getKey().substring(10)).append(String.format(Locale.US," • حجم %.2f ← %.2f • صفقات %.2f ← %.2f\n",l.getDouble("rawVol"),l.getDouble("adjustedVol"),l.getDouble("rawTrades"),l.getDouble("adjustedTrades")));}
            }catch(Exception ignored){}
        }
        StringBuilder out=new StringBuilder("اختبار ربع الساعة • مراقبة فقط، لا يغير الشراء أو الإشعارات\nتعلّم لكل عملة: 7 أيام على الأقل و100 دقيقة لكل موضع من 15 موضعًا؛ ثم تثبيت النموذج ومراقبة 28 يومًا. يحتاج تشغيلًا وجمع بيانات؛ الأيام وحدها لا تكفي.\n");
        out.append("تجميع: ").append(calibration).append(" • مقارنة: ").append(ready).append(" • انتهت: ").append(complete).append(" • لم تُحدّث منذ دقيقتين: ").append(stale);
        out.append("\nمقارنات دقيقة مترابطة: ").append(frames).append("\nنشاط أصلي فقط: ").append(rawOnly).append(" • معدل فقط: ").append(adjustedOnly);
        out.append("\n\nعينة ثابتة: أول رصد متاح في كل ساعة لكل عملة، آخر 72 عينة لكل عملة؛ صدّر السجل كل يومين لحفظ الدراسة كاملة. تشمل عدم النشاط. حركة سعر إجمالية من آخر إغلاق عند الرصد، وليست أرباحًا أو أسعار تنفيذ. لا تشمل رسومًا أو انزلاقًا. البيانات المفقودة لا تعني خسارة أو ربحًا.\n");
        String[] labels={"نشاط بكلا المقياسين","نشاط أصلي فقط","نشاط معدل فقط","لا نشاط بكليهما"};
        for(int g=0;g<4;g++){out.append("\n").append(labels[g]).append("\n");for(int h=0;h<3;h++)out.append(horizons[h]).append(" د: ").append(valid[g][h]==0?"لا نتائج":String.format(Locale.US,"%+.2f%% (%d)",sums[g][h]/valid[g][h],valid[g][h])).append(" • مفقود ").append(missing[g][h]).append(" • انتظار ").append(pending[g][h]).append("\n");}
        out.append("\nأمثلة: الأصلي ثم المعدل (حتى 12 عملة بالاسم)\n").append(latest);
        out.append("\nاختلاف النشاط ليس إثبات إنذار كاذب أو فرصة فائتة. يلزم تحليل السجل المُصدّر وتكاليف التنفيذ قبل الحكم. لا توجد أفضلية مثبتة.");
        return out.toString();
    }
}
