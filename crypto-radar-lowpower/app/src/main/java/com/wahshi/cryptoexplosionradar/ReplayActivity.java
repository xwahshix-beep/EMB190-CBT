package com.wahshi.cryptoexplosionradar;
import android.app.*;import android.os.*;import android.graphics.Color;import android.widget.*;import java.io.*;import java.util.*;import org.json.*;
/** Offline native replay. No Python, Termux, network or trading permissions. */
public final class ReplayActivity extends Activity {
 private TextView output;private Button run;private Spinner symbols,variants;private final java.util.concurrent.ExecutorService worker=java.util.concurrent.Executors.newSingleThreadExecutor();
 @Override public void onCreate(Bundle state){super.onCreate(state);
  LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setPadding(24,20,24,16);root.setBackgroundColor(Color.rgb(9,12,17));
  TextView title=new TextView(this);title.setText("Historical Replay · V6");title.setTextSize(25);title.setTextColor(Color.WHITE);root.addView(title);
  TextView note=new TextView(this);note.setText("6–9 Oct 2026 UTC · Bundled / on-demand Binance\nاختبار بحثي؛ ليس إثباتًا للربحية. سيولة الشموع = حجم تداول، وليست دفتر الأوامر.\nهدف 5% قبل وقف 2% خلال 60 دقيقة؛ الرسوم والانزلاق محسوبة. آخر 60 دقيقة غير مكتملة التقييم.");note.setTextSize(16);note.setTextColor(Color.rgb(245,183,43));root.addView(note);
  symbols=new Spinner(this);symbols.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,HistoricalReplay.SYMBOLS));root.addView(symbols);
  variants=new Spinner(this);variants.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,HistoricalReplay.VARIANTS));variants.setSelection(5);root.addView(variants);
  run=new Button(this);run.setText("▶ إعادة الاختبار على الجهاز");root.addView(run);
  ScrollView scroll=new ScrollView(this);output=new TextView(this);output.setTextSize(17);output.setTextColor(Color.WHITE);output.setTextIsSelectable(true);scroll.addView(output);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
  setContentView(root);run.setOnClickListener(v->replay());replay();
 }
 private void replay(){String symbol=(String)symbols.getSelectedItem(),variant=(String)variants.getSelectedItem();run.setEnabled(false);output.setText("جارٍ حساب جميع الشموع بالتسلسل…");
  worker.submit(()->{String text;
   try{
    List<PreExplosionEngine.Bar> bars=loadBars(symbol);StringBuilder alerts=new StringBuilder();String summary=HistoricalReplay.run(symbol,bars,alerts);StringBuilder s=new StringBuilder(symbol+" · "+variant+"\n");
    java.text.SimpleDateFormat fmt=new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm 'UTC'",Locale.US);fmt.setTimeZone(TimeZone.getTimeZone("UTC"));
    s.append("نهاية البيانات: ").append(fmt.format(new Date(bars.get(bars.size()-1).time+59999))).append("\n\n");
    for(String row:summary.split("\n")){String[] x=row.split(",");if(!x[1].equals(variant))continue;
     s.append("الشموع المختبرة: ").append(x[2]).append("\nالإشارات: ").append(x[3]).append("\nبلغت الهدف قبل الوقف: ").append(x[4]).append("\nإنذارات كاذبة / لم تبلغ الهدف: ").append(x[5]).append("\nتقييم غير مكتمل: ").append(x[6]).append("\nدقة الهدف: ").append(x[7]).append("%\nمتوسط العائد الصافي الافتراضي: ").append(x[8]).append("%\nحلقات ارتفاع 15%: ").append(x[9]).append("\nحلقات التقطتها الإشارات: ").append(x[10]).append("\nمتوسط السبق بالدقائق: ").append(x[11]).append("\n\n");}
    s.append("سجل الإشارات (UTC)\n");
    for(String row:alerts.toString().split("\n")){String[] x=row.split(",");if(x.length<9||!x[1].equals(variant))continue;s.append(fmt.format(new Date(Long.parseLong(x[2])))).append(" · ").append(x[3]).append("\nVol ").append(x[5]).append("x · Trades ").append(x[6]).append("x · Buy ").append(x[7]).append("\n\n");}
    s.append("FULL_BUY يختبر شروط المحرك؛ الفحص الحي يضيف صلاحية السعر وفارق السعر ودفتر الأوامر للعملة المتابعة. عينة العملات محدودة وليست السوق كله. لا يوجد ضبط للعتبات على نتائج هذه العينة.");text=s.toString();
   }catch(Exception e){text="تعذر الاختبار: "+e.getMessage();}
   final String ready=text;runOnUiThread(()->{if(!isFinishing()&&!isDestroyed()){output.setText(ready);run.setEnabled(true);}});
  });
 }
 private List<PreExplosionEngine.Bar> loadBars(String symbol)throws Exception{
  // Bundled symbols are offline. Additional symbols use bounded, dated Binance REST replay;
  // data is cached on-device without changing the closed-minute BUY rules.
  File cache=new File(getFilesDir(),"replay-"+symbol+"-20261006-09.csv.gz");
  InputStream stream=null;
  try {stream=getAssets().open("replay/"+symbol+".csv.bin");}
  catch(IOException missing){if(cache.isFile())stream=new FileInputStream(cache);}
  if(stream!=null){try(InputStream raw=stream;Reader reader=new InputStreamReader(new java.util.zip.GZIPInputStream(raw),"UTF-8")){
    return HistoricalReplay.read(reader);
  }}
  long cursor=HistoricalReplay.START-125L*60000L;
  List<PreExplosionEngine.Bar> out=new ArrayList<>();
  StringBuilder csv=new StringBuilder();
  for(int page=0;page<9&&cursor<HistoricalReplay.END;page++){
   String url="https://api.binance.com/api/v3/klines?symbol="+symbol+"&interval=1m&startTime="+cursor+"&endTime="+(HistoricalReplay.END-1)+"&limit=1000";
   JSONArray rows=new JSONArray(MarketHttp.read(url));if(rows.length()==0)break;
   long latest=cursor-60000L;
   for(int i=0;i<rows.length();i++){
    JSONArray x=rows.getJSONArray(i);long t=x.getLong(0);
    if(t<cursor||t>=HistoricalReplay.END)continue;
    PreExplosionEngine.Bar b=new PreExplosionEngine.Bar(t,x.getDouble(1),x.getDouble(2),x.getDouble(3),x.getDouble(4),x.getDouble(7),x.getLong(8),x.getDouble(10));
    out.add(b);latest=t;
    csv.append(t).append(',').append(x.getString(1)).append(',').append(x.getString(2)).append(',').append(x.getString(3)).append(',')
       .append(x.getString(4)).append(",0,0,").append(x.getString(7)).append(',').append(x.getLong(8))
       .append(",0,").append(x.getString(10)).append(",0\n");
   }
   if(latest<cursor)break;
   cursor=latest+60000L;
  }
  if(out.size()<PreExplosionEngine.WARMUP)throw new IOException("No sufficient historical Binance Spot 1m data for "+symbol);
  try(java.util.zip.GZIPOutputStream gz=new java.util.zip.GZIPOutputStream(new FileOutputStream(cache))){
   gz.write(csv.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
  }catch(IOException ignored){}
  return out;
 }
 @Override public void onDestroy(){worker.shutdownNow();super.onDestroy();}
}
