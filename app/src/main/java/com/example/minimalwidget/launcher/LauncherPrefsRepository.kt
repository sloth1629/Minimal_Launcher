package com.example.minimalwidget.launcher

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.launcherPrefsDataStore by preferencesDataStore(name = "launcher_prefs")

data class LauncherPrefs(
    val hiddenPackages: Set<String> = emptySet(),
    val aliases: Map<String, String> = emptyMap()
)

private object LauncherPrefKeys {
    val HiddenPackages = stringSetPreferencesKey("hidden_packages")
    val AliasesBlob = stringPreferencesKey("aliases_blob")
}

class LauncherPrefsRepository(private val context: Context) {
    val prefsFlow: Flow<LauncherPrefs> = context.launcherPrefsDataStore.data.map { prefs ->
        prefs.toLauncherPrefs()
    }

    suspend fun addHidden(packageName: String) {
        context.launcherPrefsDataStore.edit { prefs ->
            val current = prefs[LauncherPrefKeys.HiddenPackages].orEmpty().toMutableSet()
            current.add(packageName)
            prefs[LauncherPrefKeys.HiddenPackages] = current
        }
    }

    suspend fun removeHidden(packageName: String) {
        context.launcherPrefsDataStore.edit { prefs ->
            val current = prefs[LauncherPrefKeys.HiddenPackages].orEmpty().toMutableSet()
            current.remove(packageName)
            prefs[LauncherPrefKeys.HiddenPackages] = current
        }
    }

    suspend fun setAlias(packageName: String, alias: String) {
        context.launcherPrefsDataStore.edit { prefs ->
            val current = parseAliases(prefs[LauncherPrefKeys.AliasesBlob].orEmpty()).toMutableMap()
            if (alias.isBlank()) current.remove(packageName)
            else current[packageName] = alias.trim()
            prefs[LauncherPrefKeys.AliasesBlob] = serializeAliases(current)
        }
    }
}

private fun Preferences.toLauncherPrefs(): LauncherPrefs {
    return LauncherPrefs(
        hiddenPackages = this[LauncherPrefKeys.HiddenPackages].orEmpty(),
        aliases = parseAliases(this[LauncherPrefKeys.AliasesBlob].orEmpty())
    )
}

private fun parseAliases(blob: String): Map<String, String> {
    if (blob.isBlank()) return emptyMap()
    return blob
        .split('\n')
        .mapNotNull { line ->
            val tabIndex = line.indexOf('\t')
            if (tabIndex <= 0) null
            else {
                val pkg = line.substring(0, tabIndex)
                val alias = line.substring(tabIndex + 1)
                if (pkg.isBlank() || alias.isBlank()) null else pkg to alias
            }
        }
        .toMap()
}

private fun serializeAliases(map: Map<String, String>): String {
    return map.entries.joinToString("\n") { "${it.key}\t${it.value.replace("\n", " ")}" }
}
