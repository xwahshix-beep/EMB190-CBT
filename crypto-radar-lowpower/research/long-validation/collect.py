"""Frozen protocol downloader. Missing archives retained in manifest; verified SHA256."""
import concurrent.futures as cf, urllib.request, urllib.error, hashlib, json, pathlib, time, sys
ROOT=pathlib.Path(__file__).resolve().parent
p=json.loads((ROOT/'protocol.json').read_text());cache=pathlib.Path(sys.argv[1]);cache.mkdir(parents=True,exist_ok=True)
def get(url):
 for k in range(3):
  try:return urllib.request.urlopen(url,timeout=45).read()
  except urllib.error.HTTPError as e:
   if e.code==404:raise
  except Exception:pass
  time.sleep(k+1)
 raise RuntimeError('network retry exhausted')
def work(task):
 symbol,month,period=task;name=f'{symbol}-1m-{month}.zip';url='https://data.binance.vision/data/spot/monthly/klines/'+symbol+'/1m/'+name
 row=dict(symbol=symbol,month=month,period=period,url=url)
 try:
  expected=get(url+'.CHECKSUM').decode().split()[0];f=cache/name
  b=f.read_bytes() if f.exists() else get(url)
  actual=hashlib.sha256(b).hexdigest()
  if actual!=expected:raise ValueError('checksum mismatch')
  f.write_bytes(b);row.update(status='ok',sha256=actual,bytes=len(b))
 except urllib.error.HTTPError as e:row.update(status='missing' if e.code==404 else 'error',error=str(e))
 except Exception as e:row.update(status='error',error=str(e))
 return row
tasks=[]
for period,(start,end) in p['periods'].items():
 y,m=map(int,start.split('-'))
 while f'{y:04}-{m:02}'<=end:
  for symbol in p['symbols']:tasks.append((symbol,f'{y:04}-{m:02}',period))
  m+=1
  if m==13:y+=1;m=1
rows=[]
with cf.ThreadPoolExecutor(max_workers=24) as ex:
 for r in ex.map(work,tasks):
  rows.append(r)
  if len(rows)%14==0:print(len(rows),len(tasks),sum(x['status']=='ok' for x in rows),'verified',flush=True)
(ROOT/'manifest.json').write_text(json.dumps(rows,indent=2))
print('COMPLETE', {k:sum(x['status']==k for x in rows) for k in ['ok','missing','error']},flush=True)
if any(x['status']=='error' for x in rows):sys.exit(1)
