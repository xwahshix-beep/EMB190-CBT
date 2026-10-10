#!/usr/bin/env python3
"""Opportunistic historical fetch for new replay symbols. Explicitly reports any unavailable symbol."""
import gzip,json,sys,urllib.parse,urllib.request,pathlib
folder=pathlib.Path(sys.argv[1]);folder.mkdir(parents=True,exist_ok=True)
start=1791244800000-125*60000;end=1791590400000
results=[]
for symbol in ("LUMIAUSDT","ERAUSDT","CFXUSDT"):
 path=folder/(symbol+".csv.bin");cursor=start;count=0;rows=[];problem=""
 try:
  while cursor<end and len(rows)<7000:
   q=urllib.parse.urlencode({"symbol":symbol,"interval":"1m","startTime":cursor,"endTime":end-1,"limit":1000})
   url="https://data-api.binance.vision/api/v3/klines?"+q
   req=urllib.request.Request(url,headers={"User-Agent":"ExplosionRadarV6-ReplayResearch/1.0"})
   with urllib.request.urlopen(req,timeout=15) as http: candles=json.load(http)
   if not candles:break
   latest=cursor-60000
   for b in candles:
    t=int(b[0])
    if not(cursor<=t<end):continue
    rows.append(",".join(map(str,(t,b[1],b[2],b[3],b[4],0,0,b[7],b[8],0,b[10],0))))
    latest=t
   if latest<cursor:break
   cursor=latest+60000
  count=len(rows)
  if count>=125:
   with gzip.open(path,"wt",encoding="utf-8",newline="") as out:out.write("\n".join(rows)+"\n")
   results.append(f"{symbol}: available {count} minute bars; downloaded from Binance Spot REST")
  else:results.append(f"{symbol}: UNAVAILABLE ({count} bars; less than 125 warmup)")
 except Exception as e:
  problem=str(e);results.append(f"{symbol}: UNAVAILABLE ({count} bars; {problem[:140]})")
coverage=pathlib.Path("research/optional-replay-coverage.txt")
coverage.write_text("\n".join(results)+"\n",encoding="utf-8")
print(coverage.read_text())
