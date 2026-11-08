# CSV import format

The importer accepts UTF-8 CSV with a header row. Required columns are
`description` (or `payee`, `title`, `narration`, `merchant`) and
`amount` (or `value`, `transaction_amount`).

Optional columns: `date`, `type`, `category`, `account`,
`payment_method`, and `tags`.

Supported date formats: YYYY-MM-DD, DD/MM/YYYY, DD-MM-YYYY, MM/DD/YYYY.
Amounts are INR decimal values. The importer handles quoted CSV cells and
escaped double quotes. Invalid rows are skipped and counted in the import
summary; review your bank's sign convention and categories before importing.
The app does not connect to banks or fetch credentials.
