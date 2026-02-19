package com.example.minimalwidget.settings

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "widget_settings")

data class WidgetSettings(
    val textTone: String = "light",
    val fontSize: String = "medium",
    val region: String = "Daejeon",
    val interests: String = "AI, IT",
    val dailyTodo: String = "10 min walk",
    val homeTopPadding: String = "mid",
    val swipeLeftAction: String = "dial",
    val swipeRightAction: String = "messaging"
)

object WidgetSettingKeys {
    val TextTone = stringPreferencesKey("text_tone")
    val FontSize = stringPreferencesKey("font_size")
    val Region = stringPreferencesKey("region")
    val Interests = stringPreferencesKey("interests")
    val DailyTodo = stringPreferencesKey("daily_todo")
    val HomeTopPadding = stringPreferencesKey("home_top_padding")
    val SwipeLeftAction = stringPreferencesKey("swipe_left_action")
    val SwipeRightAction = stringPreferencesKey("swipe_right_action")
}

class WidgetSettingsRepository(private val context: Context) {
    val settingsFlow: Flow<WidgetSettings> = context.settingsDataStore.data.map { prefs ->
        prefs.toWidgetSettings()
    }

    suspend fun updateTextTone(value: String) {
        context.settingsDataStore.edit { it[WidgetSettingKeys.TextTone] = value }
    }

    suspend fun updateFontSize(value: String) {
        context.settingsDataStore.edit { it[WidgetSettingKeys.FontSize] = value }
    }

    suspend fun updateRegion(value: String) {
        context.settingsDataStore.edit { it[WidgetSettingKeys.Region] = value }
    }

    suspend fun updateInterests(value: String) {
        context.settingsDataStore.edit { it[WidgetSettingKeys.Interests] = value }
    }

    suspend fun updateDailyTodo(value: String) {
        context.settingsDataStore.edit { it[WidgetSettingKeys.DailyTodo] = value }
    }

    suspend fun updateHomeTopPadding(value: String) {
        context.settingsDataStore.edit { it[WidgetSettingKeys.HomeTopPadding] = value }
    }

    suspend fun updateSwipeLeftAction(value: String) {
        context.settingsDataStore.edit { it[WidgetSettingKeys.SwipeLeftAction] = value }
    }

    suspend fun updateSwipeRightAction(value: String) {
        context.settingsDataStore.edit { it[WidgetSettingKeys.SwipeRightAction] = value }
    }
}

fun Preferences.toWidgetSettings(): WidgetSettings {
    return WidgetSettings(
        textTone = this[WidgetSettingKeys.TextTone] ?: "light",
        fontSize = this[WidgetSettingKeys.FontSize] ?: "medium",
        region = this[WidgetSettingKeys.Region] ?: "Daejeon",
        interests = this[WidgetSettingKeys.Interests] ?: "AI, IT",
        dailyTodo = this[WidgetSettingKeys.DailyTodo] ?: "10 min walk",
        homeTopPadding = this[WidgetSettingKeys.HomeTopPadding] ?: "mid",
        swipeLeftAction = this[WidgetSettingKeys.SwipeLeftAction] ?: "dial",
        swipeRightAction = this[WidgetSettingKeys.SwipeRightAction] ?: "messaging"
    )
}
