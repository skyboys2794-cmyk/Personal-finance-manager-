# Personal Finance Manager

An offline-first Android personal-finance app. It is designed for manual entry
and statement imports, so users can track multiple banks, cash wallets and
credit cards without paid bank-sync APIs or sharing bank credentials.

## Implemented foundation

- Modern Material 3 dashboard with monthly income, spending, net cash flow and
  category spending bars.
- Manual income/expense entry with INR amounts, categories, tags, payment
  methods and account association.
- Local Room database with a versioned 1-to-2 migration.
- Full account lifecycle: create, edit, include/exclude from net worth and safe deletion. Deletion detaches ordinary history/reminders and removes matched transfer pairs to avoid half-transfers.
- Paired account transfers with a shared reconciliation ID; transfers change account balances but are excluded from income/expense reports.
- CSV statement import with quoted-field parsing, header aliases, date parsing
  and skipped-row reporting.
- Password-encrypted full-data backups for transactions, accounts, budgets and bill reminders using PBKDF2-HMAC-SHA256 and AES-256-GCM. Restore validates/decrypts before replacing the local dataset; legacy v1 transaction-only backups remain readable.
- Interactive reports with monthly income/expense bar charts, trend line charts, category donut charts and 3/6/12-month filters.
- WorkManager bill reminder checks and threshold notification evaluator.
- Google Sheets REST upload adapter; see integration status below.
- GitHub Actions debug APK workflow.

## Build and download

The workflow runs on pushes to `main`, pull requests and manual dispatches.

1. Open the repository's **Actions** tab.
2. Select **Android debug APK**.
3. Open the latest successful run.
4. Download the `personal-finance-manager-debug-apk` artifact.

The build uses JDK 17 and Gradle 8.9. Local output:
`app/build/outputs/apk/debug/app-debug.apk`.

## CSV import

See [CSV import format](docs/CSV_IMPORT_FORMAT.md). Import is manual; the app
does not connect to bank accounts or request bank passwords.

## Backup and privacy

Complete finance backups are encrypted on-device before the user selects a file location. Use a strong password of at least 10 characters and keep it safe: there is no password recovery. Restore replaces the current local dataset with the backup contents. The Room database remains local app-private storage and is not itself encrypted at rest. Android cloud backup is disabled.

Google Sheets is optional and requires explicit user authorization. The REST
adapter is implemented, but the in-app Google OAuth consent and sync screen
still need to be connected to an app-specific OAuth client. Follow
[Google Sheets setup](docs/GOOGLE_SHEETS_BACKUP.md). Never commit client
secrets, access tokens, bank credentials or real financial data.

## Architecture and next implementation steps

- `data/`: Room entities, DAO and explicit database migration.
- `importer/`: CSV parsing.
- `backup/`: encrypted file backup and Google Sheets REST adapter.
- `reports/`: monthly cash-flow, trend and category analytics.
- `AccountManagement.kt`: account editing/deletion and paired transfers.
- `AdvancedReports.kt`: interactive bar, line and donut chart views.
- `notifications/`: bill reminder worker and budget threshold notification logic.

Accounts can be added manually; monthly budgets and bill reminders can be configured
in the Plans tab. The next milestones are account editing/deletion, richer reports,
credit-card payment reconciliation and dashboard widget expansion. Budget threshold
alerts are evaluated on newly recorded expenses; bill reminder checks run daily. Review imported bank CSV sign conventions before relying on totals.
