package com.wahshi.cryptoexplosionradar;

import java.util.*;
import org.json.*;

/** Experimental public-data observer. No orders, BUY alerts or production gate changes. */
public final class MicroResearch {
 public static final String KEY="micro_research_v1";
 public static final double NOTIONAL=100, FEE=.001;
 public static final int LIMIT=600;
 private final Map<String,Tape> tapes=new HashMap<>();
 private final Map<String,Deque<double[]>> closes=new HashMap<>();
 private final Map<String,Long> lastMove=new HashMap<>();
 private JSONObject root;
 private static final class Tape {
  Deque<double[]> buckets=new ArrayDeque<>();
  long id=-1,time,started,lastEval,lastBookId=-1,bookAt,confirmedAt,pendingAt,badAt,lastControl=-1,lastSample;
  String state="WARMUP",pending="",old="UNKNOWN",flow="UNKNOWN";long baselineAt;
  double price,ratio,share,change,spread,entry,quantity,exit,bid,ask,depleted,replenished;
  boolean executable;String guard="NO_BOOK";
  TreeMap<Double,Double> asks=new TreeMap<>();
 }
 public MicroResearch(String saved){try{root=new JSONObject(saved);}catch(Exception e){root=new JSONObject();}}
 private JSONObject object(String key)throws Exception{JSONObject v=root.optJSONObject(key);if(v==null){v=new JSONObject();root.put(key,v);}return v;}
 private JSONArray array(String key)throws Exception{JSONArray v=root.optJSONArray(key);if(v==null){v=new JSONArray();root.put(key,v);}return v;}
 public void select(Collection<String> symbols,long now)throws Exception{
  tapes.keySet().retainAll(symbols);for(String s:symbols)tapes.computeIfAbsent(s,k->new Tape());
  root.put("selected",new JSONArray(symbols)).put("coverageAt",now);
 }
 public void reset(long now)throws Exception{tapes.clear();root.put("connected",false).put("disconnectedAt",now);}
 public void connected(long now)throws Exception{root.put("connected",true).put("connectedAt",now);}
 public void baseline(String symbol,String old,String flow,long now){Tape t=tapes.get(symbol);if(t!=null){t.old=old;t.flow=flow;t.baselineAt=now;}}
 private static boolean valid(double x){return Double.isFinite(x)&&x>0;}
 public void trade(String symbol,long id,long time,long received,double price,double qty,boolean buyerMaker)throws Exception{
  Tape t=tapes.get(symbol);if(t==null||id<=t.id||time<t.time||received-time>5000||time>received+1000||!valid(price)||!valid(qty)||!valid(price*qty))return;
  t.id=id;t.time=time;t.price=price;if(t.started==0)t.started=received;
  long slot=time/10000*10000;double[] b=t.buckets.peekLast();
  if(b==null||b[0]!=slot){b=new double[]{slot,0,0,price,price,0};t.buckets.add(b);}
  b[1]+=price*qty;if(!buyerMaker)b[2]+=price*qty;b[4]=price;b[5]++;
  while(!t.buckets.isEmpty()&&t.buckets.peekFirst()[0]<slot-330000)t.buckets.removeFirst();
  if(received-t.lastEval<2000)return;t.lastEval=received;
  double recent=0,buy=0,base=0,first=0;int active=0;
  for(double[] x:t.buckets){if(x[0]>=slot-20000){recent+=x[1];buy+=x[2];if(first==0)first=x[3];if(x[1]>0&&x[2]/x[1]>=.60)active++;}else if(x[0]>=slot-320000)base+=x[1];}
  t.ratio=base>0?recent/(base/10):0;t.share=recent>0?buy/recent:0;t.change=first>0?100*(price/first-1):0;
  String raw=received-t.started<330000?"WARMUP":t.ratio>=2.5&&t.share>=.65&&active>=3&&t.change>=.10?"WATCH":"WAIT";
  // Immediate data/execution guards always override hysteresis.
  if("WATCH".equals(raw)&&(received-t.bookAt>3000||!t.executable||t.change>1.5))raw="BLOCKED";
  String before=t.state;
  if(raw.equals("WATCH")){
   t.badAt=0;if(!raw.equals(t.pending)){t.pending=raw;t.pendingAt=received;}
   if(received-t.pendingAt>=10000){t.state="WATCH";if(!before.equals("WATCH"))t.confirmedAt=received;}
  }else{
   t.pending="";
   if(raw.equals("BLOCKED")||raw.equals("WARMUP")){t.state=raw;t.badAt=0;}
   else if(before.equals("WATCH")){if(t.badAt==0)t.badAt=received;if(received-t.badAt>=20000)t.state="CANCELLED";}
   else t.state=raw;
  }
  JSONObject f=new JSONObject().put("symbol",symbol).put("state",t.state).put("raw",raw).put("at",received).put("price",price)
   .put("ratio",t.ratio).put("buyShare",t.share).put("change30sPct",t.change).put("spreadBps",t.spread)
   .put("guard",received-t.bookAt>3000?"STALE_BOOK":t.change>1.5?"EXTENDED":t.guard)
   .put("askDepleted",t.depleted).put("askReplenished",t.replenished).put("confirmedAt",t.confirmedAt);
  object("latest").put(symbol,f);
  if(!before.equals(t.state)){
   array("events").put(new JSONObject(f.toString()).put("previous",before));
   if(t.state.equals("WATCH"))object("signals").put(symbol,received);
  }
  long control=received/900000;
  if(received-t.started>=330000&&(control>t.lastControl||(!before.equals("WATCH")&&t.state.equals("WATCH")&&received-t.lastSample>=300000))){
   boolean fixed=control>t.lastControl;t.lastControl=control;t.lastSample=received;
   boolean exists=false;JSONArray prior=array("paper");for(int i=0;i<prior.length();i++){JSONObject p=prior.getJSONObject(i);if(fixed&&symbol.equals(p.optString("symbol"))&&p.optString("cohort").equals("fixed_15m")&&p.optLong("at")/900000==control)exists=true;}
   if(exists)return;
   array("paper").put(new JSONObject().put("symbol",symbol).put("at",received).put("mid",price).put("cohort",fixed?"fixed_15m":"transition")
    .put("baselineAt",t.baselineAt).put("micro",t.state).put("baseline",received-t.baselineAt<=75000?t.old:"UNKNOWN").put("flow",received-t.baselineAt<=75000?t.flow:"UNKNOWN")
    .put("guard",f.getString("guard")).put("status","PENDING_ENTRY"));
  }
 }
 public void book(String symbol,long updateId,JSONArray bids,JSONArray asks,long now)throws Exception{
  Tape t=tapes.get(symbol);if(t==null||updateId<=t.lastBookId)return;
  // Partial-depth packets are complete top-20 snapshots, not diff updates.
  TreeMap<Double,Double> next=levels(asks,true);TreeMap<Double,Double> bidLevels=levels(bids,false);
  if(next.isEmpty()||bidLevels.isEmpty()||bidLevels.lastKey()>=next.firstKey())return;
  t.depleted=0;t.replenished=0;
  // Compare only shared price levels; disappearance from the top 20 is not execution evidence.
  for(Map.Entry<Double,Double> e:next.entrySet()){Double previous=t.asks.get(e.getKey());if(previous==null)continue;double delta=(e.getValue()-previous)*e.getKey();if(delta>0)t.replenished+=delta;else t.depleted-=delta;}
  t.asks=next;t.lastBookId=updateId;t.bookAt=now;t.ask=next.firstKey();t.bid=bidLevels.lastKey();
  t.spread=10000*(t.ask/t.bid-1);
  double remaining=NOTIONAL,q=0;
  for(Map.Entry<Double,Double> e:next.entrySet()){double cost=Math.min(remaining,e.getKey()*e.getValue());q+=cost/e.getKey();remaining-=cost;if(remaining<1e-8)break;}
  t.quantity=q;t.entry=q>0?NOTIONAL/q:0;t.exit=sell(bidLevels,q);
  double slip=t.entry>0?10000*(t.entry/t.ask-1):Double.POSITIVE_INFINITY;
  t.executable=remaining<1e-8&&t.exit>0&&t.spread<=15&&slip<=10;
  t.guard=remaining>=1e-8||t.exit<=0?"INSUFFICIENT_DEPTH":t.spread>15?"WIDE_SPREAD":slip>10?"HIGH_SLIPPAGE":"OK";
  if(!t.executable&&t.state.equals("WATCH")){
   t.state="BLOCKED";t.pending="";JSONObject f=object("latest").optJSONObject(symbol);if(f!=null){f.put("state","BLOCKED").put("guard",t.guard).put("at",now);array("events").put(new JSONObject(f.toString()).put("previous","WATCH"));}
  }
  JSONArray paper=array("paper");
  for(int i=0;i<paper.length();i++){JSONObject p=paper.getJSONObject(i);if(!symbol.equals(p.getString("symbol")))continue;
   String status=p.getString("status");long at=p.getLong("at");
   if(status.equals("PENDING_ENTRY")&&now-at>=1000){
    if(now-at>10000)p.put("status","MISSING_ENTRY");
    else if(t.executable)p.put("status","OPEN").put("entryAt",now).put("entry",t.entry).put("quantity",q).put("notional",NOTIONAL);
   }else if(status.equals("OPEN")){
    long due=p.getLong("entryAt")+900000;
    if(now>due+10000)p.put("status","MISSING_EXIT");
    else if(now>=due){double exit=sell(bidLevels,p.getDouble("quantity"));if(exit>0){double net=100*((exit*(1-FEE))/(p.getDouble("entry")*(1+FEE))-1);p.put("status","CLOSED").put("exitAt",now).put("exit",exit).put("netPct",net);}}
   }
  }
 }
 private static TreeMap<Double,Double> levels(JSONArray a,boolean ascending)throws Exception{
  TreeMap<Double,Double> out=new TreeMap<>();double prior=ascending?0:Double.POSITIVE_INFINITY;
  if(a.length()==0||a.length()>20)return out;
  for(int i=0;i<a.length();i++){JSONArray r=a.getJSONArray(i);double p=r.getDouble(0),q=r.getDouble(1);if(!valid(p)||!valid(q)||(ascending?p<=prior:p>=prior))return new TreeMap<>();out.put(p,q);prior=p;}return out;
 }
 private static double sell(TreeMap<Double,Double> levels,double q){if(!valid(q))return 0;double left=q,cash=0;for(Map.Entry<Double,Double> e:levels.descendingMap().entrySet()){double used=Math.min(left,e.getValue());cash+=used*e.getKey();left-=used;if(left<1e-10)return cash/q;}return 0;}
 public void minute(String symbol,long close,double price,long now)throws Exception{
  if(!valid(price)||close>=now||now-close>75000)return;
  Deque<double[]> a=closes.computeIfAbsent(symbol,k->new ArrayDeque<>());
  if(!a.isEmpty()&&close<=(long)a.peekLast()[0])return;
  if(!a.isEmpty()&&close-(long)a.peekLast()[0]!=60000)a.clear();
  a.add(new double[]{close,price});while(a.size()>16)a.removeFirst();
  if(a.size()<16)return;long start=(long)a.peekFirst()[0];double rise=100*(price/a.peekFirst()[1]-1);
  if(rise<3||now-lastMove.getOrDefault(symbol,0L)<900000)return;lastMove.put(symbol,now);
  long signal=object("signals").optLong(symbol);boolean captured=signal>=start&&signal<close;
  Tape t=tapes.get(symbol);String reason=captured?"PRIOR_MICRO_WATCH":t==null?"OUTSIDE_TOP20":t.started==0||t.started>start?"PARTIAL_COVERAGE":!t.guard.equals("OK")?t.guard:"NO_MICRO_CONFIRMATION";
  array("moves").put(new JSONObject().put("symbol",symbol).put("at",now).put("start",start).put("rise15mPct",rise).put("captured",captured).put("reason",reason).put("signalAt",signal).put("leadMs",captured?close-signal:JSONObject.NULL));
 }
 public String save(long now)throws Exception{
  root.put("schema","micro-research-v1").put("updatedAt",now).put("notionalUSDT",NOTIONAL).put("feePerSide",FEE);
  JSONArray paper=array("paper");for(int i=0;i<paper.length();i++){JSONObject p=paper.getJSONObject(i);if(p.optString("status").equals("PENDING_ENTRY")&&now-p.getLong("at")>10000)p.put("status","MISSING_ENTRY");else if(p.optString("status").equals("OPEN")&&now-p.getLong("entryAt")>910000)p.put("status","MISSING_EXIT");}
  for(String name:new String[]{"events","paper","moves"}){JSONArray a=array(name);while(a.length()>LIMIT||(a.length()>0&&now-a.getJSONObject(0).optLong("at")>7L*86400000)){a.remove(0);root.put("evicted",root.optLong("evicted")+1);}}
  JSONObject latest=object("latest");List<String> gone=new ArrayList<>();Iterator<String> it=latest.keys();while(it.hasNext()){String s=it.next();if(now-latest.getJSONObject(s).optLong("at")>86400000)gone.add(s);}for(String s:gone)latest.remove(s);
  return root.toString();
 }
 public static String report(String saved,long now){try{
  JSONObject r=new JSONObject(saved);StringBuilder b=new StringBuilder("رصد داخل الدقيقة • تجريبي بلا أوامر شراء\n20 مرشحًا كحد أقصى؛ تدفئة 5.5 دقائق بعد الاتصال/تغيير الرمز.\n");
  b.append("الاتصال: ").append(r.optBoolean("connected")&&now-r.optLong("updatedAt")<30000?"متصل":"متوقف / بيانات قديمة").append(" • آخر حفظ منذ ").append(Math.max(0,(now-r.optLong("updatedAt",now))/1000)).append(" ث\n");
  Set<String> selected=new HashSet<>();JSONArray selection=r.optJSONArray("selected");if(selection!=null)for(int i=0;i<selection.length();i++)selected.add(selection.getString(i));
  JSONObject latest=r.optJSONObject("latest");if(latest!=null){Iterator<String> it=latest.keys();int n=0;while(it.hasNext()&&n<20){String symbol=it.next();if(!selected.contains(symbol))continue;n++;JSONObject f=latest.getJSONObject(symbol);b.append(f.getString("symbol")).append(" • ").append(now-f.getLong("at")>10000?"STALE":f.getString("state")).append(" • ").append(f.getString("guard")).append(String.format(Locale.US," • نشاط %.1fx • شراء %.0f%%\n",f.getDouble("ratio"),100*f.getDouble("buyShare")));}}
  JSONArray paper=r.optJSONArray("paper");b.append("\nاختبار ورقي مقارن: عينة ثابتة كل ربع ساعة لكل رمز؛ الانتقالات منفصلة.\n");
  for(String model:new String[]{"baseline","flow","micro"}){int n=0,loss=0,missing=0,pending=0;double sum=0;if(paper!=null)for(int i=0;i<paper.length();i++){JSONObject p=paper.getJSONObject(i);String signal=p.optString(model);if(!p.optString("cohort").equals("fixed_15m")||!(signal.equals("WATCH")||signal.equals("EARLY_WATCH")||signal.equals("FLOW_READY")||signal.equals("BUY")))continue;
    if(p.optString("status").equals("CLOSED")){n++;double v=p.getDouble("netPct");sum+=v;if(v<=0)loss++;}else if(p.optString("status").startsWith("MISSING")||now-p.getLong("at")>930000)missing++;else pending++;}
   b.append(model).append(": ").append(n==0?"لا نتائج":String.format(Locale.US,"%+.3f%% • %d نتيجة • غير موجب %d",sum/n,n,loss)).append(" • مفقود ").append(missing).append(" • معلّق ").append(pending).append('\n');}
  JSONArray events=r.optJSONArray("events");b.append("\nآخر تغيّرات الحالات (تبقى بعد الإلغاء):\n");if(events!=null)for(int i=Math.max(0,events.length()-10);i<events.length();i++){JSONObject e=events.getJSONObject(i);b.append(e.optString("symbol")).append(" • ").append(e.optString("previous")).append(" → ").append(e.optString("state")).append(" • منذ ").append(Math.max(0,(now-e.optLong("at"))/1000)).append(" ث\n");}
  JSONArray moves=r.optJSONArray("moves");b.append("\nارتفاعات ≥3% خلال 15 دقيقة ضمن تغطية الدقائق المتصلة:\n");if(moves!=null)for(int i=Math.max(0,moves.length()-10);i<moves.length();i++){JSONObject m=moves.getJSONObject(i);b.append(m.getString("symbol")).append(String.format(Locale.US," %+.2f%% • ",m.getDouble("rise15mPct"))).append(m.getString("reason")).append('\n');}
  b.append("\nسجل الحالات محفوظ حتى بعد الإلغاء. تصدير JSON يشمل الحالات والإشارات والعينات والحركات؛ حد 600 لكل سجل / 7 أيام.\nمحاكاة 100 USDT: أول دفتر صالح بعد ثانية وخلال 10 ثوانٍ؛ خروج بعد 15 دقيقة؛ رسوم 0.10% لكل طرف وعمق معروض فعلي، دون ضمان تعبئة أو انزلاق مستقبلي.\nنقص/زيادة عروض الأسعار المشتركة بين لقطات أعلى 20 مستوى لا يثبت تنفيذها أو هوية المتداولين. سبب فوات الحركة وصفي عند رصدها، وليس تفسيرًا سببيًا. لا تدّعي هذه النتائج تغطية السوق كله أو الربحية.");return b.toString();
 }catch(Exception e){return "لم تتوفر بيانات الرصد داخل الدقيقة بعد.";}}
}
