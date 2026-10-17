package com.wahshi.cryptoexplosionradar;
import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.view.Gravity;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

/** User-triggered, read-only daily checks and persistent paper observations. */
public final class ResearchLabActivity extends Activity {
 private final ExecutorService worker=Executors.newSingleThreadExecutor();
 private long generation; private TextView status,history; private Button save;
 private String pending="",pendingKey="";
 private TextView text(LinearLayout root,String value,int size){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(Color.rgb(230,237,244));t.setPadding(0,14,0,14);root.addView(t);return t;}
 @Override public void onCreate(Bundle b){super.onCreate(b);
  ScrollView scroll=new ScrollView(this);LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setPadding(28,28,28,28);root.setBackgroundColor(Color.rgb(9,18,28));scroll.addView(root);setContentView(scroll);
  TextView title=text(root,"مختبر الزخم اليومي · V6.1",25);title.setGravity(Gravity.CENTER);
  text(root,"وضع بحثي — لا توجد أفضلية ربحية مثبتة\nفحص يدوي ودفتر ملاحظات؛ لا تنفيذ صفقات ولا مراقبة خلفية في هذه الشاشة.",17).setTextColor(Color.rgb(255,197,90));
  text(root,"نتائج الاختبارات الفعلية",21);
  try{
   ByteArrayOutputStream bytes=new ByteArrayOutputStream();try(InputStream in=getAssets().open("research/momentum-summary.json")){byte[] buffer=new byte[4096];int n;while((n=in.read(buffer))!=-1)bytes.write(buffer,0,n);}
   JSONObject report=new JSONObject(bytes.toString("UTF-8"));JSONArray periods=report.getJSONArray("periods");
   for(int i=0;i<periods.length();i++){JSONObject p=periods.getJSONObject(i);text(root,String.format(Locale.US,"%s\n%s → %s\nصافي العائد %+.2f%% • %d صفقة\nأقصى تراجع %.2f%% • عائد الضغط %+.2f%%",p.getString("label"),p.getString("start"),p.getString("end"),p.getDouble("return_pct"),p.getInt("trades"),p.getDouble("drawdown_pct"),p.getDouble("stress_return_pct")),17);}
   text(root,report.getString("limitations"),15);
  }catch(Exception e){text(root,"تعذر قراءة نتائج البحث؛ لا تعتمد أي نتيجة غير ظاهرة.",17);}
  text(root,"افحص عملة للملاحظة الورقية",21);
  EditText symbol=new EditText(this);symbol.setSingleLine(true);symbol.setText("BTCUSDT");symbol.setTextColor(Color.WHITE);symbol.setHintTextColor(Color.GRAY);symbol.setHint("مثال: BTCUSDT");root.addView(symbol);
  Button check=new Button(this);check.setText("فحص آخر يوم مكتمل");root.addView(check);
  status=text(root,"اختر العملة واضغط الفحص. السوق الفوري فقط.",18);
  save=new Button(this);save.setText("حفظ الملاحظة الورقية");save.setEnabled(false);root.addView(save);
  save.setOnClickListener(v->{
   if(pending.isEmpty())return;
   android.content.SharedPreferences p=getSharedPreferences("research_lab",MODE_PRIVATE);
   if(pendingKey.equals(p.getString("last_key",""))){Toast.makeText(this,"هذه الملاحظة محفوظة بالفعل",Toast.LENGTH_SHORT).show();return;}
   String log=pending+"\n────────\n"+p.getString("history","");if(log.length()>20000)log=log.substring(0,20000);
   p.edit().putString("history",log).putString("last_key",pendingKey).apply();history.setText(log);save.setEnabled(false);Toast.makeText(this,"تم حفظ الملاحظة — لم تُنفذ صفقة",Toast.LENGTH_LONG).show();
  });
  check.setOnClickListener(v->{
   String s=symbol.getText().toString().trim().toUpperCase(Locale.US);
   if(!s.matches("[A-Z0-9]{2,20}USDT")){status.setText("أدخل رمزًا صحيحًا ينتهي بـ USDT مثل BTCUSDT");return;}
   long request=++generation;pending="";save.setEnabled(false);status.setText("جارٍ جلب الشموع اليومية المكتملة…");
   worker.submit(()->{
    try{
     long now=new JSONObject(MarketHttp.read("https://data-api.binance.vision/api/v3/time")).getLong("serverTime");
     JSONArray rows=new JSONArray(MarketHttp.read("https://data-api.binance.vision/api/v3/klines?symbol="+s+"&interval=1d&limit=1000"));
     List<DailyMomentumEngine.Bar> bars=new ArrayList<>();
     for(int i=0;i<rows.length();i++){JSONArray x=rows.getJSONArray(i);if(x.getLong(6)>=now)continue;bars.add(new DailyMomentumEngine.Bar(x.getLong(0),x.getDouble(1),x.getDouble(2),x.getDouble(3),x.getDouble(4),x.getDouble(7)));}
     DailyMomentumEngine.Result r=DailyMomentumEngine.evaluate(bars,bars.size()-1,now);
     java.text.SimpleDateFormat fmt=new java.text.SimpleDateFormat("yyyy-MM-dd",Locale.US);fmt.setTimeZone(TimeZone.getTimeZone("UTC"));
     String message=s+"\n"+(r.closedAt>0?"اليوم المكتمل (UTC): "+fmt.format(new Date(r.closedAt-DailyMomentumEngine.DAY))+"\n":"")+(r.state.equals("WATCH")?"مراقبة بحثية — ليست توصية شراء":r.state.equals("WAIT")?"انتظار":"بيانات غير كافية")+"\n"+r.reason;
     if(!r.state.equals("DATA"))message+=String.format(Locale.US,"\nإغلاق: %.8f\nأعلى 20 يومًا: %.8f\nمتوسط 50 يومًا: %.8f\nATR14: %.8f\nسقف سعر النموذج: %.8f\nهذا الفحص لا يختار أعلى 20 عملة سيولة ولا يحاكي صفقة كاملة.",r.close,r.upper20,r.sma50,r.atr,r.priceCap);
     final String result=message;runOnUiThread(()->{if(isFinishing()||isDestroyed()||request!=generation)return;status.setText(result);pending=result;pendingKey=s+":"+r.closedAt;save.setEnabled(!r.state.equals("DATA"));});
    }catch(Exception e){runOnUiThread(()->{if(isFinishing()||isDestroyed()||request!=generation)return;status.setText("تعذر جلب البيانات. تحقق من الاتصال ثم أعد المحاولة.\n"+e.getMessage());save.setEnabled(false);});}
   });
  });
  text(root,"سجل الملاحظات المحفوظة",21);history=text(root,getSharedPreferences("research_lab",MODE_PRIVATE).getString("history","لا توجد ملاحظات محفوظة بعد"),16);
  Button back=new Button(this);back.setText("العودة للرادار");back.setOnClickListener(v->finish());root.addView(back);
 }
 @Override protected void onDestroy(){generation++;worker.shutdownNow();super.onDestroy();}
}
