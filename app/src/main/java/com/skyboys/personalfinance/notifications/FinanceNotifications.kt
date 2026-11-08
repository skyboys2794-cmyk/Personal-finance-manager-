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
        val preferences = applicationContext.getSharedPreferences("finance_alert_state", Context.MODE_PRIVATE)
        reminders.filter { it.enabled && it.dueDay in 1..31 }.forEach { bill ->
            val daysUntilDue = (bill.dueDay - day + monthDays) % monthDays
            val alertKey = "bill-reminder-${bill.id}-${today.get(Calendar.YEAR)}-${today.get(Calendar.DAY_OF_YEAR)}"
            if (daysUntilDue in 0..3 && !preferences.getBoolean(alertKey, false)) {
                FinanceNotifications.notify(
                    applicationContext, 20_000 + bill.id.toInt(),
                    if (daysUntilDue == 0) "Bill due today" else "Bill due in $daysUntilDue day(s)",
                    "${bill.title} · ₹${"%.2f".format(Locale.US, bill.amountPaise / 100.0)}"
                )
                preferences.edit().putBoolean(alertKey, true).apply()
            }
        }
        Result.success()
    } catch (_: Exception) { Result.retry() }
}

object BudgetAlertEvaluator {
    suspend fun evaluate(context: Context, transaction: FinanceTransaction) {
        if (transaction.type != "EXPENSE") return
        val dao = FinanceDatabase.get(context).financeDao()
        val calendar = Calendar.getInstance().apply { timeInMillis = transaction.dateMillis }
        val monthKey = SimpleDateFormat("yyyy-MM", Locale.US).format(calendar.time)
        val budgets = dao.allBudgets().filter { it.category == transaction.category && it.monthKey == monthKey }
        if (budgets.isEmpty()) return
        val startCalendar = (calendar.clone() as Calendar).apply {
            set(Calendar.DAY_OF_MONTH, 1); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val end = (startCalendar.clone() as Calendar).apply { add(Calendar.MONTH, 1) }.timeInMillis
        val spent = dao.transactionsBetween(startCalendar.timeInMillis, end).filter {
            it.type == "EXPENSE" && it.category == transaction.category
        }.sumOf { it.amountPaise }
        budgets.forEach { budget ->
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
