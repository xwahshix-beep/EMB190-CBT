"""Daily-bar research prototype. No API keys and no order endpoints.

All thresholds fixed before seeing results. Daily opens proxy execution;
this does NOT validate real execution, latency, capacity or future returns.
"""
import datetime as dt
import gzip
import json
import math
from pathlib import Path
import numpy as np
import pandas as pd

ROOT=Path(__file__).resolve().parent
STABLE={'USDC','BUSD','TUSD','USDP','PAX','DAI','UST','USTC','USDD','FDUSD','USDE','USD1','AEUR','EUR','EURI','VAI','SUSD','USTB','USDS','USDSB','BIDR','IDRT','AUD','GBP','BRL','RUB','UAH','NGN','PLN','ZAR'}
EXCLUSIONS={}

def features(df):
    d=df.copy()
    prev=d.close.shift()
    tr=pd.concat([d.high-d.low,(d.high-prev).abs(),(d.low-prev).abs()],axis=1).max(axis=1)
    atr=pd.Series(np.nan,index=d.index)
    if len(d)>=14:
        atr.iloc[13]=tr.iloc[:14].mean()
        for i in range(14,len(d)): atr.iloc[i]=(atr.iloc[i-1]*13+tr.iloc[i])/14
    d['atr']=atr
    d['upper20']=d.high.shift().rolling(20).max()
    d['lower10']=d.low.shift().rolling(10).min()
    d['sma50']=d.close.rolling(50).mean()
    d['liq30']=d.quote.rolling(30).median()
    # Require 90 consecutive daily observations, not just 90 arbitrary rows.
    d['eligible']=(d.index.to_series()-d.index.to_series().shift(89)).dt.days.eq(89)&(d.quote.rolling(30).min()>0)
    d['buy']=(d.close>d.upper20)&(d.close>d.sma50)&d.eligible
    d['sell']=d.close<d.lower10
    return d

def load():
    datasets={}; audits=[]
    known_bases={s[:-4] for s in json.loads((ROOT/'archive_symbols.json').read_text()) if s.endswith('USDT')}
    for path in sorted((ROOT/'data').glob('*.json.gz')):
        symbol=path.name.split('.')[0]; base=symbol[:-4]
        leveraged=any(base.endswith(s) and base[:-len(s)] in known_bases for s in ('UP','DOWN','BULL','BEAR'))
        if base in STABLE or leveraged:
            EXCLUSIONS[symbol]='stable/fiat or leveraged-token suffix exclusion'; continue
        rows=json.loads(gzip.decompress(path.read_bytes()))
        if not rows: continue
        df=pd.DataFrame(rows)
        ts=pd.to_numeric(df[0]); ts=ts.where(ts<10**14,ts//1000)
        idx=pd.to_datetime(ts,unit='ms',utc=True).dt.tz_localize(None)
        d=pd.DataFrame({'open':pd.to_numeric(df[1]),'high':pd.to_numeric(df[2]),'low':pd.to_numeric(df[3]),'close':pd.to_numeric(df[4]),'quote':pd.to_numeric(df[7])})
        d.index=pd.DatetimeIndex(idx)
        if d.index.has_duplicates: raise ValueError('Duplicate days '+symbol)
        d=d.sort_index()
        invalid=((d.low>d.high)|(d.open>d.high)|(d.open<d.low)|(d.close>d.high)|(d.close<d.low)|(d.low<=0)|(d.quote<0))
        if invalid.any(): raise ValueError('Invalid OHLC '+symbol)
        gaps=int(((d.index[1:]-d.index[:-1]).days!=1).sum())
        audits.append({'symbol':symbol,'rows':len(d),'first':str(d.index[0].date()),'last':str(d.index[-1].date()),'gaps':gaps})
        datasets[symbol]=features(d)
    return datasets,audits

def simulate(data,start,end,slip=.0005,fee=.001):
    cash=10000.; positions={}; trades=[]; events=[]; curve=[]; universe=[]; ranks={}
    dates=pd.date_range(start,end,freq='D')
    for date in dates:
        prev=date-pd.Timedelta(days=1)
        if date==dates[0] or date.weekday()==0:
            ranks={s:float(d.loc[prev,'liq30']) for s,d in data.items() if prev in d.index and bool(d.loc[prev,'eligible'])}
            universe=sorted(ranks,key=lambda s:(-ranks[s],s))[:20]
            events.append({'date':str(date.date()),'event':'weekly_universe','symbols':universe})
        closed_today=set()
        def close(s,raw,reason):
            nonlocal cash
            p=positions.pop(s); price=max(0,raw*(1-slip)); gross=p['qty']*price
            cash+=gross*(1-fee)
            pnl=gross*(1-fee)-p['cost']
            trades.append({'symbol':s,'entry_date':str(p['day'].date()),'exit_date':str(date.date()),'qty':p['qty'],'entry_price':p['entry'],'exit_price':price,'cost':p['cost'],'pnl':pnl,'return_pct':100*pnl/p['cost'],'reason':reason})
            closed_today.add(s)
        # Manage existing positions before sizing new orders.
        for s,p in list(positions.items()):
            d=data[s]
            if date not in d.index:
                close(s,0,'missing_bar_conservative_writeoff'); continue
            bar=d.loc[date]
            if bar.open<=p['stop']: close(s,bar.open,'gap_stop')
            elif (date-p['day']).days>=30: close(s,bar.open,'time_exit')
            elif prev in d.index and bool(d.loc[prev,'sell']): close(s,bar.open,'channel_exit')
        # Account equity at today's open, never at today's closing price.
        equity_open=cash+sum(p['qty']*float(data[s].loc[date,'open']) for s,p in positions.items())
        candidates=[s for s in universe if s not in positions and s not in closed_today and prev in data[s].index and bool(data[s].loc[prev,'buy'])]
        for s in candidates:
            if len(positions)>=3:
                events.append({'date':str(date.date()),'symbol':s,'event':'rejected_capacity'}); continue
            d=data[s]
            if date not in d.index:
                events.append({'date':str(date.date()),'symbol':s,'event':'rejected_missing_bar'}); continue
            prior=d.loc[prev]; raw=float(d.loc[date,'open']); entry=raw*(1+slip); atr=float(prior.atr)
            if entry>prior.close+.25*atr:
                events.append({'date':str(date.date()),'symbol':s,'event':'rejected_price_cap'}); continue
            if not math.isfinite(atr) or atr<=0 or entry<=2*atr: continue
            # Include estimated entry+exit fees and stop slippage in planned risk.
            loss_per_unit=entry*(1+fee)-(entry-2*atr)*(1-slip)*(1-fee)
            qty=min(.0025*equity_open/loss_per_unit,.10*equity_open/entry,cash/(entry*(1+fee)))
            if qty<=0: continue
            cost=qty*entry*(1+fee); cash-=cost
            positions[s]={'qty':qty,'cost':cost,'entry':entry,'stop':entry-2*atr,'day':date}
            events.append({'date':str(date.date()),'symbol':s,'event':'entry'})
        # Daily lows can trigger a stop after opening entries. No profit target.
        for s,p in list(positions.items()):
            bar=data[s].loc[date]
            if bar.low<=p['stop']: close(s,min(float(bar.open),p['stop']),'intraday_stop')
        if date==dates[-1]:
            for s in list(positions): close(s,float(data[s].loc[date,'close']),'end_of_test')
        exposure=sum(p['qty']*float(data[s].loc[date,'close']) for s,p in positions.items())
        equity=cash+exposure
        if cash < -1e-7 or equity<0: raise AssertionError('Accounting error')
        curve.append({'date':str(date.date()),'equity':equity,'cash':cash,'exposure':exposure})
    a=np.array([10000.]+[r['equity'] for r in curve]); ret=a[1:]/a[:-1]-1
    dd=a/np.maximum.accumulate(a)-1
    gains=sum(max(0,t['pnl']) for t in trades); losses=-sum(min(0,t['pnl']) for t in trades)
    assert abs(sum(t['pnl'] for t in trades)-(cash-10000))<1e-5
    metrics={'start':str(dates[0].date()),'end':str(dates[-1].date()),'initial':10000,'final':float(a[-1]),'return_pct':float((a[-1]/10000-1)*100),'max_drawdown_pct':float(dd.min()*100),'trades':len(trades),'win_rate_pct':100*sum(t['pnl']>0 for t in trades)/len(trades) if trades else 0,'profit_factor':gains/losses if losses else None,'average_exposure_pct':float(np.mean([r['exposure']/r['equity'] for r in curve])*100),'missing_writeoffs':sum(t['reason']=='missing_bar_conservative_writeoff' for t in trades),'fees_per_side':fee,'slippage_per_side':slip}
    # Fixed-seed moving block bootstrap of daily portfolio returns; descriptive,
    # does not correct strategy selection or imply a future confidence guarantee.
    rng=np.random.default_rng(20261017); block=14; n=len(ret); means=[]
    for _ in range(2000):
        starts=rng.integers(0,max(1,n-block+1),size=math.ceil(n/block))
        sampled=np.concatenate([ret[j:j+block] for j in starts])[:n]
        means.append(sampled.mean()*365*100)
    metrics['bootstrap_annualized_mean_return_95pct']=[float(x) for x in np.quantile(means,[.025,.975])]
    return {'metrics':metrics,'trades':trades,'curve':curve,'events':events}

def benchmark(data,start,end,weight,slip=.0005,fee=.001):
    d=data['BTCUSDT'].loc[start:end]
    cash=10000*(1-weight); qty=10000*weight/(float(d.iloc[0].open)*(1+slip)*(1+fee))
    a=np.r_[10000,cash+qty*d.close.to_numpy()]
    a[-1]=cash+qty*float(d.iloc[-1].close)*(1-slip)*(1-fee)
    return {'return_pct':float((a[-1]/10000-1)*100),'max_drawdown_pct':float((a/np.maximum.accumulate(a)-1).min()*100),'allocation':weight}

def tests(data):
    # A shock to future candles must not alter earlier indicators.
    d=data['BTCUSDT'][['open','high','low','close','quote']].copy(); cut=len(d)//2
    x=features(d); d.iloc[cut:,:4]*=7; y=features(d)
    pd.testing.assert_frame_equal(x.iloc[:cut],y.iloc[:cut])
    # No trading before a breakout; this also verifies flat-account accounting.
    dates=pd.date_range('2020-01-01',periods=150)
    flat=pd.DataFrame({'open':100.,'high':101.,'low':99.,'close':100.,'quote':1000000.},index=dates)
    r=simulate({'TESTUSDT':features(flat)},'2020-04-01','2020-05-01')
    assert r['metrics']['trades']==0 and r['metrics']['final']==10000
    # Force a known breakout then a stop: loss must include both fees/slippage.
    flat.loc['2020-04-01',['high','close']]=[105,104]
    flat.loc['2020-04-02',['open','high','low','close']]=[104,105,90,95]
    r=simulate({'TESTUSDT':features(flat)},'2020-04-01','2020-04-03')
    assert len(r['trades'])==1 and r['trades'][0]['reason']=='intraday_stop'
    assert abs(r['trades'][0]['pnl']+25)<1e-6
    return ['future candle perturbation leaves prior features unchanged','flat market produces no trades','synthetic stop loses exactly planned $25 including modeled costs','all simulations reconcile closed trade PnL with final account cash']

def main():
    data,audits=load(); verified=tests(data)
    days=pd.date_range('2022-01-01','2025-12-31'); n=len(days)
    i=int(.6*n); j=int(.8*n)
    periods={'development':(days[0],days[i-1]),'validation':(days[i]+pd.Timedelta(days=30),days[j-1]),'holdout':(days[j]+pd.Timedelta(days=30),days[-1])}
    result={'status':'preliminary_daily_bar_simulation_not_execution_validation','fixed_rules_no_parameter_search':True,'assumed_fees_not_account_verified':True,'tests':verified,'coverage':audits,'exclusions':EXCLUSIONS,'periods':{},'benchmarks':{}}
    for label,(start,end) in periods.items():
        result['periods'][label]={}
        for name,slip in [('base',.0005),('stress',.0015)]:
            result['periods'][label][name]=simulate(data,start,end,slip)
        result['benchmarks'][label]={'btc_30pct_cash_70pct':benchmark(data,start,end,.3),'btc_100pct':benchmark(data,start,end,1.)}
        print(label,json.dumps(result['periods'][label]['base']['metrics']),flush=True)
    (ROOT/'results.json').write_text(json.dumps(result,indent=2))
    print('Tests passed:',verified,flush=True)

if __name__=='__main__': main()
