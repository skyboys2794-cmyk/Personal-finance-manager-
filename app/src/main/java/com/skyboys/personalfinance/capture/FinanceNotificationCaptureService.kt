package com.skyboys.personalfinance.capture

import android.app.Notification
import android.content.ComponentName
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.skyboys.personalfinance.data.FinanceDatabase
import com.skyboys.personalfinance.data.FinanceTransaction
import com.skyboys.personalfinance.categorization.MerchantMemory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.abs

/** Opt-in, local-only capture of selected payment notifications. */
class FinanceNotificationCaptureService : NotificationListenerService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_ENABLED, false)) return
        if (!isAllowedSource(sbn.packageName, prefs)) return

        val extras = sbn.notification.extras ?: return
        val parts = mutableListOf<String>()
        listOf(Notification.EXTRA_TITLE, Notification.EXTRA_TEXT, Notification.EXTRA_BIG_TEXT, Notification.EXTRA_SUB_TEXT)
            .forEach { key -> extras.getCharSequence(key)?.toString()?.takeIf(String::isNotBlank)?.let(parts::add) }
        extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.forEach { line ->
            line?.toString()?.takeIf(String::isNotBlank)?.let(parts::add)
        }
        val message = parts.distinct().joinToString(" · ").take(1200)
        if (message.isBlank()) return
        val parsed = BankNotificationParser.parse(message) ?: return

        serviceScope.launch {
            runCatching {
                val dao = FinanceDatabase.get(applicationContext).financeDao()
                val accounts = dao.allAccounts()
                val isCardSource = CARD_SOURCE_PACKAGES.contains(sbn.packageName) || parsed.paymentMethod == "CARD"
                val preferredId = prefs.getLong(if (isCardSource) KEY_CARD_ACCOUNT_ID else KEY_ACCOUNT_ID, -1L).takeIf { it > 0L }
                val preferredType = if (isCardSource) "CREDIT_CARD" else "BANK"
                val accountId = accounts.firstOrNull { it.id == preferredId }?.id
                    ?: accounts.firstOrNull { it.type == preferredType }?.id
                    ?: accounts.firstOrNull { it.type == "WALLET" }?.id
                val transactionTime = sbn.postTime.takeIf { it > 0L } ?: System.currentTimeMillis()
                val learnedCategory = MerchantMemory.learnedCategory(dao, parsed.title) ?: parsed.category
                val duplicate = dao.allTransactions().any { item ->
                    item.amountPaise == parsed.amountPaise && item.type == parsed.type &&
                        abs(item.dateMillis - transactionTime) <= 90_000L && item.tags.contains(TAG_CAPTURE)
                }
                if (!duplicate) {
                    dao.insert(FinanceTransaction(
                        title = parsed.title, amountPaise = parsed.amountPaise, type = parsed.type,
                        category = learnedCategory, dateMillis = transactionTime, accountId = accountId,
                        paymentMethod = parsed.paymentMethod,
                        tags = "$TAG_CAPTURE;SOURCE=${sbn.packageName};${if (learnedCategory != "Other") "USER_LEARNED_CATEGORY" else "CATEGORY_REVIEW"}",
                        notes = "Captured locally from an app notification"
                    ))
                }
            }
        }
    }

    private fun isAllowedSource(packageName: String, prefs: android.content.SharedPreferences): Boolean {
        val key = SOURCE_KEYS[packageName] ?: return false
        return prefs.getBoolean(key, true)
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        const val PREFS = "automatic_capture"
        const val KEY_ENABLED = "capture_enabled"
        const val KEY_ACCOUNT_ID = "default_account_id"
        const val KEY_CARD_ACCOUNT_ID = "default_card_account_id"
        const val TAG_CAPTURE = "AUTO_CAPTURE"
        const val KEY_PHONEPE = "source_phonepe"
        const val KEY_GPAY = "source_gpay"
        const val KEY_PAYTM = "source_paytm"
        const val KEY_AMAZON = "source_amazon"
        const val KEY_SBI = "source_sbi"
        const val KEY_HDFC = "source_hdfc"
        const val KEY_ICICI = "source_icici"
        const val KEY_AXIS = "source_axis"
        const val KEY_KOTAK = "source_kotak"
        const val KEY_IDFC = "source_idfc"
        const val KEY_CRED = "source_cred"
        const val KEY_ONECARD = "source_onecard"

        val CARD_SOURCE_PACKAGES = setOf(
            "com.sbicard", "com.fpl.onecard", "com.dreamplug.androidapp"
        )
        val SOURCE_KEYS = mapOf(
            "com.phonepe.app" to KEY_PHONEPE,
            "com.google.android.apps.nbu.paisa.user" to KEY_GPAY,
            "net.one97.paytm" to KEY_PAYTM,
            "in.amazon.mShop.android.shopping" to KEY_AMAZON,
            "com.sbicard" to KEY_SBI,
            "com.snapwork.hdfc" to KEY_HDFC,
            "com.csam.icici.bank.imobile" to KEY_ICICI,
            "com.axis.mobile" to KEY_AXIS,
            "com.kotak.bank.mobile" to KEY_KOTAK,
            "com.idfcfirstbank.optimus" to KEY_IDFC,
            "com.dreamplug.androidapp" to KEY_CRED,
            "com.fpl.onecard" to KEY_ONECARD
        )

        fun isListenerEnabled(context: android.content.Context): Boolean {
            val enabled = android.provider.Settings.Secure.getString(
                context.contentResolver, "enabled_notification_listeners"
            ).orEmpty()
            val component = ComponentName(context, FinanceNotificationCaptureService::class.java).flattenToString()
            return enabled.split(':').any { it.equals(component, ignoreCase = true) }
        }
    }
}

internal data class ParsedBankNotification(
    val title: String, val amountPaise: Long, val type: String, val category: String, val paymentMethod: String
)

internal object BankNotificationParser {
    private val currencyAmount = Regex(
        """(?i)(?:₹|rs\.?|inr)\s*([0-9][0-9,]*(?:\.\d{1,2})?)|([0-9][0-9,]*(?:\.\d{1,2})?)\s*(?:₹|rs\.?|inr)"""
    )
    private val debitWords = Regex("""(?i)\b(paid|payment|debited|debit|spent|sent|purchase|withdrawn|transferred to|txn of)\b""")
    private val creditWords = Regex("""(?i)\b(credited|credit|received|refund|refunded|reversal|cashback|deposited)\b""")
    private val failureWords = Regex("""(?i)\b(failed|declined|unsuccessful|pending|processing|otp|one.time.password)\b""")

    fun parse(message: String): ParsedBankNotification? {
        if (failureWords.containsMatchIn(message)) return null
        val amountMatch = currencyAmount.find(message) ?: return null
        val amountText = (amountMatch.groups[1]?.value ?: amountMatch.groups[2]?.value)?.replace(",", "") ?: return null
        val amount = amountText.toBigDecimalOrNull() ?: return null
        if (amount.signum() <= 0) return null
        val type = when {
            creditWords.containsMatchIn(message) -> "INCOME"
            debitWords.containsMatchIn(message) -> "EXPENSE"
            else -> return null
        }
        val paise = runCatching { amount.movePointRight(2).setScale(0, java.math.RoundingMode.HALF_UP).longValueExact() }.getOrNull() ?: return null
        if (paise <= 0L) return null
        val cleaned = message
            .replace(Regex("""(?i)(?:₹|rs\.?|inr)\s*[0-9][0-9,]*(?:\.\d{1,2})?"""), " ")
            .replace(Regex("""(?i)\b(successful|successfully|debited|credited|paid|payment|received|sent|from|to|your|account|a/c|xx\d+)\b"""), " ")
            .replace(Regex("""\s+"""), " ").trim()
        val title = cleaned.takeIf { it.length >= 3 }?.take(90) ?: "Bank transaction"
        return ParsedBankNotification(
            title, paise, type, MerchantCategorizer.categoryFor(message),
            when {
                Regex("""(?i)\\b(credit card|card ending|card purchase|credit card transaction)\\b""").containsMatchIn(message) -> "CARD"
                message.contains("upi", true) || message.contains("phonepe", true) ||
                    message.contains("gpay", true) || message.contains("paytm", true) -> "UPI"
                else -> "BANK_TRANSFER"
            }
        )
    }
}

internal object MerchantCategorizer {
    private val rules = linkedMapOf(
        "Food" to listOf("swiggy", "zomato", "restaurant", "cafe", "food", "dominos", "mcdonald", "pizza", "hotel"),
        "Transport" to listOf("uber", "ola", "rapido", "irctc", "metro", "fuel", "petrol", "diesel", "fastag"),
        "Shopping" to listOf("amazon", "flipkart", "myntra", "meesho", "ajio", "retail", "mall"),
        "Bills" to listOf("electricity", "bescom", "tsspdcl", "apcpdcl", "water bill", "gas bill", "recharge", "broadband"),
        "Mobile & Internet" to listOf("airtel", "jio", "vi recharge", "vodafone", "bsnl"),
        "Entertainment" to listOf("netflix", "spotify", "prime video", "hotstar", "bookmyshow", "youtube"),
        "Medical" to listOf("pharmacy", "medical", "apollo", "medplus", "hospital", "clinic", "medicine"),
        "Education" to listOf("college", "school", "university", "tuition", "course", "udemy"),
        "Salary" to listOf("salary", "payroll"),
        "Housing" to listOf("rent", "maintenance", "housing")
    )
    fun categoryFor(text: String): String {
        val value = text.lowercase(Locale.ROOT)
        return rules.entries.firstOrNull { (_, words) -> words.any(value::contains) }?.key ?: "Other"
    }
}
