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
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.state.PreferencesGlanceStateDefinition
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
    override val stateDefinition = PreferencesGlanceStateDefinition

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
        val newsItems = Repositories.news.getNewsSummaries(limit = 3, interests = settings.interests)

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
                    NewsModeContent(newsItems, settings.fontSize)
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

@androidx.compose.runtime.Composable
private fun NewsModeContent(
    newsItems: List<String>,
    fontSize: String
) {
    val lines = newsItems.take(3)
    val textColor = ColorProvider(day = Color(0xFFFFFFFF), night = Color(0xFFFFFFFF))
    val line1 = lines.getOrElse(0) { "뉴스가 없어요" }
    val line2 = lines.getOrElse(1) { "" }
    val line3 = lines.getOrElse(2) { "" }

    Text(
        text = "오늘의 뉴스",
        style = TextStyle(
            fontWeight = FontWeight.Bold,
            color = textColor,
            fontSize = bodySize(fontSize)
        )
    )
    Text(text = "• $line1", style = TextStyle(color = textColor, fontSize = bodySize(fontSize), fontWeight = FontWeight.Medium))
    if (line2.isNotBlank()) Text(text = "• $line2", style = TextStyle(color = textColor, fontSize = bodySize(fontSize), fontWeight = FontWeight.Medium))
    if (line3.isNotBlank()) Text(text = "• $line3", style = TextStyle(color = textColor, fontSize = bodySize(fontSize), fontWeight = FontWeight.Medium))
}
