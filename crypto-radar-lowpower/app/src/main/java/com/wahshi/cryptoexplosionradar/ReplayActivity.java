package com.wahshi.cryptoexplosionradar;
import android.app.*;import android.os.*;import android.graphics.Color;import android.widget.*;import java.io.*;import java.util.*;
/** Offline native replay. No Python, Termux, network or trading permissions. */
public final class ReplayActivity extends Activity {
 private TextView output;private Button run;private Spinner symbols,variants;private final java.util.concurrent.ExecutorService worker=java.util.concurrent.Executors.newSingleThreadExecutor();
 @Override public void onCreate(Bundle state){super.onCreate(state);
  LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setPadding(24,20,24,16);root.setBackgroundColor(Color.rgb(9,12,17));
  TextView title=new TextView(this);title.setText("Historical Replay · V6");title.setTextSize(25);title.setTextColor(Color.WHITE);root.addView(title);
  TextView note=new TextView(this);note.setText("6–9 Oct 2026 UTC · 9 Oct جزئي\nاختبار بحثي؛ ليس إثباتًا للربحية. سيولة الشموع = حجم تداول، وليست دفتر الأوامر.\nهدف 5% قبل وقف 2% خلال 60 دقيقة؛ الرسوم والانزلاق محسوبة. آخر 60 دقيقة غير مكتملة التقييم.");note.setTextSize(16);note.setTextColor(Color.rgb(245,183,43));root.addView(note);
  symbols=new Spinner(this);symbols.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,HistoricalReplay.SYMBOLS));root.addView(symbols);
  variants=new Spinner(this);variants.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,HistoricalReplay.VARIANTS));variants.setSelection(5);root.addView(variants);
  run=new Button(this);run.setText("▶ إعادة الاختبار على الجهاز");root.addView(run);
  ScrollView scroll=new ScrollView(this);output=new TextView(this);output.setTextSize(17);output.setTextColor(Color.WHITE);output.setTextIsSelectable(true);scroll.addView(output);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
  setContentView(root);run.setOnClickListener(v->replay());replay();
 }
 private void replay(){String symbol=(String)symbols.getSelectedItem(),variant=(String)variants.getSelectedItem();run.setEnabled(false);output.setText("جارٍ حساب جميع الشموع بالتسلسل…");
  worker.submit(()->{String text;
   try(Reader input=new InputStreamReader(new java.util.zip.GZIPInputStream(getAssets().open("replay/"+symbol+".csv.bin")),"UTF-8")){
    List<PreExplosionEngine.Bar> bars=HistoricalReplay.read(input);StringBuilder alerts=new StringBuilder();String summary=HistoricalReplay.run(symbol,bars,alerts);StringBuilder s=new StringBuilder(symbol+" · "+variant+"\n");
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
 @Override public void onDestroy(){worker.shutdownNow();super.onDestroy();}
}
