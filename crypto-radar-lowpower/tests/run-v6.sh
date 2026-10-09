#!/usr/bin/env bash
set -euo pipefail
json=${1:?json.jar required}
src=app/src/main/java/com/wahshi/cryptoexplosionradar
mkdir -p /tmp/v6tests
javac -cp "$json" -d /tmp/v6tests "$src"/{PreExplosionEngine,PreExplosionJson,HistoricalReplay,BuyStability,EntryGuard,EntryWindow,FollowEngine,FollowView,SignalJournal,SignalDisplay,EarlyWhaleMath,WatchPolicy,BuyAlertPolicy}.java tests/*.java research/ReplayCli.java
for test in PreExplosionTest EarlyWhaleMathTest WatchPolicyTest BuyAlertPolicyTest EntryGuardTest EntryWindowTest SignalDisplayTest FollowEngineTest BuyStabilityTest SignalJournalTest; do
 java -cp "$json:/tmp/v6tests" "com.wahshi.cryptoexplosionradar.$test"
done
