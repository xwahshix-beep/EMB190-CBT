from pathlib import Path
import re
base=Path('app/src/main/java/com/wahshi/cryptoexplosionradar')
p=base/'HybridEngine.java'; s=p.read_text()
def replace(old,new):
    global s
    if s.count(old)!=1: raise SystemExit('V5 anchor count: '+repr(old[:100]))
    s=s.replace(old,new,1)
# Rank liquid symbols for sampling, rotate quiet symbols so whale is not gated by momentum.
replace('        List<Candidate> candidates = new ArrayList<>();', '''        eligible.sort((a,b) -> {
            HourTicker ha=hour.get(a), hb=hour.get(b);
            double ra=ha==null?0:ha.quoteVolume/(day.get(a).quoteVolume/24);
            double rb=hb==null?0:hb.quoteVolume/(day.get(b).quoteVolume/24);
            return Double.compare(rb,ra);
        });
        EarlyWhaleEngine.Result early = EarlyWhaleEngine.scan(eligible, prefs,
                path -> readEarlyUrl(API + path));
        List<Candidate> candidates = new ArrayList<>();''')
replace('            boolean hasFlow =', '''            EarlyWhaleEngine.Evidence flow = early.evidence.get(symbol);
            if (flow != null) { score += flow.score; reasons.add(flow.label()); }
            boolean hasFlow =''')
replace('|| whale != null || catPos;', '|| whale != null || catPos || flow != null;')
replace('                c.whale = whale;', '                c.whale = whale;\n                c.earlyWhale = flow;')
replace('        out.summary = formatSummary(candidates, armedCount, activeCount);', '        out.summary = formatSummary(candidates, armedCount, activeCount) + "\\n\\n" + early.summary() + "\\nالسوق: " + hour.size() + "/" + eligible.size();')
replace('        ScannerService.WhaleSignal whale;', '        ScannerService.WhaleSignal whale;\n        EarlyWhaleEngine.Evidence earlyWhale;')
replace('            if (!buyNow && !wait && !exit && !tp2 && !tp1 && !hold) continue;',
        '            if (!buyNow && !wait && !exit && !tp2 && !tp1 && !hold && c.earlyWhale == null) continue;')
replace('            shown++;', '            if (c.earlyWhale != null) sb.append("\\n").append(c.earlyWhale.label());\n            shown++;')
# Fail isolated rolling batches; retain known tickers rather than aborting whole market.
replace('            JSONArray batch = new JSONArray(readUrl(url));', '''            JSONArray batch;
            try { batch = new JSONArray(readUrl(url)); }
            catch (Exception ex) { continue; }''')
replace('        return out;\n    }\n\n    private static Analysis analyze15m', '        if (out.isEmpty() && !symbols.isEmpty()) throw new Exception("Rolling market data unavailable");\n        return out;\n    }\n\n    private static Analysis analyze15m')
# Short timeout for the optional, bounded early flow layer.
anchor='    private static String readUrl(String url) throws Exception {'
replace(anchor, '''    private static String readEarlyUrl(String url) throws Exception {
        HttpsURLConnection c = (HttpsURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(2500); c.setReadTimeout(3000);
        try {
            int code=c.getResponseCode();
            if(code!=200) throw new Exception("Early Whale HTTP " + code);
            return readStream(c.getInputStream());
        } finally { c.disconnect(); }
    }

'''+anchor)
p.write_text(s)
p=base/'ScannerService.java'; s=p.read_text().replace('boolean whaleDue = cachedWhaleSignals.isEmpty()\n                    || lastWhaleScanAt == 0L', 'boolean whaleDue = lastWhaleScanAt == 0L')
p.write_text(s)
# Trade symbols must be Binance API symbols, not visual BASE/USDT labels.
p=base/'MainActivity.java'; s=p.read_text().replace('🎯 Explosion Radar','🎯 Explosion Radar V5')
s=s.replace('private void askEntry(String symbol) {', 'private void askEntry(String label) {\n        final String symbol=label.replace("/", "");')
s=s.replace('BINANCE SPOT • LIVE RADAR','EARLY WHALE • BINANCE SPOT')
p.write_text(s)
p=Path('app/build.gradle'); s=p.read_text(); s=re.sub(r'versionCode\s+\d+', 'versionCode 12',s); s=re.sub(r'versionName\s+[\'"][^\'"]+[\'"]', 'versionName "5.0-early-whale"',s); p.write_text(s)
print('V5 applied: early flow + rotation + fresh data + 15m BUY gate + trade symbol fix')
