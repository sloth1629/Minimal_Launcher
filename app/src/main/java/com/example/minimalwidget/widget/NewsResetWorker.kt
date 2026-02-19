package com.example.minimalwidget.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.appwidget.updateAll
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.minimalwidget.MyWidget
import com.example.minimalwidget.WidgetKeys

class NewsResetWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val manager = GlanceAppWidgetManager(applicationContext)
        val glanceIds = manager.getGlanceIds(MyWidget::class.java)

        glanceIds.forEach { id ->
            updateAppWidgetState(applicationContext, id) { prefs ->
                prefs[WidgetKeys.IsNewsMode] = false
            }
        }

        MyWidget().updateAll(applicationContext)
        return Result.success()
    }
}
