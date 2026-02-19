package com.example.minimalwidget

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.appwidget.updateAll
import androidx.glance.color.ColorProvider
import androidx.glance.currentState
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.minimalwidget.data.model.WeatherInfo
import com.example.minimalwidget.data.repository.Repositories
import com.example.minimalwidget.settings.WidgetSettingsRepository
import com.example.minimalwidget.widget.NewsResetWorker
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

class MyWidget : GlanceAppWidget() {
    override val stateDefinition = androidx.glance.state.PreferencesGlanceStateDefinition

    override val sizeMode = SizeMode.Responsive(
        setOf(
            DpSize(180.dp, 110.dp),
            DpSize(250.dp, 140.dp)
        )
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val settings = WidgetSettingsRepository(context).settingsFlow.first()
        val weather = Repositories.weather.getCurrentWeather(settings.region)
        val todo = settings.dailyTodo.ifBlank { Repositories.todo.getDailyTodo() }

        provideContent {
            val prefs = currentState<Preferences>()
            val isNewsMode = prefs[WidgetKeys.IsNewsMode] ?: false

            Column(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .padding(12.dp)
                    .clickable(actionRunCallback<ToggleWidgetModeAction>())
            ) {
                if (isNewsMode) {
                    MarketModeContent(settings.fontSize)
                } else {
                    MinimalModeContent(weather, todo, settings.fontSize)
                }
            }
        }
    }
}

class ToggleWidgetModeAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        var switchedToNews = false

        updateAppWidgetState(context, glanceId) { prefs ->
            val current = prefs[WidgetKeys.IsNewsMode] ?: false
            val next = !current
            prefs[WidgetKeys.IsNewsMode] = next
            switchedToNews = next
        }

        if (switchedToNews) {
            val request = OneTimeWorkRequestBuilder<NewsResetWorker>()
                .setInitialDelay(10, TimeUnit.SECONDS)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                "news_mode_auto_reset",
                ExistingWorkPolicy.REPLACE,
                request
            )
        }

        MyWidget().updateAll(context)
    }
}

object WidgetKeys {
    val IsNewsMode = booleanPreferencesKey("is_news_mode")
}

private fun titleSize(size: String) = when (size) {
    "small" -> 20.sp
    "large" -> 28.sp
    else -> 24.sp
}

private fun bodySize(size: String) = when (size) {
    "small" -> 12.sp
    "large" -> 16.sp
    else -> 14.sp
}

@androidx.compose.runtime.Composable
private fun MinimalModeContent(
    weather: WeatherInfo,
    todo: String,
    fontSize: String
) {
    val normalizedTodo = todo.removePrefix("To-do:").trim()
    val textColor = ColorProvider(day = Color(0xFFFFFFFF), night = Color(0xFFFFFFFF))

    Text(
        text = "${weather.temperatureCelsius}°C",
        style = TextStyle(
            fontWeight = FontWeight.Bold,
            color = textColor,
            fontSize = titleSize(fontSize)
        )
    )
    Text(
        text = weather.airQualitySummary,
        style = TextStyle(color = textColor, fontSize = bodySize(fontSize), fontWeight = FontWeight.Medium)
    )
    Text(
        text = "오늘 할 일: $normalizedTodo",
        style = TextStyle(color = textColor, fontSize = bodySize(fontSize), fontWeight = FontWeight.Medium)
    )
}

data class MarketLine(
    val label: String,
    val value: String,
    val changePercent: String
)

private fun marketLines(): List<MarketLine> = listOf(
    MarketLine("KOSPI", "2,845.10", "+0.73%"),
    MarketLine("NASDAQ", "18,920.44", "-0.41%"),
    MarketLine("USDKRW", "1,372.50", "+0.12%")
)

@androidx.compose.runtime.Composable
private fun MarketModeContent(fontSize: String) {
    val lines = marketLines()
    val base = ColorProvider(day = Color(0xFFFFFFFF), night = Color(0xFFFFFFFF))

    lines.forEach { line ->
        Row {
            Text(
                text = "${line.label} ${line.value} ",
                style = TextStyle(
                    color = base,
                    fontSize = bodySize(fontSize),
                    fontWeight = FontWeight.Medium
                )
            )
            Text(
                text = line.changePercent,
                style = TextStyle(
                    color = ColorProvider(
                        day = changeColor(line.changePercent),
                        night = changeColor(line.changePercent)
                    ),
                    fontSize = bodySize(fontSize),
                    fontWeight = FontWeight.Medium
                )
            )
        }
    }
}

private fun changeColor(change: String): Color {
    val t = change.trim()
    return when {
        t.startsWith("+") -> Color(0xFFFF3B30) // KR: plus red
        t.startsWith("-") -> Color(0xFF2F6BFF) // KR: minus blue
        else -> Color(0xFFFFFFFF)
    }
}
