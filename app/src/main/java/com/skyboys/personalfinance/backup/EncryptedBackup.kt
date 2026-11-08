package com.skyboys.personalfinance.backup

import android.util.Base64
import com.skyboys.personalfinance.data.FinanceTransaction
import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Portable password-encrypted transaction backup.
 * Format: PFM1 | 16-byte salt | 12-byte GCM nonce | AES-GCM ciphertext.
 * Never store the password in the app or repository; users must retain it.
 */
object EncryptedBackup {
    private val random = SecureRandom()
    private const val ITERATIONS = 210_000
    private const val KEY_BITS = 256

    fun export(transactions: List<FinanceTransaction>, password: CharArray): ByteArray {
        require(password.size >= 10) { "Use a backup password of at least 10 characters" }
        val payload = JSONObject().put("format", "personal-finance-backup")
            .put("version", 1).put("transactions", JSONArray().apply {
                transactions.forEach { t ->
                    put(JSONObject().put("title", t.title).put("amountPaise", t.amountPaise)
                        .put("type", t.type).put("category", t.category).put("dateMillis", t.dateMillis)
                        .put("paymentMethod", t.paymentMethod).put("tags", t.tags).put("notes", t.notes))
                }
            }).toString().toByteArray(Charsets.UTF_8)
        val salt = ByteArray(16).also(random::nextBytes)
        val nonce = ByteArray(12).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, derive(password, salt), GCMParameterSpec(128, nonce))
        val encrypted = cipher.doFinal(payload)
        password.fill('\u0000')
        return "PFM1".toByteArray() + salt + nonce + encrypted
    }

    fun restore(bytes: ByteArray, password: CharArray): List<FinanceTransaction> {
        require(bytes.size > 32 && String(bytes.copyOfRange(0, 4)) == "PFM1") { "Not a supported backup file" }
        val salt = bytes.copyOfRange(4, 20)
        val nonce = bytes.copyOfRange(20, 32)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, derive(password, salt), GCMParameterSpec(128, nonce))
        val plain = cipher.doFinal(bytes.copyOfRange(32, bytes.size))
        password.fill('\u0000')
        val root = JSONObject(String(plain, Charsets.UTF_8))
        require(root.getString("format") == "personal-finance-backup" && root.getInt("version") == 1)
        val items = root.getJSONArray("transactions")
        return (0 until items.length()).map { i ->
            val t = items.getJSONObject(i)
            FinanceTransaction(
                title = t.getString("title"), amountPaise = t.getLong("amountPaise"),
                type = t.getString("type"), category = t.getString("category"),
                dateMillis = t.getLong("dateMillis"), paymentMethod = t.optString("paymentMethod", "CASH"),
                tags = t.optString("tags", ""), notes = t.optString("notes", "")
            )
        }
    }

    private fun derive(password: CharArray, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, ITERATIONS, KEY_BITS)
        return try {
            SecretKeySpec(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded, "AES")
        } finally { spec.clearPassword() }
    }
}
