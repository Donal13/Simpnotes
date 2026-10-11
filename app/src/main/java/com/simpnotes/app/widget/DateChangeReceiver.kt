package com.simpnotes.app.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.GlanceAppWidgetManager
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Keeps the date in the widget header current. Runs just after midnight, and when the clock,
 * time zone or language changes, then refreshes the widgets and books the next midnight.
 */
class DateChangeReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in HANDLED_ACTIONS) return
        val appContext = context.applicationContext
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                // With no widgets left there's nothing to refresh, so the chain of alarms stops here.
                val glanceIds = GlanceAppWidgetManager(appContext).getGlanceIds(NotesWidget::class.java)
                if (glanceIds.isNotEmpty()) {
                    NotesWidget.refreshDate(appContext, glanceIds)
                    scheduleNextDayRefresh(appContext)
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private const val ACTION_NEW_DAY = "com.simpnotes.app.action.NEW_DAY"
        private const val TEN_MINUTES = 10 * 60 * 1000L

        private val HANDLED_ACTIONS = setOf(
            ACTION_NEW_DAY,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_LOCALE_CHANGED
        )

        /**
         * Books a refresh for within 10 minutes after the coming midnight, replacing any earlier
         * booking. The alarm doesn't wake the phone, so it costs no battery: if the phone is
         * asleep at midnight, the widget updates as soon as the screen comes on.
         */
        fun scheduleNextDayRefresh(context: Context) {
            val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
            val intent = Intent(context, DateChangeReceiver::class.java).setAction(ACTION_NEW_DAY)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                0,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            val nextMidnight = LocalDate.now().plusDays(1)
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
            // A plain inexact alarm may arrive up to an hour late; 10 minutes is the tightest
            // window allowed without the exact-alarm permission.
            alarmManager.setWindow(AlarmManager.RTC, nextMidnight, TEN_MINUTES, pendingIntent)
        }
    }
}
