# V6.2 coverage repair

V6.1 added a research lab; it did not change minute discovery. V6.2 changes discovery scheduling and visibility, not the PreExplosionEngine BUY thresholds.

- All previously eligible liquid USDT pairs reach minute scheduling regardless of hourly/daily change, hourly score, or preliminary spread. The existing universe still excludes stable/fiat and leveraged-looking names and requires 24h quote turnover >= $1.5m. It is not every Binance listing.
- Missing hourly data removes ranking information, not minute eligibility.
- Up to two priority symbols (active or recently watched) and oldest-attempt-first discovery fill eight request slots. A failed attempt advances that symbol; a deadline does not advance unattempted work.
- Successful coverage requires non-DATA, fresh contiguous minute history. Report shows valid coverage over five minutes, never successfully evaluated count, latest-cycle failures and deferrals. It does not label partial coverage as an exhaustive scan.
- Current batch and diagnostics appear above saved historical signals. Report age is visible; stale reports are flagged.

Regression test exercises 150 symbols, persistent priorities, early interruption, failures and deduplication. Existing causal feature, BUY/entry/state/replay tests remain gates. There is no claim of a measured increase in profitable detections, exhaustive instantaneous coverage or device testing. Phone networking limits and a bounded sequential scanner can still miss short events.
