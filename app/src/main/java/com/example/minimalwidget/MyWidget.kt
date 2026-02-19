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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
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
        val markets = fetchMarketQuotes()

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
                    MarketModeContent(settings.fontSize, markets)
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
        text = "Today: $normalizedTodo",
        style = TextStyle(color = textColor, fontSize = bodySize(fontSize), fontWeight = FontWeight.Medium)
    )
}

data class MarketLine(
    val label: String,
    val value: String,
    val changePercent: String
)

@androidx.compose.runtime.Composable
private fun MarketModeContent(fontSize: String, lines: List<MarketLine>) {
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

private suspend fun fetchMarketQuotes(): List<MarketLine> = withContext(Dispatchers.IO) {
    return@withContext try {
        fun fetchYahooChart(symbolEncoded: String, label: String): MarketLine {
            val url = URL("https://query2.finance.yahoo.com/v8/finance/chart/$symbolEncoded?interval=1d&range=5d")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 7000
                readTimeout = 7000
                setRequestProperty("User-Agent", "Mozilla/5.0")
            }
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()

            val meta = JSONObject(body)
                .getJSONObject("chart")
                .getJSONArray("result")
                .getJSONObject(0)
                .getJSONObject("meta")

            val price = meta.optDouble("regularMarketPrice", Double.NaN)
            val prev = meta.optDouble("chartPreviousClose", Double.NaN)
            val pct = if (price.isNaN() || prev.isNaN() || prev == 0.0) Double.NaN else ((price - prev) / prev) * 100.0

            val priceStr = if (price.isNaN()) "-" else String.format("%,.2f", price)
            val pctStr = if (pct.isNaN()) "0.00%" else String.format("%+.2f%%", pct)
            return MarketLine(label, priceStr, pctStr)
        }

        listOf(
            fetchYahooChart("%5EKS11", "KOSPI"),
            fetchYahooChart("%5EIXIC", "NASDAQ"),
            fetchYahooChart("KRW=X", "USDKRW")
        )
    } catch (_: Exception) {
        listOf(
            MarketLine("KOSPI", "-", "0.00%"),
            MarketLine("NASDAQ", "-", "0.00%"),
            MarketLine("USDKRW", "-", "0.00%")
        )
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
