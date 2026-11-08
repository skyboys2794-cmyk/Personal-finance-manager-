# Personal Finance Manager Android App

An offline-first, single-user personal finance manager for Android.

## Project goals

- Record income, expenses, transfers, and account balances manually.
- Import bank statements from supported CSV files.
- View dashboard summaries, category breakdowns, and transaction history.
- Store financial data securely on-device using encrypted local storage.
- Optionally synchronize selected data with Google Sheets.
- Build a debug Android APK automatically with GitHub Actions.

## Design constraints

- Android APK is the target deliverable.
- Single-user app; no account or hosted-backend requirement.
- Offline-first: core finance features work without an internet connection.
- Google Sheets sync is optional and requires explicit user configuration.
- Never commit credentials, API keys, or personal financial data to this repository.

## Build

The Android project and automated APK workflow will be added in subsequent commits. The debug APK will be published as a downloadable GitHub Actions artifact.
