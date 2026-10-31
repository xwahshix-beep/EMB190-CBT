# Explosion Radar 6.10.0 research

The research APK uses package `com.wahshi.cryptoexplosionradar.research` and label `Explosion Radar Research`, so it installs beside the older app without deleting its data. Settings and logs are separate; the original signing private key was not available.

Built on repository 6.9.3-research (a120b975), preserving its fail-closed BUY policy and older records. The new challenger is wired into the live scanner, displayed on the main screen via the flow comparison button, and exportable through Android's document picker. It never places orders or overrides production BUY gating.

Implemented:
- Causal change detector: robust log-turnover and actual trade-count baseline (117 minutes), separate eight-minute detection window, bounded joint CUSUM with fixed experimental thresholds. This is not Bayesian inference or a calibrated probability.
- Buyer persistence over three closed minutes, relative taker-share change, price response, absorption-risk label and volatility-scaled extension rejection.
- Separate buildup / impulse / breakout descriptions; instantaneous impulses cannot claim persistent buying.
- Paired baseline/challenger records on the same candles; fixed first-available 15-minute controls including WAIT, and distinct event-triggered samples. Immutable observation time/price; duplicate candles ignored; missing outcomes remain missing. 1/5/15-minute gross forward observations.
- Per-candle event/receipt/decision timestamps, clock-skew flag and explicit nulls when a source event timestamp is unknown (REST). These are wall-clock diagnostics, not RTT or proof of phone notification delivery.
- Bounded local research storage (1500 samples, seven days) with eviction count and JSON export. Export frequently for a full study. Current status is timestamped; prior observations are retained until the stated storage limits.

Validation: all native regression suites plus new causal and recording tests. Exploratory replay covers all ten packaged datasets (69,680 minute bars). Fixed 15-minute discovery cooldown per symbol/model. Entry at next minute open, exit at minute 15 close, fees 0.10% and slippage 0.05% each side. Stress adds two minutes of delay and raises slippage to 0.15% each side. No depth/spread/fill simulation or portfolio construction. Signals are correlated and the universe/time period was previously selected, so this is not independent validation.

| Discovery model | Observations | Mean net return | Mean stress return |
|---|---:|---:|---:|
| Previous | 438 | -0.280501% | -0.497944% |
| Transition | 384 | -0.231016% | -0.448446% |
| Fixed 15-minute control | 4550 | -0.274529% | -0.473187% |

The new mean is less negative in this sample; profitability and statistical superiority are NOT established. No threshold tuning was performed in response to these replay returns. The original production-disabled flag remains false. CI reproduces the packaged evidence rather than replacing unfavorable results.

Not implemented in this phase: live trade-level Hawkes inference, order-book reconstruction/depletion estimation, a hosted collector, cross-exchange lead-lag, calibrated net-profit probabilities or production BUY activation. Minute bars cannot establish order cancellation, whale identity or true order-book absorption. Candidate discovery remains on the existing scanner's cadence; a new scoring model does not eliminate minute-close and scan delays.

Research basis:
- https://arxiv.org/abs/2307.02375
- https://doi.org/10.1111/mafi.12413
- https://link.springer.com/article/10.1007/s10203-026-00570-z
- https://github.com/binance/binance-spot-api-docs/blob/master/web-socket-streams.md
