# V6.3 streaming coverage

Public Binance combined kline_1m streams feed a bounded 130-closed-minute history per subscribed pair. REST initializes or repairs at most eight histories per cycle. Every fresh, contiguous, warmed history is evaluated locally each cycle, ahead of those REST requests. Daily and hourly ranking requests are cached for 60 seconds. Existing closed-minute feature and BUY thresholds are unchanged.

The UI reports stream connection, last packet age and ready-history count. Open candles cannot enter the decision history; last close older than 75 seconds, future closes and gaps block cached decisions. Reconnection is attempted by the service scan with a 60-second failure cooldown; stopped services invalidate callbacks and cancel the socket. A new connection is made after silence or subscription changes. Limit 900 streams per connection; remaining universe uses bounded REST fallback. Fresh closed data can remain usable briefly after disconnect, until its age gate expires.

Regression tests use synthetic packets for 150 symbols, open/future rejection, aging after disconnect, out-of-order repair, duplicate handling and exact feature parity against REST-shaped data. These are engineering tests, not profitable strategy backtests or a physical Samsung/network reliability test. The live 2026-10-19 missed-move study remains blocked by HTTP 451 from the research environment and unavailable daily archive (404 at attempt time). No claim of improved financial returns or measured real exchange latency is made.

Limitations: seed warmup can take minutes; evaluation is still scheduled at roughly 15-second cadence and relies on closed 1-minute bars. Optional network requests, REST quote confirmation, OS/background scheduling and stream outages can add latency. This does not detect moves before they exist in public data. No trading keys or order execution.

Protocol: https://developers.binance.com/docs/binance-spot-api-docs/web-socket-streams
