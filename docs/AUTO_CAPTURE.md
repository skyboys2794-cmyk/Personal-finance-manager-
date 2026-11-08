# Automatic notification transaction capture

This opt-in feature uses Android NotificationListenerService and processes selected payment-app notifications locally.

Supported package IDs:
- PhonePe: com.phonepe.app
- Google Pay India: com.google.android.apps.nbu.paisa.user
- Paytm: net.one97.paytm
- Amazon Shopping: in.amazon.mShop.android.shopping

Capture is disabled by default. The user must enable it and grant Android notification access. Only selected package notifications are inspected. Notification text is parsed in memory and is not uploaded or retained as raw text.

Transactions with a recognizable currency amount and debit/credit wording are saved into the existing Room database. Merchant keyword rules assign categories; unknown merchants are categorized as Other. Similar alerts with the same amount and direction within 90 seconds are suppressed.

## Coverage and limitations
Notification formats vary by app, issuer, language, and Android version. Notifications may be hidden, delayed, absent, or omit merchant/amount details. This cannot guarantee every transaction or provide historical account/card data. PDF/CSV statement import and reconciliation remain necessary to catch missed activity. No SMS permission, accessibility service, unofficial bank API, credentials, OTP, or cloud notification forwarding is used.
