# Verification plan

The GitHub Actions workflow runs:

1. `testDebugUnitTest` for monthly analytics and transfer accounting.
2. `connectedDebugAndroidTest` on an Android 35 Pixel 2 emulator.
3. `assembleDebug` and uploads the APK only after tests pass.

Instrumentation coverage includes:
- Main dashboard and navigation between Overview, Activity, Accounts and Plans.
- Encrypted backup round-trip for transactions, accounts, budgets and bill reminders.
- Rejection of an incorrect backup password.

Manual release checks still required on real phones:
- Android 8/9 (API 26/28), Android 12/13 and Android 14/15/16.
- Small and large screens, display scaling, dark mode and TalkBack.
- File picker export/import, CSV imports from actual bank statements and backup restore after app reinstall.
- Notification permission denied/granted, exact bill due dates, timezone and reboot behavior.
- Google Sheets OAuth with the app's production package name and signing certificate.

Emulator CI is not a substitute for physical-device testing. Do not treat a passing CI run as proof of bank-statement correctness or production OAuth readiness.
