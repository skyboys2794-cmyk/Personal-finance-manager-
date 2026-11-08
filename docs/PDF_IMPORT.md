# PDF bank statement import

The Android app reads text-based PDF bank statements locally with Apache PDFBox Android.
No statement content or PDF password is sent to a server.

## Import flow

1. Choose **Settings → Import PDF bank statement**.
2. Select a PDF. If it is encrypted, enter its password in the unlock prompt.
3. Review extracted transaction rows and confirm debit/credit direction for ambiguous rows.
4. Choose the destination bank, cash, or wallet account and import.
5. Existing transactions are retained. Matching date, description, amount, type, and account rows are skipped as duplicates.

## Notes and limitations

- PDF passwords are held only in temporary UI memory and cleared after reading; they are not saved.
- The importer supports text-based PDFs. Scanned/image-only statements require OCR and are reported as having no readable rows.
- Bank statement layouts differ. The preview and debit/credit review are intentional safeguards; verify the extracted rows before importing.
- Imported rows are stored in the existing Room transactions table and immediately contribute to the account's calculated balance.
- PDFBox Android is Apache-licensed and runs on-device; no paid PDF API is used.
