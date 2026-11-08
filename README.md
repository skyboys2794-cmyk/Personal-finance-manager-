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
- Multiple account records (cash and bank account seed records on first launch).
- CSV statement import with quoted-field parsing, header aliases, date parsing
  and skipped-row reporting.
- Password-encrypted portable transaction backups using PBKDF2-HMAC-SHA256
  and AES-256-GCM; restore is supported from the app.
- Monthly category analytics and net-worth calculation logic.
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

Transaction backups are encrypted on-device before the user selects a file
location. Use a strong password of at least 10 characters and keep it safe:
there is no password recovery. The local Room database remains the primary
source of truth. Android cloud backup is disabled.

Google Sheets is optional and requires explicit user authorization. The REST
adapter is implemented, but the in-app Google OAuth consent and sync screen
still need to be connected to an app-specific OAuth client. Follow
[Google Sheets setup](docs/GOOGLE_SHEETS_BACKUP.md). Never commit client
secrets, access tokens, bank credentials or real financial data.

## Architecture and next implementation steps

- `data/`: Room entities, DAO and explicit database migration.
- `importer/`: CSV parsing.
- `backup/`: encrypted file backup and Google Sheets REST adapter.
- `reports/`: monthly cash-flow and category analytics.
- `notifications/`: bill reminder worker and budget threshold notification logic.

The next UI milestone is full CRUD for accounts, budgets and bill reminders,
plus a dedicated reports/settings area and Google OAuth consent flow. Dashboard
widget preferences and account/card balance reconciliation also remain to be
completed. Review imported bank CSV sign conventions before relying on totals.
