from pathlib import Path
import re
base=Path('app/src/main/java/com/wahshi/cryptoexplosionradar')
# V5.2 Recovery: DO NOT alter V4.4 discovery/ranking/filtering. Only lock phone notifications
# to the manually followed trade and keep one active followed symbol.
p=base/'HybridEngine.java'; s=p.read_text()
old='                    if (maybeBuyAlert(context, prefs, c, a, total)) buyCount++;'
if old not in s: raise SystemExit('V5.2 BUY alert anchor missing')
s=s.replace(old,'                    buyCount++; // V5.2: BUY remains visible in app, no general phone alert',1)
old='''            if (c.tradeTriggered && "EXIT".equals(c.managementState)) {
                maybeExitAlert(context, prefs, c);
            }'''
if old in s: s=s.replace(old,'            // V5.2: TradeManager alone sends followed-symbol management alerts.',1)
p.write_text(s)

p=base/'MainActivity.java'; s=p.read_text()
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
s=s.replace(old,new,1); p.write_text(s)

p=Path('app/build.gradle'); s=p.read_text(); s=re.sub(r'versionCode\s+\d+','versionCode 14',s); s=re.sub(r'versionName\s+[\'"][^\'"]+[\'"]','versionName "5.2-recovery"',s); p.write_text(s)
print('V5.2 Recovery: V4.4 scanner unchanged; notifications locked to one manually followed symbol')
