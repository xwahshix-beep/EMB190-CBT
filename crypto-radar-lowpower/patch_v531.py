from pathlib import Path
import re
p=Path('app/src/main/java/com/wahshi/cryptoexplosionradar/MainActivity.java')
s=p.read_text()
def replace(old,new):
 global s
 if s.count(old)!=1: raise SystemExit('UI patch anchor mismatch: '+old)
 s=s.replace(old,new,1)
replace('.setNeutralButton("تسجيل دخول",', '.setNeutralButton("سعر الشراء",')
replace('.setPositiveButton("مراقبة مبكرة",', '.setPositiveButton("مراقبة",')
replace('إذا اشتريتها اختر تسجيل دخول.', 'إذا اشتريتها اختر سعر الشراء.')
replace('    private TextView health;', '    private TextView health;\n    private Button startButton;')
replace('        Button start = new Button(this);', '        startButton = new Button(this);')
s=s.replace('start.', 'startButton.')
replace('controls.addView(start, bp);', 'controls.addView(startButton, bp);')
replace('            health.setText("● جارٍ تشغيل الرادار…");', '            health.setText("● جارٍ تشغيل الرادار…");\n            updateStartButton(true);')
replace('            startService(new Intent(this, ScannerService.class).setAction(ScannerService.ACTION_STOP));', '            startService(new Intent(this, ScannerService.class).setAction(ScannerService.ACTION_STOP));\n            updateStartButton(false);')
replace('        boolean enabled = p.getBoolean("radar_enabled", false);', '        boolean enabled = p.getBoolean("radar_enabled", false);\n        updateStartButton(enabled);')
replace('    private GradientDrawable roundRect(', '''    private void updateStartButton(boolean running) {
        if (startButton == null) return;
        startButton.setEnabled(!running);
        startButton.setBackground(roundRect(running ? Color.rgb(65, 70, 80) : Color.rgb(35, 139, 84), 14));
        startButton.setTextColor(running ? Color.rgb(180, 185, 195) : Color.WHITE);
    }

    private GradientDrawable roundRect(''')
replace('🎯 Explosion Radar V5.3', '🎯 Explosion Radar V5.3.1')
p.write_text(s)
p=Path('app/build.gradle');s=p.read_text()
s=re.sub(r'versionCode\s+\d+', 'versionCode 54',s)
s=re.sub(r'versionName\s+"[^"]+"','versionName "5.3.1-ui"',s)
p.write_text(s)
print('V5.3.1: simplified labels and state-aware Start button')
