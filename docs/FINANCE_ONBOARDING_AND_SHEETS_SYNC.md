# Financial onboarding and Google Sheets recovery

## Product rules

- The Room database on the Android device remains the primary source of truth.
- The app must work without an internet connection. Google Sheets is an optional, user-authorized off-device recovery copy.
- No bank passwords, OTPs, or payment-app credentials are collected.
- A spreadsheet connection is never enabled silently.

## First launch

1. Do not create fake "Primary bank" or "Cash wallet" accounts.
2. Route a new installation to Accounts so the owner can add real accounts.
3. Capture each bank/wallet's current balance and each credit card's current outstanding amount, credit limit, and statement due day.
4. Let users add multiple accounts and cards. The account list shows bank/cash balances, card outstanding, available credit, and limits.
5. Statement imports and notification capture add transaction history locally. They do not independently reveal a bank's complete live balance or card limit.

## Google Sheets backup and restore

The linked spreadsheet should contain separate, human-readable tabs for:
- Transactions
- Accounts
- Budgets
- Bill reminders
- Sync metadata

Each row needs a stable record identifier and last-modified timestamp. The sync engine must be idempotent, preserve account-to-transaction relationships, track deletions, and detect conflicting edits. It must not use row numbers as record identity.

### User actions

- **Update existing sheet:** upload local changes to the linked spreadsheet.
- **Import from sheet:** preview the selected spreadsheet data, create a local recovery point, then merge or replace only after explicit confirmation.
- **Sync both ways:** compare local and remote changes, apply non-conflicting changes, and show conflicts for user resolution.
- **Create/link sheet:** allow creating a new backup spreadsheet or linking an existing spreadsheet by its URL/ID.

### Safety requirements

- Keep OAuth access tokens in memory only; request authorization when needed.
- Request only the Google Sheets scope required for the selected operation.
- Do not send data unless the user explicitly starts or enables sync.
- Provide last-sync time, result, and error details.
- Never silently overwrite a newer local record with an older spreadsheet row.
- A spreadsheet restore must validate all tabs and references before modifying Room.
- Take an encrypted local backup before any destructive restore.
- Keep encrypted file backup available as an independent recovery route.
- Automatic daily sync is opt-in, configurable, and uses WorkManager constraints (for example Wi-Fi/charging). The app remains fully usable offline.

## Implementation sequence

1. First-launch account setup and removal of placeholder accounts.
2. Versioned full-data spreadsheet export, including account and card information.
3. Link/create spreadsheet and import preview/restore.
4. Stable IDs, change tracking, deletion tracking, and conflict-safe two-way sync.
5. Optional daily background backup and sync status.
6. Unit, instrumentation, migration, duplicate-sync, partial-failure, and restore tests.

## Balance semantics

A manually entered balance is a user-provided snapshot, not a live bank balance. To avoid double counting when importing older statements, the data model must record the snapshot's effective date and calculate only transactions after that date. Credit-card outstanding is represented as a liability; the UI should ask for a positive outstanding amount and store it using the app's liability sign convention.
