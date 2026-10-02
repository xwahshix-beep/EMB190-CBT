package com.wahshi.cryptoexplosionradar;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private TextView status;
    private TextView details;
    private EditText apiKey;

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (!ScannerService.ACTION_UI.equals(intent.getAction())) return;
            String s = intent.getStringExtra("status");
            String d = intent.getStringExtra("details");
            status.setText(s == null ? "" : s);
            details.setText(d == null ? "" : d);
        }
    };

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 7);
        }

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(32, 38, 32, 26);
        root.setBackgroundColor(Color.rgb(9,12,17));

        TextView title = new TextView(this);
        title.setText("🐋 Whale Accumulation Radar");
        title.setTextColor(Color.WHITE);
        title.setTextSize(25);
        title.setGravity(Gravity.CENTER_HORIZONTAL);
        title.setPadding(0,0,0,12);
        root.addView(title);

        TextView mode = new TextView(this);
        mode.setText("ON-CHAIN • Nansen + Binance Spot / USDT\nيرصد تجميع الحيتان وSmart Money قبل الحركة السعرية الكبيرة");
        mode.setTextColor(Color.rgb(245,183,43));
        mode.setTextSize(15);
        mode.setGravity(Gravity.CENTER_HORIZONTAL);
        mode.setPadding(0,0,0,20);
        root.addView(mode);

        TextView keyLabel = new TextView(this);
        keyLabel.setText("Nansen API Key — يُحفظ مشفرًا على الهاتف");
        keyLabel.setTextColor(Color.rgb(170,182,198));
        keyLabel.setTextSize(13);
        root.addView(keyLabel);

        LinearLayout keyRow = new LinearLayout(this);
        keyRow.setOrientation(LinearLayout.HORIZONTAL);
        apiKey = new EditText(this);
        apiKey.setHint(SecretStore.hasNansenKey(this) ? "API key محفوظ ✓ — أدخل مفتاحًا جديدًا فقط للتغيير" : "ألصق Nansen API key هنا");
        apiKey.setSingleLine(true);
        apiKey.setTextColor(Color.WHITE);
        apiKey.setHintTextColor(Color.rgb(110,122,140));
        apiKey.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        keyRow.addView(apiKey, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        Button save = new Button(this);
        save.setText("حفظ");
        save.setOnClickListener(v -> {
            String k = apiKey.getText().toString().trim();
            if (k.isEmpty()) {
                Toast.makeText(this, SecretStore.hasNansenKey(this) ? "المفتاح محفوظ بالفعل" : "أدخل API key أولًا", Toast.LENGTH_SHORT).show();
                return;
            }
            try {
                SecretStore.saveNansenKey(this, k);
                apiKey.setText("");
                apiKey.setHint("API key محفوظ ✓");
                Toast.makeText(this, "تم حفظ المفتاح مشفرًا", Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Toast.makeText(this, "تعذر حفظ المفتاح", Toast.LENGTH_LONG).show();
            }
        });
        keyRow.addView(save);
        root.addView(keyRow);

        status = new TextView(this);
        status.setText(SecretStore.hasNansenKey(this) ? "الرادار متوقف" : "أدخل Nansen API key ثم شغّل الرادار");
        status.setTextColor(Color.rgb(230,235,242));
        status.setTextSize(18);
        status.setPadding(0,16,0,12);
        root.addView(status);

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setGravity(Gravity.CENTER);

        Button start = new Button(this);
        start.setText("تشغيل رادار الحيتان");
        start.setOnClickListener(v -> {
            if (!SecretStore.hasNansenKey(this)) {
                status.setText("أدخل Nansen API key أولًا");
                return;
            }
            Intent i = new Intent(this, ScannerService.class).setAction(ScannerService.ACTION_START);
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(i); else startService(i);
            status.setText("بدء فحص On-chain…");
        });
        buttons.addView(start, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        Button stop = new Button(this);
        stop.setText("إيقاف");
        stop.setOnClickListener(v -> startService(new Intent(this, ScannerService.class).setAction(ScannerService.ACTION_STOP)));
        buttons.addView(stop, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        root.addView(buttons);

        TextView note = new TextView(this);
        note.setText("\nهذا الإصدار لا يعطي أوامر شراء. يبحث عن: Whale net inflow + Smart Traders + Top-PnL wallets + خروج من المنصات + Fresh Wallets، ثم يتحقق أن الزوج موجود على Binance Spot وأن السعر لم ينفجر بعد.\n\nPowered by Nansen API • الفحص الهادئ يقلل استهلاك البطارية وAPI credits.");
        note.setTextColor(Color.rgb(145,157,175));
        note.setTextSize(13);
        note.setPadding(0,8,0,10);
        root.addView(note);

        details = new TextView(this);
        details.setText("في انتظار التشغيل…");
        details.setTextColor(Color.rgb(220,225,232));
        details.setTextSize(14);
        details.setLineSpacing(5,1.0f);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(details);
        root.addView(scroll, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));
        setContentView(root);
    }

    @Override protected void onStart() {
        super.onStart();
        IntentFilter f = new IntentFilter(ScannerService.ACTION_UI);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(receiver, f, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(receiver, f);
    }

    @Override protected void onStop() {
        try { unregisterReceiver(receiver); } catch (Exception ignored) {}
        super.onStop();
    }
}
