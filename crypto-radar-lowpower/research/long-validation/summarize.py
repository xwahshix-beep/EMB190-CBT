import json,pathlib,hashlib,sys
import numpy as np,pandas as pd
r=pathlib.Path(__file__).resolve().parent
p=json.loads((r/'protocol.json').read_text());d=pd.read_csv(r/'trades.csv');cov=pd.read_csv(r/'coverage.csv');manifest=json.loads((r/'manifest.json').read_text())
assert all(x['status']!='error' for x in manifest),'unresolved download errors'
assert set(d.variant.dropna().astype(int))<= {0,1}
def stat(x,c):
 valid=x[x.missing==0].copy();n=len(valid)
 out=dict(signals=len(x),evaluated=n,missing=int(x.missing.sum()),months=int(valid.month.nunique()),events=int(c.events.sum()),detected=int(c.detected.sum()),missed=int(c.missed.sum()))
 if not n:return {**out,'accepted':False,'reason':'no evaluable signals'}
 for k in ['net60','stress60','baseline60','baselineStress']:
  assert np.isfinite(valid[k]).all();out[k]=float(valid[k].mean())
 out['excess']=out['net60']-out['baseline60'];out['stressExcess']=out['stress60']-out['baselineStress'];out['negative']=int((valid.net60<0).sum());out['positive']=int((valid.net60>0).sum());out['false_target_alerts']=int((valid.target==0).sum());out['worst_net']=float(valid.net60.min());out['best_net']=float(valid.net60.max())
 valid['excess']=valid.net60-valid.baseline60
 groups=valid.groupby(['symbol','month']).agg(net=('net60','sum'),excess=('excess','sum'),n=('net60','count')).to_numpy()
 # Resample symbol-month clusters, preserve all observations inside each sampled cluster.
 rng=np.random.default_rng(20261024);means=[]
 for _ in range(2000):
  sample=groups[rng.integers(len(groups),size=len(groups))].sum(axis=0);means.append(sample[:2]/sample[2])
 ci=np.quantile(means,[.0125,.9875],axis=0);out['net_ci97_5']=ci[:,0].tolist();out['excess_ci97_5']=ci[:,1].tolist()
 out['accepted']=bool(n>=100 and out['months']>=6 and ci[0,0]>0 and ci[0,1]>0 and out['stress60']>0 and out['stressExcess']>0)
 out['reason']='passes historical criteria only' if out['accepted'] else 'fails one or more preregistered criteria; not enabled'
 return out
results={}
for label,(start,end) in p['periods'].items():
 results[label]={}
 for v in [0,1]:
  x=d[(d.variant==v)&(d.month>=start)&(d.month<=end)];c=cov[(cov.variant==v)&(cov.month>=start)&(cov.month<=end)]
  results[label][str(v)]=stat(x,c)
by_symbol={}
for sym in p['symbols']:
 by_symbol[sym]={str(v):stat(d[(d.symbol==sym)&(d.variant==v)],cov[(cov.symbol==sym)&(cov.variant==v)]) for v in [0,1]}
passed=[v for v in ['0','1'] if all(results[k][v]['accepted'] for k in ['validation','confirmation_retrospective'])]
summary=dict(protocol=p['id'],profitability_proven=False,independent_forward_validation=False,passed_historical_variants=passed,production_buy_enabled=False,periods=results,by_symbol=by_symbol,data_files={k:sum(x['status']==k for x in manifest) for k in ['ok','missing','error']},bars=int(cov[cov.variant==0].bars.sum()),limitations=p['limitations'],unassessed_engines=p['unevaluable'],execution_model=p['entry']+'; '+p['exit'],protocol_sha256=hashlib.sha256((r/'protocol.json').read_bytes()).hexdigest(),source_checksums={x.name:hashlib.sha256(x.read_bytes()).hexdigest() for x in [r/'trades.csv',r/'coverage.csv',r/'manifest.json']})
(r/'scanner-long-summary.json').write_text(json.dumps(summary,ensure_ascii=False,indent=2))
print(json.dumps({k:summary[k] for k in ['bars','data_files','passed_historical_variants','periods']},indent=2))
