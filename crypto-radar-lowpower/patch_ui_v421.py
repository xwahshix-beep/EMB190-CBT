from pathlib import Path

p = Path('app/src/main/java/com/wahshi/cryptoexplosionradar/MainActivity.java')
s = p.read_text(encoding='utf-8')

# Needed for collapsible help panel.
if 'import android.view.View;\n' not in s:
    s = s.replace('import android.view.Gravity;\n', 'import android.view.Gravity;\nimport android.view.View;\n', 1)

# Give more usable space to live candidates.
s = s.replace('root.setPadding(32, 38, 32, 26);', 'root.setPadding(24, 24, 24, 18);')
s = s.replace('title.setTextSize(24);', 'title.setTextSize(21);')
s = s.replace('title.setPadding(0,0,0,12);', 'title.setPadding(0,0,0,6);')
s = s.replace('mode.setTextSize(14);', 'mode.setTextSize(12);')
s = s.replace('mode.setPadding(0,0,0,20);', 'mode.setPadding(0,0,0,10);')
s = s.replace('status.setPadding(0,16,0,6);', 'status.setPadding(0,8,0,4);')
s = s.replace('lastScan.setPadding(0,0,0,12);', 'lastScan.setPadding(0,0,0,6);')

old_note = '''        TextView note = new TextView(this);\n        note.setText("\\n🔎 EARLY HUNT: يفحص سوق Binance Spot/USDT كاملًا تقريبًا بحثًا عن RVOL وتسارع الصفقات والزخم المبكر.\\n\\n🎯 ARMED: أفضل المرشحين يخضعون لتأكيد 15m: Taker Buy + Higher Lows/Compression + Resistance/Retest.\\n\\n🐋 WHALE: التجميع On-chain أصبح عامل تقوية وليس شرطًا، مع Ethereum/Base/Arbitrum/Avalanche/Solana وBNB Smart Chain عبر RPC مجاني.\\n\\n⚡ CATALYST: الأخبار الرسمية من Binance ترفع أو تخفض ثقة المرشح، لكنها ليست شرطًا.\\n\\n📲 لا إشعارات للهاتف لـ Early Hunt أو ARMED أو Whale/Catalyst منفردة؛ Push فقط عند BUY مؤكد.\\n\\n🌙 Background Wake من V4.1 محفوظ لتستمر الدورة والشاشة مطفأة.");\n        note.setTextColor(Color.rgb(145,157,175));\n        note.setTextSize(13);\n        note.setPadding(0,8,0,10);\n        root.addView(note);\n\n        details = new TextView(this);\n        details.setText("في انتظار التشغيل…");\n        details.setTextColor(Color.rgb(220,225,232));\n        details.setTextSize(14);\n        details.setLineSpacing(5,1.0f);\n\n        ScrollView scroll = new ScrollView(this);\n        scroll.addView(details);\n        root.addView(scroll, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));\n'''

new_note = '''        Button help = new Button(this);\n        help.setText("ℹ️ شرح النظام");\n        help.setAllCaps(false);\n        root.addView(help, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));\n\n        TextView note = new TextView(this);\n        note.setText("🔎 EARLY HUNT: فحص السوق بالكامل للزخم المبكر.\\n\\n🎯 ARMED: تأكيد 15m عبر Taker Buy + Compression/Higher Lows + Resistance/Retest.\\n\\n🐋 WHALE: عامل تقوية On-chain وليس شرطًا.\\n\\n⚡ CATALYST: أخبار Binance عامل تقوية/تحذير.\\n\\n📲 Push فقط عند BUY مؤكد.\\n\\n🌙 Background Wake يعمل والشاشة مطفأة.");\n        note.setTextColor(Color.rgb(160,172,190));\n        note.setTextSize(13);\n        note.setPadding(10,8,10,10);\n        note.setVisibility(View.GONE);\n        root.addView(note);\n        help.setOnClickListener(v -> {\n            boolean show = note.getVisibility() != View.VISIBLE;\n            note.setVisibility(show ? View.VISIBLE : View.GONE);\n            help.setText(show ? "✖ إخفاء الشرح" : "ℹ️ شرح النظام");\n        });\n\n        TextView listTitle = new TextView(this);\n        listTitle.setText("📡 المرشحون الآن");\n        listTitle.setTextColor(Color.rgb(245,183,43));\n        listTitle.setTextSize(17);\n        listTitle.setPadding(0,10,0,6);\n        root.addView(listTitle);\n\n        details = new TextView(this);\n        details.setText("في انتظار التشغيل…");\n        details.setTextColor(Color.rgb(235,239,245));\n        details.setTextSize(16);\n        details.setLineSpacing(7,1.06f);\n        details.setPadding(8,8,8,24);\n\n        ScrollView scroll = new ScrollView(this);\n        scroll.setFillViewport(true);\n        scroll.addView(details);\n        root.addView(scroll, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));\n'''

if old_note not in s:
    raise SystemExit('V4.2 note/details block not found')
s = s.replace(old_note, new_note, 1)

s = s.replace('🎯🐋⚡ Hybrid Explosion Radar V4.2', '🎯🐋⚡ Hybrid Explosion Radar V4.2.1')
s = s.replace('بدء Hybrid Explosion Radar V4.2…', 'بدء Hybrid Explosion Radar V4.2.1…')

p.write_text(s, encoding='utf-8')
print('V4.2.1 UI patch applied: candidate-first dashboard + collapsible help')
