# 6.11.0 Research — forward microstructure observer

This release does not enable production BUY, place orders, or establish profitability.
The existing closed-minute engine and its frozen replay evidence are unchanged.

## Data and bounded coverage

- Binance Spot eligible USDT universe is unchanged (existing daily liquidity/eligibility rules).
- A separate connection follows at most 20 candidates ranked by existing hourly relative volume.
- The selection changes no more frequently than every 15 minutes. A change or reconnection restarts 330 seconds of warmup; it cannot create a signal from incomplete history.
- `aggTrade` supplies taker direction (`m=false` means buyer is taker), not trader identity. Aggregate IDs and timestamps reject duplicate/out-of-order/old packets.
- `depth20` supplies complete top-20 bid/ask snapshots at the default one-second cadence. It is NOT a diff-book reconstruction. Update IDs must advance, levels must be ordered, and crossed/invalid books are rejected.
- Ask decreases/increases are measured only at prices present in both snapshots. They can represent cancellations or additions, not necessarily trades or spoofing. Snapshot receipt age is known; exchange timestamp is not provided by this stream.
- Socket health is checked every five seconds; silence for 15 seconds triggers bounded backoff. Foreground-service lifecycle starts/stops both streams. Android background/network restrictions still apply.

## Frozen experimental rules

Evaluate at most once per two seconds. Ten-second buckets form a current 3-bucket window and preceding 30-bucket baseline. A raw watch requires turnover ratio >=2.5, aggregate buy share >=65%, each current bucket >=60% buying, and price response >=0.10%. Confirm only after ten seconds of persistent conditions. A weak signal must remain weak for 20 seconds before cancellation; execution/data guards override this delay. Extension above 1.5%, depth older than 3 seconds, spread above 15 bps or displayed buy slippage above 10 bps block a candidate. These thresholds are experimental, not fitted probabilities.

Every state transition is kept after cancellation. No production signal/notification behavior changes. Latest frames visibly become STALE after ten seconds; the event history remains.

## Forward paper comparison

First available observation in each 15-minute slot after warmup, including WAIT controls, freezes old, flow and micro states. Old/flow states older than 75 seconds are UNKNOWN. Additional micro transitions have a separate cohort and 5-minute cooldown. Slot deduplication survives reconnect/restart.

Hypothetical notional is 100 USDT; fee is 0.10% each side. Entry uses the first acceptable displayed ask ladder at least one second after observation, within ten seconds. Exit uses the bid ladder 15 minutes after entry, within ten seconds. Insufficient displayed quantity does not create a fill. Missed entry/exit windows stay explicitly missing; they are never filled using a later price. Net return includes both fees, spread and displayed depth consumption. It excludes queueing, future impact, lot-size rounding and real execution delays. This is a measurement instrument, not a trade simulator validated against actual fills.

The on-device report compares positive signals in the fixed sample cohort and shows missing/pending outcomes. Non-positive net outcomes are not a calibrated false-alarm rate. Full WAIT/control samples and separate transition samples are exported for further analysis. Different positive subsets are not a causal paired efficacy test.

## Missed movement audit

Closed WebSocket minute prices across the existing subscription universe detect >=3% close-to-close gains over 15 minutes, requiring all 16 consecutive closes. Audit records whether a prior micro WATCH was observed and its lead time. Reasons distinguish outside top 20, partial warmup/coverage, current execution guard and no micro confirmation. Reasons are descriptive at audit time, not proven root causes. Missing/unsubscribed periods cannot be called missed signals. This does not yet classify misses for every legacy model.

## Storage and installation

600 entries each for states, paper observations and movement audits, retained up to seven days with eviction count; latest frames retained one day. Save every ten seconds and on stop; an abrupt process kill can lose up to ten seconds. Export JSON from the new main-screen button. Trades and book ladders are in-memory only and warm up again after restart.

Package `com.wahshi.cryptoexplosionradar.research611` / label `Explosion Radar 6.11` intentionally installs alongside older versions: their temporary signing private keys were not retained. It does not migrate old settings or logs; keep the older installation if those are needed. No production signing key is present in the repository.

## Verification and limitations

Native regression tests cover persistent flow, duplicate/out-of-order rejection, immediate stale-book/spread guards, reconnect warmup, forward entry/exit fees, insufficient depth, missing windows and gap-aware audits. CI builds and verifies APK metadata, signature and packaged historical evidence. This does not replace testing on an Android phone, live network endurance, battery measurement or independent prospective profitability validation. No synthetic test is presented as market performance.

Primary API source: https://developers.binance.com/docs/binance-spot-api-docs/web-socket-streams
