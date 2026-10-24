"""Reproduction of old daily study, NOT a new independent validation."""
import json,sys,pathlib,hashlib
import daily_portfolio as b
r=pathlib.Path(__file__).resolve().parent;cache=pathlib.Path(sys.argv[1]);old=json.loads((cache/'results.json').read_text());periods=[]
for folder,definitions in [(cache,[(k,v['base']['metrics']['start'],v['base']['metrics']['end']) for k,v in old['periods'].items()]),(cache/'independent_2026',[('2026_reproduction','2026-01-01','2026-10-16')])]:
 b.ROOT=folder;data,audit=b.load();tests=b.tests(data)
 for name,start,end in definitions:
  base=b.simulate(data,start,end);stress=b.simulate(data,start,end,slip=.0015);m=base['metrics'];s=stress['metrics'];bench={str(w):b.benchmark(data,start,end,w) for w in [.3,1.]}
  periods.append(dict(label=name+' — إعادة إنتاج',start=start,end=end,return_pct=m['return_pct'],trades=m['trades'],drawdown_pct=m['max_drawdown_pct'],stress_return_pct=s['return_pct'],average_exposure_pct=m['average_exposure_pct'],bootstrap_interval=m['bootstrap_annualized_mean_return_95pct'],benchmarks=bench))
  print(name,m['return_pct'],m['trades'],flush=True)
  (r/('daily-'+name+'.json')).write_text(json.dumps(dict(base=base,stress=stress,benchmarks=bench,tests=tests,coverage=audit)))
summary=dict(profitability_proven=False,periods=periods,rules_changed_after_results=False,validation_status='reproduction_of_previous_periods_NOT_new_independent_evidence',source='Cached Binance REST daily candles, separate from new monthly minute ZIP archives',limitations='إعادة إنتاج فعلية للفترات اليومية السابقة بالقواعد نفسها؛ ليست اختبارًا مستقلًا جديدًا ولا نتائج ماسح الدقيقة. رسوم 0.10% وانزلاق 0.05% لكل طرف؛ ضغط 0.15%. مرجع BTC بتخصيص 30% و100% ليس مطابقًا تمامًا لمخاطر المحفظة. لا دفتر أوامر أو تأخير تنفيذ مقاس؛ الربحية غير مثبتة.')
(r/'momentum-summary.json').write_text(json.dumps(summary,ensure_ascii=False,indent=2))
