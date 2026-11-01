
package com.leitnerbox.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

object ReviewAlarmScheduler {

    private const val EXTRA_WORD_ID = "word_id"

    fun scheduleAll(context: Context, database: WordDatabase) {
        val words = database.getAllWords()
        val now = System.currentTimeMillis()

        words.forEach { word ->
            scheduleWord(context, word)
        }

        val dueWord = words.firstOrNull {
            it.id > 0 && it.nextReviewTime <= now
        }

        if (dueWord != null) {
            val intent = Intent(
                context,
                ReviewNotificationReceiver::class.java
            ).apply {
                putExtra(
                    ReviewNotificationReceiver.EXTRA_WORD_ID,
                    dueWord.id
                )
            }

            context.sendBroadcast(intent)
        }
    }

    fun scheduleWord(context: Context, word: Word) {
        val alarmManager =
            context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val pendingIntent = createPendingIntent(context, word.id)

        if (word.id <= 0 || word.nextReviewTime <= System.currentTimeMillis()) {
            alarmManager.cancel(pendingIntent)
            return
        }

        try {
            if (
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                !alarmManager.canScheduleExactAlarms()
            ) {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    word.nextReviewTime,
                    pendingIntent
                )
            } else {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    word.nextReviewTime,
                    pendingIntent
                )
            }
        } catch (_: SecurityException) {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                word.nextReviewTime,
                pendingIntent
            )
        }
    }

    fun cancelWord(context: Context, wordId: Int) {
        if (wordId <= 0) return

        val alarmManager =
            context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        alarmManager.cancel(createPendingIntent(context, wordId))
    }

    private fun createPendingIntent(
        context: Context,
        wordId: Int
    ): PendingIntent {
        val intent = Intent(
            context,
            ReviewNotificationReceiver::class.java
        ).apply {
            putExtra(EXTRA_WORD_ID, wordId)
        }

        return PendingIntent.getBroadcast(
            context,
            wordId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
