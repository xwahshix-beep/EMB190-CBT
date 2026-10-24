#!/usr/bin/env bash
set -euo pipefail
json=${1:?json.jar required}
src=app/src/main/java/com/wahshi/cryptoexplosionradar
mkdir -p /tmp/v6tests
javac -cp "$json" -d /tmp/v6tests "$src"/{ProductionPolicy,WakeMetrics,HolderNet,FairRotation,TradingEligibility,StreamRecovery,PerformanceCard,RetryPolicy,ResearchExport,PhaseShadow,OutcomeLedger,MinuteHistory,MinuteScanPolicy,DailyMomentumEngine,DecisionLedger,PreExplosionEngine,PreExplosionJson,HistoricalReplay,BuyStability,EntryGuard,EntryWindow,FollowEngine,FollowView,SignalJournal,SignalDisplay,EarlyWhaleMath,WatchPolicy,BuyAlertPolicy}.java tests/*.java research/ReplayCli.java
for test in ProductionPolicyTest WakeMetricsTest HolderNetTest FairRotationTest TradingEligibilityTest StreamRecoveryTest PerformanceCardTest ResearchExportTest RetryPolicyTest PhaseShadowTest OutcomeLedgerTest AlertScopeTest MinuteHistoryTest MinuteScanPolicyTest DailyMomentumTest DecisionLedgerTest PreExplosionTest EarlyWhaleMathTest WatchPolicyTest BuyAlertPolicyTest EntryGuardTest EntryWindowTest SignalDisplayTest FollowEngineTest BuyStabilityTest SignalJournalTest; do
 java -cp "$json:/tmp/v6tests" "com.wahshi.cryptoexplosionradar.$test"
done

mkdir -p /tmp/v6streamtests
javac -cp "$json:/tmp/v6tests" -d /tmp/v6streamtests tests/streams/okhttp3/*.java "$src/MinuteStreams.java" tests/streams/MinuteStreamsRecoveryTest.java
java -cp "$json:/tmp/v6tests:/tmp/v6streamtests" com.wahshi.cryptoexplosionradar.MinuteStreamsRecoveryTest
