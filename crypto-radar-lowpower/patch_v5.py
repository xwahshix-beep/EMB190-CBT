from pathlib import Path
import re
base=Path('app/src/main/java/com/wahshi/cryptoexplosionradar')
# V5.2 Recovery: preserve V4.4 discovery exactly. Disable legacy market-wide EXIT push
# and make the manually followed trade exclusive (one symbol only).
p=base/'HybridEngine.java'; s=p.read_text(encoding='utf-8')
old='''            if (c.tradeTriggered && "EXIT".equals(c.managementState)) {
                maybeExitAlert(context, prefs, c);
            }'''
if old not in s: raise SystemExit('V5.2 EXIT alert anchor missing')
s=s.replace(old,'            // V5.2: no market-wide EXIT push; TradeManager owns manual-follow notifications.',1)
p.write_text(s,encoding='utf-8')

p=base/'MainActivity.java'; s=p.read_text(encoding='utf-8')
old='''        if(entry<=0)return; SharedPreferences p=prefs(); String raw=p.getString("follow_symbols","");
        if(!(\",\"+raw+\",\").contains(\",\"+symbol+\",\")) raw=raw.isEmpty()?symbol:raw+\",\"+symbol;
        double stop=entry*0.97; p.edit().putString("follow_symbols",raw).putLong("follow_entry_"+symbol,Double.doubleToLongBits(entry))'''
new='''        if(entry<=0)return; SharedPreferences p=prefs(); String previous=p.getString("follow_symbols","");
        SharedPreferences.Editor clean=p.edit();
        if(previous!=null) for(String oldSymbol:previous.split(",")) { oldSymbol=oldSymbol.trim(); if(!oldSymbol.isEmpty() && !oldSymbol.equals(symbol)) {
            clean.remove("follow_entry_"+oldSymbol).remove("follow_stop_"+oldSymbol).remove("follow_state_"+oldSymbol);
        }}
        clean.putString("follow_symbols",symbol).apply();
        double stop=entry*0.97; p.edit().putString("follow_symbols",symbol).putLong("follow_entry_"+symbol,Double.doubleToLongBits(entry))'''
if old not in s: raise SystemExit('V5.2 follow anchor missing')
s=s.replace(old,new,1); p.write_text(s,encoding='utf-8')

p=Path('app/build.gradle'); s=p.read_text(encoding='utf-8'); s=re.sub(r'versionCode\s+\d+','versionCode 14',s,count=1); s=re.sub(r'versionName\s+"[^"]+"','versionName "5.2-recovery"',s,count=1); p.write_text(s,encoding='utf-8')
print('V5.2 Recovery applied: V4.4 discovery preserved; notifications exclusive to one manually followed symbol')
