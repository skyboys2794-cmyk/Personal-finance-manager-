# Google Sheets backup integration

The repository includes a Google Sheets REST adapter at
`GoogleSheetsBackupClient.kt`. It creates a spreadsheet and uploads the
transaction log as a tabular snapshot. The adapter accepts a short-lived OAuth
access token; it does not store credentials or embed a client secret.

## Required Google setup

1. Create or select a project in Google Cloud Console.
2. Enable the Google Sheets API.
3. Configure the OAuth consent screen for the intended test/production audience.
4. Create an Android OAuth client using application ID
   `com.skyboys.personalfinance` and the app signing certificate SHA-1.
5. Request the least-privilege Sheets scope:
   `https://www.googleapis.com/auth/spreadsheets`.
6. Obtain user consent and a short-lived access token through Google's Android
   authorization flow, then call
   `GoogleSheetsBackupClient().createBackup(accessToken, transactions)`
   on a background dispatcher.

Do not put OAuth client secrets, refresh tokens, or service-account private keys
in the APK or Git repository. A user must explicitly authorize the upload.
Google Sheets backup is optional; local use and encrypted file backups work
without a Google account or bank connection.

## Current integration boundary

The REST upload client is implemented, but the in-app Google OAuth consent and
"Sync to Sheets" screen still need to be wired after the app's OAuth client
configuration is supplied. No financial data is sent to Google automatically.
