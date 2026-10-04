from pathlib import Path

p = Path('app/src/main/java/com/wahshi/cryptoexplosionradar/MainActivity.java')
s = p.read_text(encoding='utf-8')

# Make header more compact so the candidate list owns most of the screen.
s = s.replace('root.setPadding(32, 38, 32, 26);', 'root.setPadding(24, 22, 24, 18);', 1)
s = s.replace('title.setTextSize(24);', 'title.setTextSize(22);', 1)
s = s.replace('title.setPadding(0,0,0,12);', 'title.setPadding(0,0,0,6);', 1)
s = s.replace('mode.setTextSize(14);', 'mode.setTextSize(12);', 1)
s = s.replace('mode.setPadding(0,0,0,20);', 'mode.setPadding(0,0,0,8);', 1)
s = s.replace('status.setTextSize(18);', 'status.setTextSize(16);', 1)
s = s.replace('status.setPadding(0,16,0,6);', 'status.setPadding(0,6,0,2);', 1)
s = s.replace('lastScan.setTextSize(13);', 'lastScan.setTextSize(12);', 1)
s = s.replace('lastScan.setPadding(0,0,0,12);', 'lastScan.setPadding(0,0,0,6);', 1)

# Replace the long explanatory block with a compact legend.
start = s.index('        TextView note = new TextView(this);')
end_marker = '        root.addView(note);\n'
end = s.index(end_marker, start) + len(end_marker)
compact_note = '''        TextView note = new TextView(this);\n        note.setText("🔎 Early Hunt   •   🎯 ARMED   •   🐋 Whale   •   ⚡ Catalyst\\n📲 إشعار الهاتف فقط عند BUY مؤكد");\n        note.setTextColor(Color.rgb(145,157,175));\n        note.setTextSize(12);\n        note.setPadding(0,6,0,8);\n        root.addView(note);\n'''
s = s[:start] + compact_note + s[end:]

# Add a strong visual heading above the live candidate area.
anchor = '        details = new TextView(this);\n'
heading = '''        TextView candidatesTitle = new TextView(this);\n        candidatesTitle.setText("📡 المرشحون الآن");\n        candidatesTitle.setTextColor(Color.rgb(245,183,43));\n        candidatesTitle.setTextSize(18);\n        candidatesTitle.setPadding(0,6,0,6);\n        root.addView(candidatesTitle);\n\n'''
if heading not in s:
    s = s.replace(anchor, heading + anchor, 1)

# Larger, easier-to-read candidate text and more spacing.
s = s.replace('details.setTextSize(14);', 'details.setTextSize(17);', 1)
s = s.replace('details.setLineSpacing(5,1.0f);', 'details.setLineSpacing(8,1.05f);', 1)
s = s.replace('details.setTextColor(Color.rgb(220,225,232));', 'details.setTextColor(Color.rgb(235,239,245));', 1)
s = s.replace('        details.setLineSpacing(8,1.05f);\n', '        details.setLineSpacing(8,1.05f);\n        details.setPadding(4,4,4,16);\n        details.setTextIsSelectable(true);\n', 1)

# Rename the displayed version to make the UI update obvious.
s = s.replace('Hybrid Explosion Radar V4.2', 'Hybrid Explosion Radar V4.2.1')

p.write_text(s, encoding='utf-8')
print('V4.2.1 Large Market View UI applied')
