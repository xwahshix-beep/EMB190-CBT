# E175 MCDU familiarization recon — 2026-10-26
Scope: Android APK, English, offline. User requested Replica; not a certified or complete FMS replica. Original code and vector icon, no proprietary assets.
## Primary sources
- Honeywell NGFMS E-Jets platform and training-document index: https://www.honeywellaerospace.com/content/dam/aerobt/en/documents/learn/platforms/airlines-%26-cargo/brochures/n61-2230-000-000_embraer_ejets.pdf (pp.15–16).
- Waypoint entry video linked by Honeywell: https://youtu.be/v6DG0ZQkQec
- Performance initialization video linked by Honeywell: https://youtu.be/4DkyHGUjNEM
Videos are reference links, not claimed watched or transcribed. Manufacturer overview supports system context, not exact trainer page fidelity. No operator-specific guide supplied or reviewed. Exact labels, positions and sequences remain unverified, explicitly simplified.
## Screen inventory
S01 Route 1/2: origin/destination/flight ID; blank/modified/active/error.
S02 Route 2/2: add/remove synthetic fixes; empty/full/duplicate/error.
S03 Flight plan: paginated ordered fix list; modified/active/direct selection.
S04 Performance init: manual ZFW/fuel/FL/reserve; no performance computation.
S05 Departure: synthetic choice only, no operational procedure.
S06 Arrival: synthetic choice only.
S07 Navigation index: links; sensors not simulated.
S08 Direct-to: input, review, confirm/cancel; no guidance.
S09 Progress: stored-plan summary; no prediction.
S10 Menu/radio scope: navigation, explicit unsupported radio screen.
## Flows
F01 enter origin/destination/flight ID, append fix, enter illustrative perf values, activate, review.
F02 edit/delete fix, reactivation required, page through flight plan.
F03 direct-to active route fix, review and confirm/cancel.
F04 restore local session, load demo or reset with confirmation.
## Components and data
Six line-select keys per side, scratchpad, function keys, alpha/numeric keyboard, page indicator, guided progress, confirmation dialogs. One local plan entity; no accounts or backend. Current load and airline specifics unknown.
## Exclusions
Real AIRAC/procedures, performance model, flight dynamics, radio tuning, sensors, exact software-load match. No original proprietary assets or binary analysis. No real-user testimonials collected.
