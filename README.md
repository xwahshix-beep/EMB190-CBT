# E175 MCDU Familiarization — BY:WAHSHI
English offline Android trainer. Install the APK on Android 8+. First release 1.0.0. No account, internet permission, native libraries or remote assets. This is a custom simplified practice tool, not a certified simulator or an exact Honeywell software-load reproduction.

Practice: type OOMS → L1; OMDB → R1; WH175 → L2. NEXT → DEM01 → L1. PERF → 32 → L1; 5 → R1; 300 → L2. ROUTE → R6 activates. FPL reviews. DIR accepts a route fix and requires confirmation. Data and numerical examples are synthetic/illustrative, never operational.

Build: Java17, Android SDK35, Gradle8.9, `gradle assembleDebug`. Tests: `node test-engine.cjs`. GitHub workflow verifies APK identity, icon and signature and runs an Android launch smoke test. Debug signing is for personal testing; subsequent builds may require uninstall if the CI debug certificate changes.

Replica workflow source attribution: https://github.com/Jakeschincariol/replica-skill (MIT), revision 77c9436fb3d18c3d58169efb8caf4fe906b0dc51. Application source was written fresh; no upstream code or assets embedded.

Sources and fidelity limits: replica/recon.md and in-app Scope & references.
