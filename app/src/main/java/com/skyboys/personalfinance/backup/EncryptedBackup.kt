package com.skyboys.personalfinance.backup

import com.skyboys.personalfinance.data.BillReminder
import com.skyboys.personalfinance.data.FinanceAccount
import com.skyboys.personalfinance.data.FinanceBudget
import com.skyboys.personalfinance.data.FinanceTransaction
import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

data class FinanceBackupData(
    val transactions: List<FinanceTransaction>,
    val accounts: List<FinanceAccount>,
    val budgets: List<FinanceBudget>,
    val reminders: List<BillReminder>
)

/** Versioned full-dataset backup encrypted with PBKDF2-HMAC-SHA256 and AES-256-GCM. */
object EncryptedBackup {
    private val random = SecureRandom()
    private const val ITERATIONS = 210_000
    private const val KEY_BITS = 256

    fun export(data: FinanceBackupData, password: CharArray): ByteArray {
        require(password.size >= 10) { "Use a backup password of at least 10 characters" }
        try {
            val root = JSONObject().put("format", "personal-finance-backup").put("version", 4)
            root.put("transactions", JSONArray().apply {
                data.transactions.forEach { t -> put(JSONObject()
                    .put("id", t.id).put("title", t.title).put("amountPaise", t.amountPaise)
                    .put("type", t.type).put("category", t.category).put("dateMillis", t.dateMillis)
                    .put("accountId", t.accountId ?: JSONObject.NULL).put("paymentMethod", t.paymentMethod)
                    .put("tags", t.tags).put("notes", t.notes).put("transferId", t.transferId ?: JSONObject.NULL)
                    .put("isRecurring", t.isRecurring).put("recurrenceFrequency", t.recurrenceFrequency)) }
            })
            root.put("accounts", JSONArray().apply {
                data.accounts.forEach { a -> put(JSONObject().put("id", a.id).put("name", a.name)
                    .put("institution", a.institution).put("type", a.type).put("openingBalancePaise", a.openingBalancePaise)
                    .put("currency", a.currency).put("isIncludedInNetWorth", a.isIncludedInNetWorth)
                    .put("creditLimitPaise", a.creditLimitPaise).put("statementDueDay", a.statementDueDay)
                    .put("balanceAsOfMillis", a.balanceAsOfMillis)) }
            })
            root.put("budgets", JSONArray().apply {
                data.budgets.forEach { b -> put(JSONObject().put("id", b.id).put("category", b.category)
                    .put("limitPaise", b.limitPaise).put("monthKey", b.monthKey).put("alertPercent", b.alertPercent)) }
            })
            root.put("reminders", JSONArray().apply {
                data.reminders.forEach { b -> put(JSONObject().put("id", b.id).put("title", b.title)
                    .put("amountPaise", b.amountPaise).put("dueDay", b.dueDay).put("category", b.category)
                    .put("accountId", b.accountId ?: JSONObject.NULL).put("enabled", b.enabled)) }
            })
            return encrypt(root.toString().toByteArray(Charsets.UTF_8), password)
        } finally { password.fill('\u0000') }
    }

    /** Accepts legacy v1 transaction-only backups and the v2 complete snapshot. */
    fun restore(bytes: ByteArray, password: CharArray): FinanceBackupData {
        val plain = try { decrypt(bytes, password) } finally { password.fill('\u0000') }
        val root = JSONObject(String(plain, Charsets.UTF_8))
        require(root.getString("format") == "personal-finance-backup") { "Unsupported backup format" }
        val version = root.getInt("version")
        require(version in 1..4) { "Unsupported backup version" }
        val transactions = root.getJSONArray("transactions").objects().map { t ->
            FinanceTransaction(
                id = if (version >= 2) t.optLong("id", 0) else 0,
                title = t.getString("title"), amountPaise = t.getLong("amountPaise"),
                type = t.getString("type"), category = t.getString("category"),
                dateMillis = t.getLong("dateMillis"),
                accountId = if (version >= 2 && !t.isNull("accountId")) t.getLong("accountId") else null,
                paymentMethod = t.optString("paymentMethod", "CASH"), tags = t.optString("tags", ""),
                notes = t.optString("notes", ""),
                transferId = if (version >= 2 && !t.isNull("transferId")) t.getString("transferId") else null,
                isRecurring = t.optBoolean("isRecurring", false), recurrenceFrequency = t.optString("recurrenceFrequency", "MONTHLY")
            )
        }
        if (version == 1) return FinanceBackupData(transactions, emptyList(), emptyList(), emptyList())
        val accounts = root.getJSONArray("accounts").objects().map { a -> FinanceAccount(
            id=a.getLong("id"), name=a.getString("name"), institution=a.optString("institution", ""),
            type=a.optString("type", "BANK"), openingBalancePaise=a.optLong("openingBalancePaise", 0),
            currency=a.optString("currency", "INR"), isIncludedInNetWorth=a.optBoolean("isIncludedInNetWorth", true),
            creditLimitPaise=a.optLong("creditLimitPaise", 0), statementDueDay=a.optInt("statementDueDay", 0),
            balanceAsOfMillis=a.optLong("balanceAsOfMillis", 0)
        ) }
        val budgets = root.getJSONArray("budgets").objects().map { b -> FinanceBudget(
            id=b.getLong("id"), category=b.getString("category"), limitPaise=b.getLong("limitPaise"),
            monthKey=b.getString("monthKey"), alertPercent=b.optInt("alertPercent", 80)
        ) }
        val reminders = root.getJSONArray("reminders").objects().map { b -> BillReminder(
            id=b.getLong("id"), title=b.getString("title"), amountPaise=b.getLong("amountPaise"),
            dueDay=b.getInt("dueDay"), category=b.optString("category", "Bills"),
            accountId=if (b.isNull("accountId")) null else b.getLong("accountId"),
            enabled=b.optBoolean("enabled", true)
        ) }
        return FinanceBackupData(transactions, accounts, budgets, reminders)
    }

    private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }

    private fun encrypt(payload: ByteArray, password: CharArray): ByteArray {
        val salt = ByteArray(16).also(random::nextBytes)
        val nonce = ByteArray(12).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, derive(password, salt), GCMParameterSpec(128, nonce))
        return "PFM1".toByteArray() + salt + nonce + cipher.doFinal(payload)
    }

    private fun decrypt(bytes: ByteArray, password: CharArray): ByteArray {
        require(bytes.size > 32 && String(bytes.copyOfRange(0, 4)) == "PFM1") { "Not a supported backup file" }
        val salt = bytes.copyOfRange(4, 20)
        val nonce = bytes.copyOfRange(20, 32)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, derive(password, salt), GCMParameterSpec(128, nonce))
        return cipher.doFinal(bytes.copyOfRange(32, bytes.size))
    }

    private fun derive(password: CharArray, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, ITERATIONS, KEY_BITS)
        return try {
            SecretKeySpec(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded, "AES")
        } finally { spec.clearPassword() }
    }
}
