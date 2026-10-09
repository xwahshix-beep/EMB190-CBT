#!/usr/bin/env python3
"""Historical Binance Spot pre-explosion research tool (offline research, not Android runtime).
Usage: pip install numpy pandas requests; python explosion_replay.py --date YYYY-MM-DD
Data source: data.binance.vision public daily 1m archives.
"""
import argparse, io, zipfile, requests
import numpy as np
import pandas as pd
from datetime import datetime, timedelta, timezone

COLS=['open_time','open','high','low','close','volume','close_time','quote_volume','trades','taker_buy_base','taker_buy_quote','ignore']

def fetch_day(symbol, day):
    d=day.strftime('%Y-%m-%d')
    url=f'https://data.binance.vision/data/spot/daily/klines/{symbol}/1m/{symbol}-1m-{d}.zip'
    r=requests.get(url,timeout=45); r.raise_for_status()
    with zipfile.ZipFile(io.BytesIO(r.content)) as z:
        df=pd.read_csv(z.open(z.namelist()[0]),header=None,names=COLS)
    df['time']=pd.to_datetime(pd.to_numeric(df.open_time),unit='us' if float(df.open_time.iloc[0])>1e14 else 'ms',utc=True)
    for c in ['open','high','low','close','volume','quote_volume','trades','taker_buy_quote']:
        df[c]=pd.to_numeric(df[c],errors='coerce')
    return df

def analyze(symbol, date, threshold=0.15):
    day=datetime.strptime(date,'%Y-%m-%d').replace(tzinfo=timezone.utc)
    df=pd.concat([fetch_day(symbol,day-timedelta(days=i)) for i in (3,2,1,0)],ignore_index=True).sort_values('time').reset_index(drop=True)
    q=df.quote_volume; n=df.trades; p=df.close
    df['rv_5m']=q.rolling(5).sum()/q.shift(5).rolling(120).sum().mul(5/120).replace(0,np.nan)
    df['trade_accel']=n.rolling(5).sum()/n.shift(5).rolling(120).sum().mul(5/120).replace(0,np.nan)
    df['buy_share']=df.taker_buy_quote.rolling(5).sum()/q.rolling(5).sum().replace(0,np.nan)
    df['pre_range_high']=df.high.shift(1).rolling(30).max()
    target=df[(df.time>=day)&(df.time<day+timedelta(days=1))]
    if target.empty:return {'symbol':symbol,'error':'no data'}
    peak_i=target.high.idxmax()
    baseline=target.close.iloc[0]
    onset=target[(target.index<=peak_i)&(target.high/baseline-1>=threshold)]
    if onset.empty:return {'symbol':symbol,'peak_gain_pct':round((df.loc[peak_i,'high']/baseline-1)*100,2),'onset':'not reached'}
    onset_i=onset.index[0]
    early=df.loc[max(0,onset_i-120):onset_i-1].copy()
    hits=early[(early.rv_5m>=2.5)&(early.trade_accel>=1.8)&(early.buy_share>=.55)&(early.close>=early.pre_range_high*.995)]
    first=hits.iloc[0] if len(hits) else None
    return {'symbol':symbol,'onset_utc':str(df.loc[onset_i,'time']),'peak_gain_pct':round((df.loc[peak_i,'high']/baseline-1)*100,2),'first_watch_utc':str(first.time) if first is not None else '', 'lead_minutes':round((df.loc[onset_i,'time']-first.time).total_seconds()/60,1) if first is not None else None,'rv_5m':round(first.rv_5m,2) if first is not None else None,'trade_accel':round(first.trade_accel,2) if first is not None else None,'buy_share':round(first.buy_share,3) if first is not None else None}

if __name__=='__main__':
    ap=argparse.ArgumentParser()
    ap.add_argument('--date',required=True,help='UTC YYYY-MM-DD')
    ap.add_argument('--symbols',nargs='+',default=['MAGICUSDT','KAIAUSDT','BATUSDT','ZKUSDT','STRKUSDT'])
    ap.add_argument('--threshold',type=float,default=.15)
    a=ap.parse_args()
    results=[]
    for s in a.symbols:
        try:results.append(analyze(s,a.date,a.threshold))
        except Exception as e:results.append({'symbol':s,'error':str(e)})
    out=pd.DataFrame(results)
    out.to_csv('explosion_replay_results.csv',index=False)
    print(out.to_string(index=False))
