# 6.12 engineering update

No research rule, engine threshold, prospective collection or production BUY activation changes.

- HTTP transport receipt health is separate from completed scan time. Display thresholds: depth 3s; candles 60s; prices 15s; market summary 120s; eligibility/announcement pages 20min. Transport success does not establish payload validity or freshness of every symbol. WebSocket depth freshness retains its existing 3s engine guard.
- Operational failures and BUY suppression use the existing `v6_trace` DecisionLedger, not a second journal. Schema adds `decision_at`, `received_at`, `source_at`, `data_age` (milliseconds), `reason`, `event_type`, and rule/build identifiers. Unknown event/receipt times are null. Legacy aliases remain for existing exports. This bounded 300-event/24-hour journal is not the external append-only prospective study archive. No prospective readiness claim.
- Existing FairRotation resumes after each attempted symbol, including failures. Tests cover a seven-symbol universe across three batches of three, removal, interruption, wrap, duplicates and empty universe.
- BUY production asset stays literal false. Shared final dispatch tests exercise false/no callback and true/one callback with neutral copy, using an isolated sink, not Android notification delivery. Existing scope, expiry and policy tests retained. Channels are created before sends. WATCH/paper notifications describe observations, not hold/sell advice.
- Research build is explicitly non-debuggable. New application ID research612 allows side-by-side install because previous transient CI signing key is unavailable. This does not migrate old application data.
- Not tested on a physical Android phone. No claim of improved predictive performance. Official announcement HTML sources remain brittle; failures/empty extraction are surfaced, not replaced by a new source in this change.
