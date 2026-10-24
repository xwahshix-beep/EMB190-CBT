"""Supplemental descriptive monthly comparison. Not used to tune or select rules.
Each symbol-month starts with 1 unit. Strategy compounds nonoverlapping 60m trades,
otherwise holds cash. Buy/hold buys first bar open and sells last bar close.
Both pay .1% fee and .05% slippage per side. Exposure is NOT matched.
Missing strategy outcomes or missing archive => comparison unavailable, never dropped silently.
"""
import pathlib,json,zipfile,csv,io
import pandas as pd
r=pathlib.Path(__file__).resolve().parent
m=json.loads((r/'manifest.json').read_text());t=pd.read_csv(r/'trades.csv')
rows=[]
for item in m:
 sym,month=item['symbol'],item['month']
 for v in [0,1]:
  row=dict(symbol=sym,month=month,variant=v,status=item['status'])
  if item['status']=='ok':
   p=pathlib.Path('/workspace/scratch/ec526f9bc010/long-minute-cache')/(sym+'-1m-'+month+'.zip')
   with zipfile.ZipFile(p) as z:
    lines=io.TextIOWrapper(z.open(z.namelist()[0])); first=None;last=None
    for line in lines:
     if line.startswith('open_time'):continue
     values=line.strip().split(',')
     if first is None:first=values
     last=values
   bh=100*(float(last[4])*.9995*.999/(float(first[1])*1.0005*1.001)-1)
   trades=t[(t.symbol==sym)&(t.month==month)&(t.variant==v)]
   row.update(buy_hold_pct=bh,signals=len(trades),missing_outcomes=int(trades.missing.sum()),exposure_minutes=60*len(trades))
   if row['missing_outcomes']:row['status']='incomplete_strategy_outcomes'
   else:
    net=100*((1+trades.net60/100).prod()-1)
    row.update(strategy_pct=net,excess_percentage_points=net-bh,status='evaluated')
  rows.append(row)
pd.DataFrame(rows).to_csv(r/'monthly-buy-hold.csv',index=False)
print('Monthly comparisons',len(rows),'including missing; not matched exposure or acceptance criterion')
