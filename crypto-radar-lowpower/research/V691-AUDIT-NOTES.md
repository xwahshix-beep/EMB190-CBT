# V6.9.1 audit repair — 2026-10-24

Changes:
- Fair on-chain rotation: three distinct symbols per batch, lexicographic cursor updated only for actual attempts (including provider failures). No quiet-priority starvation.
- Solana: finalized union of previous/current top-20 account cohorts, at most 40 accounts, minContextSlot continuity. Signed exact-decimal changes aggregated by owner BEFORE thresholds and concentration guard. New accounts lack a baseline; missing accounts invalidate comparison rather than becoming zero. Changed ownership excludes affected owners. Baselines older than 24 hours, backwards clock and non-increasing slots yield no inference.
- Top-20 coverage is PARTIAL. Even distinct owners need not be economically independent; balance increases do not prove purchases. Symbol/price DEX matching is not authoritative contract identity.
- Unverified on-chain results no longer boost HybridEngine or CatalystEngine. Research scanning remains; no new whale push is enabled. Removed dead maybeAlert/empty notification implementation.
- Enrichment attempt/error counts shown in scanner summary; sanitized error class/stage stored without URLs, credentials or wallet addresses. Main market failure behavior remains fail/no decision.
- TradeManager paper notifications ensure their own channel; HTTP errors use MarketHttp and reject invalid prices; symbols encoded; per-symbol errors recorded. This legacy path is not newly scheduled and actual active watch notification delivery is not claimed fixed by this change.
- Service/version labels use BuildConfig. Version 74 / 6.9.1-audit.

Validation:
- 25 Java test programs passed locally, including 10 HolderNet scenarios and 5 FairRotation scenarios. Existing stream, scope, ledger, engine and follow regressions retained.
- CI Android build/signature/assets verification must pass before delivery.
- No physical Samsung test, battery measurement, live RPC load test or independent profitability validation performed.
- Long historical validation and contract safety/authoritative identity remain unfinished. No claim of honeypot detection. No real order execution.
- Published APK workflow uses assembleDebug, so this remains an experimental debug build, not a production release.
