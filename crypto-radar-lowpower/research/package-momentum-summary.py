"""Package measured results, never a hard-coded profitability claim."""
import json,sys
from pathlib import Path
old=json.loads(Path(sys.argv[1]).read_text()); new=json.loads(Path(sys.argv[2]).read_text())
periods=[]
def add(label,base,stress):
    m=base['metrics'];s=stress['metrics']
    periods.append({'label':label,'start':m['start'],'end':m['end'],'return_pct':m['return_pct'],'trades':m['trades'],'drawdown_pct':m['max_drawdown_pct'],'stress_return_pct':s['return_pct'],'average_exposure_pct':m['average_exposure_pct'],'bootstrap_interval':m['bootstrap_annualized_mean_return_95pct']})
for k,label in [('development','التطوير دون ضبط'),('validation','التحقق'),('holdout','الجزء المحجوز الأصلي')]:add(label,old['periods'][k]['base'],old['periods'][k]['stress'])
add('اختبار مستقل بالقواعد نفسها — 2026',new['base'],new['stress'])
payload={'profitability_proven':False,'periods':periods,'limitations':'محاكاة بشموع يومية؛ كل فترة تبدأ بـ 10,000 USDT افتراضية مستقلة. رسوم 0.10% لكل طرف، وانزلاق 0.05% أو 0.15% في الضغط. ليست رسوم حسابك المتحققة. لا دفتر أوامر ولا تأخر تنفيذ مقاس. لا ضمان للربح ولا اختبار مباشر على هاتفك. نطاق عدم اليقين يشمل الخسارة.','source':'Binance public daily candles','rules_changed_after_results':False}
dest=Path(__file__).resolve().parents[1]/'app/src/main/assets/research/momentum-summary.json'
dest.parent.mkdir(parents=True,exist_ok=True);dest.write_text(json.dumps(payload,ensure_ascii=False,indent=2))
print(dest)
