
package com.leitnerbox.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (
            intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            val pendingResult = goAsync()

            Thread {
                try {
                    val database = WordDatabase(context)
                    try {
                        ReviewAlarmScheduler.scheduleAll(context, database)
                    } finally {
                        database.close()
                    }
                } finally {
                    pendingResult.finish()
                }
            }.start()
        }
    }
}
