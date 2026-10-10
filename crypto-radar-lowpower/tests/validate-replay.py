#!/usr/bin/env python3
"""Validate deterministic full-universe minute replay without pinning stale research snapshots."""
import csv,sys
with open(sys.argv[1],newline="") as f: rows=list(csv.DictReader(f))
with open(sys.argv[2],newline="") as f: alerts=list(csv.DictReader(f))
symbols={"MAGICUSDT","BATUSDT","KAIAUSDT","ZKUSDT","STRKUSDT","BTCUSDT","ETHUSDT","BNBUSDT","XRPUSDT","ADAUSDT"}
variants={"VOLUME","VOLUME_FLOW","VOLUME_TRADES","BREAKOUT","FULL_WATCH","FULL_BUY"}
present={r["symbol"] for r in rows}
assert symbols.issubset(present),"Missing baseline or negative-control symbol"
assert present <= (symbols|{"LUMIAUSDT","ERAUSDT","CFXUSDT"}),"Unexpected replay symbols"
assert {(r["symbol"],r["variant"]) for r in rows}=={(s,v) for s in present for v in variants},"Missing variant for replay symbol"
assert len(rows)==6*len(present),"Replay summary size"
for r in rows:
 a=int(r["alerts"]);good=int(r["target5_before_stop2"]);bad=int(r["false_alerts"]);c=int(r["censored"])
 assert a==good+bad+c,(r["symbol"],r["variant"],"outcome mismatch")
 assert int(r["eligible_minutes"])>0
 assert int(r["episodes_detected"])<=int(r["episodes15pct"])
 assert r["precision_pct"]=="NA" or 0<=float(r["precision_pct"])<=100
assert len(alerts)==sum(int(r["alerts"]) for r in rows),"Alert journal mismatch"
assert all(x["symbol"] in present and x["variant"] in variants for x in alerts)
print("Validated",len(rows),"symbol-variant rows;",len(alerts),"alerts including non-pump controls")
