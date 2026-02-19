package com.example.minimalwidget

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.minimalwidget.data.model.WeatherInfo
import com.example.minimalwidget.data.repository.Repositories
import com.example.minimalwidget.launcher.LauncherPrefsRepository
import com.example.minimalwidget.settings.WidgetSettings
import com.example.minimalwidget.settings.WidgetSettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.BLACK
        window.navigationBarColor = android.graphics.Color.BLACK
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        val settingsRepository = WidgetSettingsRepository(this)
        val launcherPrefsRepository = LauncherPrefsRepository(this)

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
                    MinimalLauncherApp(settingsRepository, launcherPrefsRepository)
                }
            }
        }
    }
}

private enum class LauncherScreen { Home, Apps, Settings }

data class MarketQuote(val label: String, val value: String, val changePct: String)

@Composable
private fun MinimalLauncherApp(
    settingsRepository: WidgetSettingsRepository,
    launcherPrefsRepository: LauncherPrefsRepository
) {
    val context = LocalContext.current
    val settings by settingsRepository.settingsFlow.collectAsState(initial = WidgetSettings())
    val prefs by launcherPrefsRepository.prefsFlow.collectAsState(initial = com.example.minimalwidget.launcher.LauncherPrefs())
    val scope = rememberCoroutineScope()

    var currentScreen by remember { mutableStateOf(LauncherScreen.Home) }
    var weather by remember { mutableStateOf(WeatherInfo(temperatureCelsius = 0, airQualitySummary = "Loading weather...")) }
    var weatherLoading by remember { mutableStateOf(true) }
    var newsItems by remember { mutableStateOf<List<String>>(emptyList()) }
    var newsLoading by remember { mutableStateOf(true) }
    var isNewsMode by remember { mutableStateOf(true) }
    var marketQuotes by remember { mutableStateOf<List<MarketQuote>>(emptyList()) }
    var marketLoading by remember { mutableStateOf(true) }

    val apps = remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val pm = context.packageManager
            val launchIntent = Intent(Intent.ACTION_MAIN, null).apply { addCategory(Intent.CATEGORY_LAUNCHER) }
            val loaded = pm.queryIntentActivities(launchIntent, 0)
                .map {
                    val pkg = it.activityInfo.packageName
                    val label = it.loadLabel(pm).toString()
                    pkg to label
                }
                .distinctBy { it.first }
                .sortedBy { it.second.lowercase() }
            apps.value = loaded
        }
    }

    LaunchedEffect(settings.region) {
        weatherLoading = true
        weather = Repositories.weather.getCurrentWeather(settings.region)
        weatherLoading = false
    }

    LaunchedEffect(settings.interests) {
        newsLoading = true
        newsItems = Repositories.news.getNewsSummaries(limit = 3, interests = settings.interests)
        newsLoading = false
    }



    LaunchedEffect(Unit) {
        marketLoading = true
        marketQuotes = fetchMarketQuotes()
        marketLoading = false
    }

    when (currentScreen) {
        LauncherScreen.Home -> HomeScreen(
            settings = settings,
            weather = weather,
            weatherLoading = weatherLoading,
            newsItems = newsItems,
            newsLoading = newsLoading,
            marketQuotes = marketQuotes,
            marketLoading = marketLoading,
            isNewsMode = isNewsMode,
            onToggleNewsMode = { isNewsMode = !isNewsMode },
            onSwipeLeft = { launchSwipeAction(context, settings.swipeLeftAction) },
            onSwipeRight = { launchSwipeAction(context, settings.swipeRightAction) },
            onSwipeUp = { currentScreen = LauncherScreen.Apps },
            onLongPress = { currentScreen = LauncherScreen.Settings }
        )

        LauncherScreen.Apps -> AppsScreen(
            apps = apps.value,
            aliases = prefs.aliases,
            hiddenPackages = prefs.hiddenPackages,
            onBackHome = { currentScreen = LauncherScreen.Home },
            onLaunchApp = { packageName ->
                context.packageManager.getLaunchIntentForPackage(packageName)?.let { context.startActivity(it) }
            }
        )

        LauncherScreen.Settings -> SettingsScreen(
            apps = apps.value,
            aliases = prefs.aliases,
            hiddenPackages = prefs.hiddenPackages,
            settings = settings,
            onBackHome = { currentScreen = LauncherScreen.Home },
            onSaveSettings = { region, interests, todo, tone, fontSize, homeTopPadding, swipeLeftAction, swipeRightAction ->
                scope.launch {
                    settingsRepository.updateRegion(region.ifBlank { "Seoul" })
                    settingsRepository.updateInterests(interests.ifBlank { "AI, IT" })
                    settingsRepository.updateDailyTodo(todo.ifBlank { "10 min walk" })
                    settingsRepository.updateTextTone(tone)
                    settingsRepository.updateFontSize(fontSize)
                    settingsRepository.updateHomeTopPadding(homeTopPadding)
                    settingsRepository.updateSwipeLeftAction(swipeLeftAction.ifBlank { "dial" })
                    settingsRepository.updateSwipeRightAction(swipeRightAction.ifBlank { "messaging" })
                    currentScreen = LauncherScreen.Home
                }
            },
            onToggleHidden = { packageName, hidden ->
                scope.launch {
                    if (hidden) launcherPrefsRepository.addHidden(packageName)
                    else launcherPrefsRepository.removeHidden(packageName)
                }
            },
            onAliasChanged = { packageName, alias ->
                scope.launch { launcherPrefsRepository.setAlias(packageName, alias) }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HomeScreen(
    settings: WidgetSettings,
    weather: WeatherInfo,
    weatherLoading: Boolean,
    newsItems: List<String>,
    newsLoading: Boolean,
    marketQuotes: List<MarketQuote>,
    marketLoading: Boolean,
    isNewsMode: Boolean,
    onToggleNewsMode: () -> Unit,
    onSwipeLeft: () -> Unit,
    onSwipeRight: () -> Unit,
    onSwipeUp: () -> Unit,
    onLongPress: () -> Unit
) {
    var totalDx by remember { mutableFloatStateOf(0f) }
    var totalDy by remember { mutableFloatStateOf(0f) }

    val backgroundColor = if (settings.textTone == "dark") Color.Black else Color(0xFFF6F6F6)
    val textColor = if (settings.textTone == "dark") Color(0xFFF2F2F2) else Color(0xFF111111)
    val tempFontSize = when (settings.fontSize) {
        "small" -> 40.sp
        "large" -> 56.sp
        else -> 48.sp
    }
    val bodyFontSize = when (settings.fontSize) {
        "small" -> 14.sp
        "large" -> 18.sp
        else -> 16.sp
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDrag = { _, dragAmount ->
                        totalDx += dragAmount.x
                        totalDy += dragAmount.y
                    },
                    onDragEnd = {
                        if (kotlin.math.abs(totalDx) > kotlin.math.abs(totalDy)) {
                            when {
                                totalDx > 120f -> onSwipeRight()
                                totalDx < -120f -> onSwipeLeft()
                            }
                        } else if (totalDy < -120f) {
                            onSwipeUp()
                        }
                        totalDx = 0f
                        totalDy = 0f
                    }
                )
            }
            .combinedClickable(onClick = onToggleNewsMode, onLongClick = onLongPress)
            .padding(horizontal = 20.dp)
    ) {
        val topPadding = when (settings.homeTopPadding) {
            "high" -> maxHeight / 9
            "mid" -> maxHeight * 0.18f
            "low" -> maxHeight / 4
            else -> maxHeight * 0.18f
        }

        Column(
            modifier = Modifier.align(Alignment.TopStart).padding(top = topPadding),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            if (isNewsMode) {
                val lines = if (marketLoading || marketQuotes.isEmpty()) {
                    listOf(
                        MarketQuote("KOSPI", "-", "0.00%"),
                        MarketQuote("NASDAQ", "-", "0.00%"),
                        MarketQuote("USDKRW", "-", "0.00%")
                    )
                } else marketQuotes

                lines.forEach { line ->
                    Row {
                        Text("${line.label} ${line.value} ", color = textColor, fontSize = bodyFontSize)
                        Text(
                            line.changePct,
                            color = when {
                                line.changePct.startsWith("+") -> Color(0xFFFF3B30)
                                line.changePct.startsWith("-") -> Color(0xFF2F6BFF)
                                else -> textColor
                            },
                            fontSize = bodyFontSize
                        )
                    }
                }
            } else {
                Text(
                    text = if (weatherLoading) "--°C" else "${weather.temperatureCelsius}°C",
                    color = textColor,
                    fontSize = tempFontSize,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (weatherLoading) "Loading weather..." else weather.airQualitySummary,
                    color = textColor,
                    fontSize = bodyFontSize
                )
                Text("Today: ${settings.dailyTodo}", color = textColor, fontSize = bodyFontSize)
            }
        }
    }
}

private suspend fun fetchMarketQuotes(): List<MarketQuote> = withContext(Dispatchers.IO) {
    return@withContext try {
        fun fetchYahooChart(symbolEncoded: String, label: String): MarketQuote {
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
            return MarketQuote(label, priceStr, pctStr)
        }

        listOf(
            fetchYahooChart("%5EKS11", "KOSPI"),
            fetchYahooChart("%5EIXIC", "NASDAQ"),
            fetchYahooChart("KRW=X", "USDKRW")
        )
    } catch (_: Exception) {
        listOf(
            MarketQuote("KOSPI", "-", "0.00%"),
            MarketQuote("NASDAQ", "-", "0.00%"),
            MarketQuote("USDKRW", "-", "0.00%")
        )
    }
}
private fun launchSwipeAction(context: android.content.Context, action: String) {
    val normalized = action.trim().lowercase()
    when (normalized) {
        "", "none", "off", "disabled" -> Unit
        "dial", "phone", "call" -> runCatching { context.startActivity(Intent(Intent.ACTION_DIAL)) }
        "messaging", "sms", "message" -> {
            val smsIntent = Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_APP_MESSAGING) }
            runCatching { context.startActivity(smsIntent) }
                .onFailure { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, "sms:".toUri())) } }
        }
        else -> {
            context.packageManager.getLaunchIntentForPackage(action.trim())?.let {
                runCatching { context.startActivity(it) }
            }
        }
    }
}

@Composable
private fun AppsScreen(
    apps: List<Pair<String, String>>,
    aliases: Map<String, String>,
    hiddenPackages: Set<String>,
    onBackHome: () -> Unit,
    onLaunchApp: (String) -> Unit
) {
    val visibleApps = apps.filterNot { hiddenPackages.contains(it.first) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitPointerEvent(pass = PointerEventPass.Initial).changes.firstOrNull()?.position
                    var last = down
                    while (true) {
                        val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                        val change = event.changes.firstOrNull()
                        if (change != null) last = change.position
                        if (event.changes.none { it.pressed }) break
                    }
                    if (down != null && last != null) {
                        val dx = last.x - down.x
                        val dy = last.y - down.y
                        if (kotlin.math.abs(dy) > kotlin.math.abs(dx) && dy > 120f) onBackHome()
                    }
                }
            }
            .padding(20.dp)
    ) {
        Text("Apps", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(visibleApps) { (pkg, originalName) ->
                val label = aliases[pkg].takeUnless { it.isNullOrBlank() } ?: originalName
                Text(
                    text = label,
                    color = Color.White,
                    fontSize = 18.sp,
                    modifier = Modifier.fillMaxWidth().clickable { onLaunchApp(pkg) }.padding(vertical = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun MonoButton(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        border = BorderStroke(1.dp, Color.White),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.Transparent, contentColor = Color.White)
    ) {
        Text(label)
    }
}

@Composable
private fun SettingsScreen(
    apps: List<Pair<String, String>>,
    aliases: Map<String, String>,
    hiddenPackages: Set<String>,
    settings: WidgetSettings,
    onBackHome: () -> Unit,
    onSaveSettings: (region: String, interests: String, todo: String, tone: String, fontSize: String, homeTopPadding: String, swipeLeftAction: String, swipeRightAction: String) -> Unit,
    onToggleHidden: (packageName: String, hidden: Boolean) -> Unit,
    onAliasChanged: (packageName: String, alias: String) -> Unit
) {
    var region by remember { mutableStateOf(settings.region) }
    var interests by remember { mutableStateOf(settings.interests) }
    var todo by remember { mutableStateOf(settings.dailyTodo) }
    var tone by remember { mutableStateOf(settings.textTone) }
    var fontSize by remember { mutableStateOf(settings.fontSize) }
    var homeTopPadding by remember { mutableStateOf(settings.homeTopPadding) }
    var swipeLeftAction by remember { mutableStateOf(settings.swipeLeftAction) }
    var swipeRightAction by remember { mutableStateOf(settings.swipeRightAction) }
    var settingsTab by remember { mutableStateOf("apps") }

    val aliasDrafts = remember { mutableStateMapOf<String, String>() }
    val settingsTextFieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = Color.White,
        unfocusedTextColor = Color.White,
        focusedLabelColor = Color.White,
        unfocusedLabelColor = Color.White.copy(alpha = 0.8f),
        focusedBorderColor = Color.White,
        unfocusedBorderColor = Color.White.copy(alpha = 0.5f),
        cursorColor = Color.White
    )
    val whiteSelectionColors = TextSelectionColors(
        handleColor = Color.White,
        backgroundColor = Color.White.copy(alpha = 0.35f)
    )

    Column(
        modifier = Modifier.fillMaxSize().background(Color(0xFF141414)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Launcher Settings", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            MonoButton(label = "Close", onClick = onBackHome)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            MonoButton(label = "App name / hidden", modifier = Modifier, onClick = { settingsTab = "apps" })
            MonoButton(label = "Advanced", modifier = Modifier, onClick = { settingsTab = "advanced" })
        }

        if (settingsTab == "apps") {
            Text("App name / hidden", color = Color.White, fontWeight = FontWeight.SemiBold)
            LazyColumn(modifier = Modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(apps) { (pkg, originalName) ->
                    val currentAlias = aliasDrafts[pkg] ?: aliases[pkg].orEmpty()
                    Column(modifier = Modifier.fillMaxWidth().background(Color.White.copy(alpha = 0.03f)).padding(10.dp)) {
                        Text(originalName, color = Color.White, fontWeight = FontWeight.Medium)
                        Text(pkg, color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp)

                        androidx.compose.runtime.CompositionLocalProvider(LocalTextSelectionColors provides whiteSelectionColors) {
                            OutlinedTextField(
                                value = currentAlias,
                                onValueChange = { aliasDrafts[pkg] = it },
                                label = { Text("Launcher name") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                colors = settingsTextFieldColors
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MonoButton(label = "Save name") { onAliasChanged(pkg, aliasDrafts[pkg] ?: "") }
                            if (hiddenPackages.contains(pkg)) {
                                MonoButton(label = "Unhide") { onToggleHidden(pkg, false) }
                            } else {
                                MonoButton(label = "Hide") { onToggleHidden(pkg, true) }
                            }
                        }
                    }
                }
            }
        } else {
            androidx.compose.runtime.CompositionLocalProvider(LocalTextSelectionColors provides whiteSelectionColors) {
                OutlinedTextField(value = region, onValueChange = { region = it }, label = { Text("Region") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text), modifier = Modifier.fillMaxWidth(), colors = settingsTextFieldColors)
                OutlinedTextField(value = interests, onValueChange = { interests = it }, label = { Text("Interests") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text), modifier = Modifier.fillMaxWidth(), colors = settingsTextFieldColors)
                OutlinedTextField(value = todo, onValueChange = { todo = it }, label = { Text("Today todo") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text), modifier = Modifier.fillMaxWidth(), colors = settingsTextFieldColors)
                OutlinedTextField(value = swipeLeftAction, onValueChange = { swipeLeftAction = it }, label = { Text("Left swipe action (dial/messaging/none/package)") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = settingsTextFieldColors)
                OutlinedTextField(value = swipeRightAction, onValueChange = { swipeRightAction = it }, label = { Text("Right swipe action (dial/messaging/none/package)") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = settingsTextFieldColors)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MonoButton(label = "Light") { tone = "light" }
                MonoButton(label = "Dark") { tone = "dark" }
                Text("Now: $tone", color = Color.White, modifier = Modifier.align(Alignment.CenterVertically))
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MonoButton(label = "Small") { fontSize = "small" }
                MonoButton(label = "Medium") { fontSize = "medium" }
                MonoButton(label = "Large") { fontSize = "large" }
                Text("Now: $fontSize", color = Color.White, modifier = Modifier.align(Alignment.CenterVertically))
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MonoButton(label = "Top") { homeTopPadding = "high" }
                MonoButton(label = "Mid") { homeTopPadding = "mid" }
                MonoButton(label = "Low") { homeTopPadding = "low" }
                Text("Pos: $homeTopPadding", color = Color.White, modifier = Modifier.align(Alignment.CenterVertically))
            }

            MonoButton(
                label = "Save",
                modifier = Modifier.fillMaxWidth(),
                onClick = { onSaveSettings(region, interests, todo, tone, fontSize, homeTopPadding, swipeLeftAction, swipeRightAction) }
            )
        }
    }
}















