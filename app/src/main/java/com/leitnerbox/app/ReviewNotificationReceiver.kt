
package com.leitnerbox.app

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

class ReviewNotificationReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val wordId = intent.getIntExtra(EXTRA_WORD_ID, -1)
        if (wordId < 0) return

        val words = WordDatabase(context).use { database ->
            database.getAllWords()
        }

        val now = System.currentTimeMillis()
        val triggeredWord = words.firstOrNull { it.id == wordId }

        // Reschedule a card if its review time has changed.
        if (triggeredWord != null && triggeredWord.nextReviewTime > now) {
            ReviewAlarmScheduler.scheduleWord(context, triggeredWord)
        }

        // Android 13 and newer require notification permission.
        if (!hasNotificationPermission(context)) {
            return
        }

        createNotificationChannel(context)

        val dueWords = words
            .filter {
                it.id > 0 &&
                        it.nextReviewTime > 0L &&
                        it.nextReviewTime <= now
            }
            .sortedBy { it.nextReviewTime }

        updateReviewNotifications(context, dueWords)
    }

    private fun updateReviewNotifications(
        context: Context,
        dueWords: List<Word>
    ) {
        // Check permission in the same function that accesses notifications.
        if (!hasNotificationPermission(context)) {
            return
        }

        val manager = NotificationManagerCompat.from(context)
        val dueIds = dueWords.map { it.id }.toSet()

        // Remove notifications for deleted or no-longer-due cards.
        // Keep notifications that are still valid to avoid unnecessary alerts.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.activeNotifications
                .filter { it.notification.channelId == CHANNEL_ID }
                .forEach { active ->
                    val isSummary = active.id == SUMMARY_NOTIFICATION_ID
                    val isDueCard = active.id in dueIds

                    if (!isSummary && !isDueCard) {
                        manager.cancel(active.tag, active.id)
                    }
                }
        }

        if (dueWords.size < 2) {
            manager.cancel(SUMMARY_NOTIFICATION_ID)
        }

        // A single due card is shown as a regular notification.
        // Two or more due cards are grouped together.
        val shouldGroup = dueWords.size >= 2

        dueWords.forEach { word ->
            val openAppIntent = Intent(context, MainActivity::class.java)

            val contentIntent = PendingIntent.getActivity(
                context,
                word.id,
                openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or
                        PendingIntent.FLAG_IMMUTABLE
            )

            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification_review)
                .setColor(android.graphics.Color.BLACK)
                .setColorized(true)
                .setContentTitle("Quick Review: ")
                .setContentText(word.collocation)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setContentIntent(contentIntent)
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)

            if (shouldGroup) {
                builder.setGroup(GROUP_KEY)
            }

            // Protect against permission changes between the check and this call.
            try {
                manager.notify(word.id, builder.build())
            } catch (_: SecurityException) {
                return
            }
        }

        if (shouldGroup) {
            val openAppIntent = Intent(context, MainActivity::class.java)

            val summaryIntent = PendingIntent.getActivity(
                context,
                SUMMARY_PENDING_INTENT_ID,
                openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or
                        PendingIntent.FLAG_IMMUTABLE
            )

            val summaryText = if (dueWords.size >= 4) {
                "${dueWords.size} reviews ready"
            } else {
                "Your cards are ready to review."
            }

            val summaryNotification =
                NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_notification_review)
                    .setColor(android.graphics.Color.BLACK)
                    .setColorized(true)
                    .setContentTitle("Quick Review: ")
                    .setContentText(summaryText)
                    .setStyle(
                        NotificationCompat.InboxStyle()
                            .setSummaryText(summaryText)
                    )
                    .setGroup(GROUP_KEY)
                    .setGroupSummary(true)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setCategory(NotificationCompat.CATEGORY_REMINDER)
                    .setContentIntent(summaryIntent)
                    .setOnlyAlertOnce(true)
                    .setAutoCancel(true)
                    .build()

            try {
                manager.notify(
                    SUMMARY_NOTIFICATION_ID,
                    summaryNotification
                )
            } catch (_: SecurityException) {
                return
            }
        }
    }

    private fun hasNotificationPermission(context: Context): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                context.checkSelfPermission(
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
    }

    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Review Reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Smart reminders to review your flashcards."
            }

            val manager = context.getSystemService(
                Context.NOTIFICATION_SERVICE
            ) as NotificationManager

            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        internal const val EXTRA_WORD_ID = "word_id"
        private const val CHANNEL_ID = "leitnerbox_review_reminders_v2"

        private const val GROUP_KEY = "leitnerbox_reviews"
        private const val SUMMARY_NOTIFICATION_ID = 0
        private const val SUMMARY_PENDING_INTENT_ID = 1000000
    }
}
