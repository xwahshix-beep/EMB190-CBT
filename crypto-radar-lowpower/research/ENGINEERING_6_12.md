# 6.12 engineering update

No research rule, engine threshold, prospective collection or production BUY activation changes.

- HTTP transport receipt health is separate from completed scan time. Display thresholds: depth 3s; candles 60s; prices 15s; market summary 120s; eligibility/announcement pages 20min. Transport success does not establish payload validity or freshness of every symbol. WebSocket depth freshness retains its existing 3s engine guard.
- Operational failures and BUY suppression use the existing `v6_trace` DecisionLedger, not a second journal. Schema adds `decision_at`, `received_at`, `source_at`, `data_age` (milliseconds), `reason`, `event_type`, and rule/build identifiers. Unknown event/receipt times are null. Legacy aliases remain for existing exports. This bounded 300-event/24-hour journal is not the external append-only prospective study archive. No prospective readiness claim.
- Existing FairRotation resumes after each attempted symbol, including failures. Tests cover a seven-symbol universe across three batches of three, removal, interruption, wrap, duplicates and empty universe.
- BUY production asset stays literal false. Shared final dispatch tests exercise false/no callback and true/one callback with neutral copy, using an isolated sink, not Android notification delivery. Existing scope, expiry and policy tests retained. Channels are created before sends. WATCH/paper notifications describe observations, not hold/sell advice.
- Engineering build is explicitly non-debuggable; code/resource shrinking is explicitly disabled. New application ID engineering612 allows side-by-side install because previous transient CI signing key is unavailable. This does not migrate old application data.
- Not tested on a physical Android phone. No claim of improved predictive performance. Official announcement HTML sources remain brittle; failures/empty extraction are surfaced, not replaced by a new source in this change.

## Corrected release identity
Version name is exactly `6.12-engineering`, version code 80. `assets/build-provenance.json` is generated from the checked-out GitHub commit before packaging. The workflow verifies it against GITHUB_SHA and emits release-identity.json with the APK SHA-256 after signing. These are explicit provenance records; the automatic version-control-info metadata is not used as evidence.

The former operational-event marker `unchanged-6.11` meant to refer to the engine baseline, not the app build, and was ambiguous. Operational events now set rules_version to null (no rule evaluated) and identify rules_baseline_commit separately. Engine source hashes are included in build provenance. This does not assert the whole application is unchanged.

Resource entry filenames may be rewritten by Android packaging; neither a renamed resource nor disabling shrinking proves on-device rendering. A physical phone icon/notification check remains outstanding. No claim of physical-device verification is made.

## HMAC string attribution
Static DEX inspection of the prior 6.12 APK (SHA-256 6e619786021e2e2e261f3207c18ee9aa7ba8e0c1078f160eaa079e3b6c0704aa) locates HmacSHA256 in okio.Buffer, okio.ByteString, and HashingSink/HashingSource companion methods. Only library wrapper callers were found; no app-origin direct callers were found by Androguard cross-reference analysis. This is dependency functionality, not evidence of trading-request signing or an APK signature. Static analysis does not rule out reflective/dynamic behavior. No physical phone rendering validation was performed.
