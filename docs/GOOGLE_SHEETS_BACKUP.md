# Google Sheets backup

The app has an optional **Sheets** action. When the user taps it, Google Identity
Services requests explicit OAuth consent for the Google Sheets scope. After
authorization, the app creates a dated spreadsheet snapshot and uploads the
current transaction log. The short-lived access token is used in memory only;
the app does not save it or send data automatically.

## One-time Google Cloud setup

1. Open Google Cloud Console and create/select a project.
2. Enable the **Google Sheets API**.
3. Configure the OAuth consent screen and add test users while in testing mode.
4. Create an **Android OAuth client** for application ID
   `com.skyboys.personalfinance`.
5. Add the SHA-1 signing certificate fingerprint for each build you intend to
   use (debug and release certificates differ).
6. Ensure the OAuth app is allowed to request
   `https://www.googleapis.com/auth/spreadsheets`.

The project uses Google's Android authorization API. No client secret belongs
in the APK. Google Sheets access is optional and requires a user action and
consent each time access is needed. The upload creates a new spreadsheet
snapshot per run; it does not silently overwrite an existing sheet.

## Privacy and recovery

Local Room storage remains the primary source of truth. Users can also create
password-encrypted local backup files using the Backup action. Never commit
OAuth tokens, client secrets, bank credentials or real financial records to
GitHub. If access is revoked, the local app and encrypted file backup continue
to work normally.
