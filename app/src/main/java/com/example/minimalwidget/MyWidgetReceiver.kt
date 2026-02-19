package com.example.minimalwidget

import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import com.example.minimalwidget.widget.WidgetRefreshScheduler

class MyWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MyWidget()

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        WidgetRefreshScheduler.ensureScheduled(context)
    }
}
