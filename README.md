# Personal Finance Manager

An offline-first Android app for tracking personal income and expenses.

## Initial features

- Record income and expenses in Indian rupees (₹).
- View total balance, income, and expenses.
- Categorize transactions.
- Review and delete recent transactions.
- Store transactions locally with Room; no account or server required.
- Build a debug APK automatically through GitHub Actions.

## Build the APK

The workflow runs on pushes to `main`, pull requests, and manual dispatches.

1. Open the repository's **Actions** tab.
2. Select **Android debug APK**.
3. Open the latest successful run.
4. Download the `personal-finance-manager-debug-apk` artifact.

The workflow uses JDK 17 and Gradle 8.9. The APK output is `app/build/outputs/apk/debug/app-debug.apk`.

## Data and privacy

Transaction data is stored locally in the app's Room database. Android backup is disabled in this initial version. Do not add real financial records, passwords, API keys, or credentials to the Git repository.

## Planned enhancements

- Monthly reports and charts.
- CSV bank-statement import and export.
- Multiple accounts and transfers.
- Budget limits and recurring transactions.
- Optional, user-configured cloud backup.
