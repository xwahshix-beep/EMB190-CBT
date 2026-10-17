# V6.1 Research Lab

The existing minute radar is retained. The new native Android research screen adds:

- packaged actual daily-momentum historical metrics, including a frozen 2026 extension;
- user-triggered public-data checks for the last completed UTC daily candle;
- causal 20-day breakout, SMA50 and Wilder ATR14 diagnostics;
- explicit stale, incomplete and gapped-data rejection;
- locally persisted paper observations with duplicate-last-observation protection;
- no order endpoints, API keys or automatic trade execution.

The single-symbol check is NOT the full portfolio strategy: it does not establish top-20 historical liquidity rank, execute orders, implement positions or reconstruct transaction costs. WATCH means a research candidate, never proven profitability. The complete 2022–2025 backtest package was delivered separately; the app summary identifies assumptions and dates. The 2026 run preserves the same rules rather than tuning them on the failed holdout.

The laboratory does not run in the background. Existing radar background behavior is unchanged. The research notes are stored separately from old app preferences.

Tests verify closed-candle gating, stale data, gaps, fixed ATR calculation, and future-data isolation, alongside existing native-engine/state regressions. APK checks require the new Java classes and summary asset. Compilation and artifact validation do not constitute an on-device Samsung S23 test or a live paper-trading track record.

Build uses the existing debug signing workflow. A previously installed build signed with a different debug key may not accept an in-place update; preserve existing observations before uninstalling anything. Do not claim stable signing-key continuity.
