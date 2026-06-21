package com.s3id3l.voicecapture.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.s3id3l.voicecapture.R
import com.s3id3l.voicecapture.RecordingActivity
import com.s3id3l.voicecapture.data.PrefsManager

class VoiceWidget : AppWidgetProvider() {

    companion object {
        const val ACTION_CYCLE_FORMAT = "com.s3id3l.voicecapture.CYCLE_FORMAT"
        const val ACTION_CYCLE_TARGET = "com.s3id3l.voicecapture.CYCLE_TARGET"

        fun updateAllWidgets(context: Context) {
            val mgr   = AppWidgetManager.getInstance(context)
            val ids   = mgr.getAppWidgetIds(ComponentName(context, VoiceWidget::class.java))
            val prefs = PrefsManager(context)
            ids.forEach { id -> mgr.updateAppWidget(id, buildViews(context, prefs)) }
        }

        private fun buildViews(context: Context, prefs: PrefsManager): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_voice)

            // Current selection labels
            views.setTextViewText(R.id.tv_format, PrefsManager.formatLabel(prefs.preferredFormat))
            views.setTextViewText(R.id.tv_target, PrefsManager.targetLabel(prefs.preferredTarget))

            // Cycle format on tap
            val fmtIntent = Intent(context, VoiceWidget::class.java).apply { action = ACTION_CYCLE_FORMAT }
            val fmtPi = PendingIntent.getBroadcast(context, 1, fmtIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            views.setOnClickPendingIntent(R.id.tv_format, fmtPi)

            // Cycle target on tap
            val tgtIntent = Intent(context, VoiceWidget::class.java).apply { action = ACTION_CYCLE_TARGET }
            val tgtPi = PendingIntent.getBroadcast(context, 2, tgtIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            views.setOnClickPendingIntent(R.id.tv_target, tgtPi)

            // Open RecordingActivity on mic button
            val recIntent = Intent(context, RecordingActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            val recPi = PendingIntent.getActivity(context, 0, recIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            views.setOnClickPendingIntent(R.id.btn_record, recPi)

            return views
        }
    }

    override fun onUpdate(context: Context, mgr: AppWidgetManager, ids: IntArray) {
        val prefs = PrefsManager(context)
        ids.forEach { id -> mgr.updateAppWidget(id, buildViews(context, prefs)) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val prefs = PrefsManager(context)

        when (intent.action) {
            ACTION_CYCLE_FORMAT -> {
                val current = prefs.preferredFormat
                val idx = PrefsManager.FORMATS.indexOf(current)
                prefs.preferredFormat = PrefsManager.FORMATS[(idx + 1) % PrefsManager.FORMATS.size]
                updateAllWidgets(context)
            }
            ACTION_CYCLE_TARGET -> {
                val current = prefs.preferredTarget
                val idx = PrefsManager.TARGETS.indexOf(current)
                prefs.preferredTarget = PrefsManager.TARGETS[(idx + 1) % PrefsManager.TARGETS.size]
                updateAllWidgets(context)
            }
        }
    }
}
