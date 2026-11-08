package com.skyboys.personalfinance.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.skyboys.personalfinance.R
import com.skyboys.personalfinance.data.FinanceDatabase
import com.skyboys.personalfinance.data.FinanceTransaction
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit

object FinanceNotifications {
    private const val CHANNEL = "finance_alerts"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(NotificationChannel(CHANNEL, "Finance reminders", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Bill due dates and budget threshold alerts"
            })
        }
    }

    fun notify(context: Context, id: Int, title: String, message: String) {
        ensureChannel(context)
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title).setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setAutoCancel(true).build()
        runCatching { NotificationManagerCompat.from(context).notify(id, notification) }
    }

    fun scheduleBillChecks(context: Context) {
        val request = PeriodicWorkRequestBuilder<BillReminderWorker>(24, TimeUnit.HOURS).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "personal_finance_bill_reminders", ExistingPeriodicWorkPolicy.KEEP, request
        )
    }
}

class BillReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        val reminders = FinanceDatabase.get(applicationContext).financeDao().allReminders()
        val today = Calendar.getInstance()
        val day = today.get(Calendar.DAY_OF_MONTH)
        val monthDays = today.getActualMaximum(Calendar.DAY_OF_MONTH)
        reminders.forEach { bill ->
            val daysUntilDue = (bill.dueDay - day + monthDays) % monthDays
            if (daysUntilDue in 0..3) {
                FinanceNotifications.notify(
                    applicationContext, 20_000 + bill.id.toInt(),
                    if (daysUntilDue == 0) "Bill due today" else "Bill due in $daysUntilDue day(s)",
                    "${bill.title} · ₹${"%.2f".format(Locale.US, bill.amountPaise / 100.0)}"
                )
            }
        }
        Result.success()
    } catch (_: Exception) { Result.retry() }
}

object BudgetAlertEvaluator {
    suspend fun evaluate(context: Context, transaction: FinanceTransaction) {
        if (transaction.type != "EXPENSE") return
        val dao = FinanceDatabase.get(context).financeDao()
        val budgets = dao.allBudgets().filter { it.category == transaction.category }
        if (budgets.isEmpty()) return
        val calendar = Calendar.getInstance()
        val monthKey = SimpleDateFormat("yyyy-MM", Locale.US).format(calendar.time)
        val start = (calendar.clone() as Calendar).apply {
            set(Calendar.DAY_OF_MONTH, 1); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val spent = dao.allTransactions().filter {
            it.type == "EXPENSE" && it.category == transaction.category && it.dateMillis >= start
        }.sumOf { it.amountPaise }
        budgets.filter { it.monthKey == monthKey }.forEach { budget ->
            val threshold = budget.limitPaise * budget.alertPercent / 100
            val key = "budget-alert-${budget.id}-$monthKey"
            val prefs = context.getSharedPreferences("finance_alert_state", Context.MODE_PRIVATE)
            if (spent >= threshold && !prefs.getBoolean(key, false)) {
                FinanceNotifications.notify(
                    context, 30_000 + budget.id.toInt(), "Budget alert: ${budget.category}",
                    "You've used ${budget.alertPercent}% or more of your monthly budget."
                )
                prefs.edit().putBoolean(key, true).apply()
            }
        }
    }
}
