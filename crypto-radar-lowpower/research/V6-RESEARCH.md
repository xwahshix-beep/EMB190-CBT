# V6 experimental native engine and replay

Base commit: 7491d7b53f70f978c212828619b41f2c61977a59.

## Data and limits

Binance Spot USDT 1m candles for MAGIC, KAIA, BAT, ZK, STRK, plus fixed liquid controls BTC, ETH, BNB, XRP, ADA. October 5 is warmup only. Evaluation covers October 6 00:00 UTC through October 9 20:07:59 UTC. October 9 is PARTIAL, because it had not finished in UTC at retrieval. No invented final-day candles.

October 5–8: official daily archives. October 9: paginated public klines endpoint, frozen at 20:08 UTC, excludes the open candle. Per-source retrieval status is in source-manifest.json and rest-manifest.json. Packaged file hashes are in assets/replay/manifest.json.

Sources:
- https://github.com/binance/binance-public-data
- https://developers.binance.com/docs/binance-spot-api-docs/rest-api/market-data-endpoints
- https://data.binance.vision/
- https://data-api.binance.vision/api/v3/klines

## Causal features and fixed thresholds

The same PreExplosionEngine.java executes in the Android scanner and Historical Replay. It uses five CLOSED minutes against the preceding non-overlapping 120 minutes: volume acceleration >=2.5, trade count acceleration >=1.8, taker buy quote share >=55%, five-minute turnover >=25,000 USDT. WATCH requires price within 0.5% below the previous 30-minute high. BUY requires a closed-minute breakout 0.1%–0.6% above that high, five-minute change <=1.5%, and last-minute range <=1.2%. These are experimental fixed rules, not fitted optimal parameters.

Turnover is a liquidity PROXY, not historical order-book depth. Selected-coin live BUY additionally checks current bid/ask spread <=15 bps, at least $5,000 displayed depth on each side within 20 levels and 0.2%, price freshness, and the existing anti-chase gate. Historical results do not claim execution equivalence for these unavailable depth observations. Broad market discovery additionally has legacy universe/ranking filters. Replay evaluates the engine on every minute without that scheduler and therefore does not measure production scan latency or whole-app precision.

## Evaluation

Every eligible minute is processed in chronological order, with independent 15-minute signal cooldown per variant. Entry is NEXT candle open plus 10 bps slippage. Success is +5% before -2% within 60 minutes. If both are touched in a minute, stop wins. Stop gaps use the worse opening price. Costs subtract another 30 bps (20 fees, 10 exit slippage). No target means false alert for this definition, even if return is positive. Missing full future horizon is censored, never counted as failure. Mean returns are per hypothetical signal, NOT portfolio returns; overlapping signals and capital constraints are not simulated.

The six ablations are volume alone, volume+flow, volume+trade count, breakout alone, full WATCH and full BUY. Independently detect non-overlapping 120-minute episodes gaining >=15% from the next-minute open, then count signals between episode anchor and first +15% touch. This episode definition is mechanical, not a visual claim about the exact start of an explosion. Lead is measured to that +15% touch. Episodes with zero alerts remain in the denominator.

The requested symbols are outcome-selected by the user; adding five fixed controls and evaluating all timestamps reduces cherry-picking but does NOT remove selection bias or establish out-of-sample validity. No thresholds were retuned after inspecting these results. Longer predeclared out-of-sample testing remains necessary.

## Observed result

On requested coins FULL_BUY emitted 17 signals: zero reached +5% before -2% in 60 minutes. FULL_WATCH emitted 62 signals: 7 targets, 55 false alerts; it detected 3 of 9 mechanically defined +15% episodes. Across all ten coins FULL_BUY emitted 30 signals, all failed this target definition. These results do NOT support a profitability or high-accuracy claim. V6 is an instrumentation/replay and stability improvement, not a proven profitable strategy.

## App changes

- Native Java features; no Python/Termux runtime.
- Native offline Historical Replay with symbol/variant selection and timestamped outcomes.
- Persisted confirmed signal journal remains independent of ranking.
- Data failures stay separate from technical cancellation; soft condition weakness enters RECHECK for 30 seconds; hard out-of-range rejection cancels immediately. RECHECK is not advertised as fresh BUY.
- Manual single-coin follow retained. BUY push is gated under the same selection lock, old selected BUY notification is removed on deselection.
- Selected-coin entry requests drop sequential 3m/5m/aggTrades calls; 1m plus quote and conditional depth remain. This reduces request dependencies, not a measured guarantee of latency.
- Build uses checked-in resolved Java sources directly, eliminating build-time historical patch replay. Other workflow jobs already exclude this branch.

## Reproduce

With JDK 17 and org.json 20240303:

    bash tests/run-v6.sh /tmp/json.jar
    java -cp /tmp/v6tests com.wahshi.cryptoexplosionradar.ReplayCli app/src/main/assets/replay /tmp/results.csv /tmp/alerts.csv

CI compares generated CSVs byte-for-byte, builds APK, checks package/version/icon/signature, then uploads a nonempty artifact and SHA-256.
