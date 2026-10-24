# V6.9 recovery and trading eligibility — 2026-10-24

- Binance serverShutdown is handled in raw and combined messages; replacement connection starts immediately. Old callbacks cannot disconnect its replacement.
- Disconnects invalidate in-flight REST seeds and cached minute history. Automatic alerts require a fresh, contiguous history for that symbol. Manual follow retains its separate EntryWindow validation.
- Before automatic confirmation and all buy notifications, public exchangeInfo must report TRADING and isSpotTradingAllowed=true. Missing, unknown, expired, empty or failed metadata blocks the alert with an Arabic reason.
- Metadata is shared across symbols for 60 seconds; failures retry no sooner than 30 seconds. This bounds request volume, but cannot detect status changes instantly within the cache lifetime.
- Source status fetch time is stored for notification checks. No private account endpoints or trade execution are added.
- Version labels use BuildConfig rather than fixed strings.

Validation: 23 local test programs passed, including synthetic socket callbacks (not a real Binance connection), status transitions, failed/expired metadata, stale REST responses, candle gaps and duplicates. CI additionally compiles the real Android dependency graph and verifies APK package/version/signature/assets.

Limitations: no physical Samsung test, no proof of background delivery or battery improvement. Historical research assets are unchanged; long-horizon validation remains separate and incomplete. No new profitability claim. An unavailable status check can suppress a valid opportunity; that is an intentional fail-closed tradeoff.
