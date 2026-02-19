package com.example.minimalwidget

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.minimalwidget.data.model.WeatherInfo
import com.example.minimalwidget.data.repository.Repositories
import com.example.minimalwidget.settings.WidgetSettings
import com.example.minimalwidget.settings.WidgetSettingsRepository
import com.example.minimalwidget.launcher.LauncherPrefsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.core.net.toUri

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
                Surface(modifier = Modifier.fillMaxSize()) {
                    MinimalLauncherApp(settingsRepository, launcherPrefsRepository)
                }
            }
        }
    }
}

private enum class LauncherScreen {
    Home,
    Apps,
    Settings
}

@Composable
private fun MinimalLauncherApp(
    settingsRepository: WidgetSettingsRepository,
    launcherPrefsRepository: LauncherPrefsRepository
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val settings by settingsRepository.settingsFlow.collectAsState(initial = WidgetSettings())
    val prefs by launcherPrefsRepository.prefsFlow.collectAsState(initial = com.example.minimalwidget.launcher.LauncherPrefs())

    var currentScreen by remember { mutableStateOf(LauncherScreen.Home) }
    var weather by remember { mutableStateOf(WeatherInfo(temperatureCelsius = 0, airQualitySummary = "날씨 불러오는 중")) }
    var weatherLoading by remember { mutableStateOf(true) }
    var newsItems by remember { mutableStateOf<List<String>>(emptyList()) }
    var newsLoading by remember { mutableStateOf(true) }
    var isNewsMode by remember { mutableStateOf(false) }

    var apps by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }

    LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.Default) {
            context.packageManager.queryIntentActivities(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),
                0
            ).map { info ->
                val pkg = info.activityInfo.packageName
                val appName = info.loadLabel(context.packageManager).toString()
                pkg to appName
            }.distinctBy { it.first }.sortedBy { it.second.lowercase() }
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

    when (currentScreen) {
        LauncherScreen.Home -> HomeScreen(
            settings = settings,
            weather = weather,
            weatherLoading = weatherLoading,
            newsItems = newsItems,
            newsLoading = newsLoading,
            isNewsMode = isNewsMode,
            onToggleNewsMode = { isNewsMode = !isNewsMode },
            onSwipeLeft = {
                val dialIntent = Intent(Intent.ACTION_DIAL)
                context.startActivity(dialIntent)
            },
            onSwipeRight = {
                val smsIntent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_APP_MESSAGING)
                }
                runCatching { context.startActivity(smsIntent) }
                    .onFailure {
                        val fallback = Intent(Intent.ACTION_VIEW, "sms:".toUri())
                        context.startActivity(fallback)
                    }
            },
            onSwipeUp = { currentScreen = LauncherScreen.Apps },
            onLongPress = { currentScreen = LauncherScreen.Settings }
        )

        LauncherScreen.Apps -> AppsScreen(
            apps = apps,
            aliases = prefs.aliases,
            hiddenPackages = prefs.hiddenPackages,
            onBackHome = { currentScreen = LauncherScreen.Home },
            onLaunchApp = { packageName ->
                val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
                if (launchIntent != null) context.startActivity(launchIntent)
            }
        )

        LauncherScreen.Settings -> SettingsScreen(
            apps = apps,
            aliases = prefs.aliases,
            hiddenPackages = prefs.hiddenPackages,
            settings = settings,
            onBackHome = { currentScreen = LauncherScreen.Home },
            onSaveSettings = { region, interests, todo, tone, fontSize, homeTopPadding ->
                scope.launch {
                    settingsRepository.updateRegion(region.ifBlank { "Seoul" })
                    settingsRepository.updateInterests(interests.ifBlank { "AI, IT" })
                    settingsRepository.updateDailyTodo(todo.ifBlank { "오늘 할 일을 입력해 주세요" })
                    settingsRepository.updateTextTone(tone)
                    settingsRepository.updateFontSize(fontSize)
                    settingsRepository.updateHomeTopPadding(homeTopPadding)
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
                scope.launch {
                    launcherPrefsRepository.setAlias(packageName, alias)
                }
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
        "small" -> 24.sp
        "large" -> 32.sp
        else -> 28.sp
    }
    val bodyFontSize = when (settings.fontSize) {
        "small" -> 14.sp
        "large" -> 18.sp
        else -> 16.sp
    }
    val bodyLineHeight = when (settings.fontSize) {
        "small" -> 15.sp
        "large" -> 19.sp
        else -> 17.sp
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
                        } else {
                            if (totalDy < -120f) onSwipeUp()
                        }
                        totalDx = 0f
                        totalDy = 0f
                    }
                )
            }
            .combinedClickable(
                onClick = onToggleNewsMode,
                onLongClick = onLongPress
            )
            .padding(horizontal = 20.dp)
    ) {
        val topPadding = when (settings.homeTopPadding) {
            "high" -> maxHeight / 9
            "mid" -> maxHeight * 0.18f
            "low" -> maxHeight / 4
            else -> maxHeight * 0.18f
        }

        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = topPadding),
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            if (isNewsMode) {
                val marketLines = listOf(
                    Triple("KOSPI", "2,845.10", "+0.73%"),
                    Triple("NASDAQ", "18,920.44", "-0.41%"),
                    Triple("USDKRW", "1,372.50", "+0.12%")
                )

                marketLines.forEach { (label, value, change) ->
                    Row {
                        Text(
                            text = "$label $value ",
                            color = textColor,
                            fontSize = bodyFontSize,
                            fontWeight = FontWeight.Normal,
                            letterSpacing = (-0.2).sp,
                            lineHeight = bodyLineHeight
                        )
                        Text(
                            text = change,
                            color = when {
                                change.startsWith("+") -> Color(0xFFFF3B30)
                                change.startsWith("-") -> Color(0xFF2F6BFF)
                                else -> textColor
                            },
                            fontSize = bodyFontSize,
                            fontWeight = FontWeight.Normal,
                            letterSpacing = (-0.2).sp,
                            lineHeight = bodyLineHeight
                        )
                    }
                }
            } else {
                Text(
                    text = if (weatherLoading) "--°C" else "${weather.temperatureCelsius}°C",
                    color = textColor,
                    fontSize = tempFontSize,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.8).sp
                )
                Text(
                    text = if (weatherLoading) "날씨 불러오는 중..." else weather.airQualitySummary,
                    color = textColor,
                    fontSize = bodyFontSize,
                    fontWeight = FontWeight.Normal,
                    letterSpacing = (-0.2).sp,
                    lineHeight = bodyLineHeight
                )
                Text(
                    text = "오늘 할 일: ${settings.dailyTodo}",
                    color = textColor,
                    fontSize = bodyFontSize,
                    fontWeight = FontWeight.Normal,
                    letterSpacing = (-0.2).sp,
                    lineHeight = bodyLineHeight
                )
            }
        }
    }
}


// clock removed
@Composable
private fun AppsScreen(
    apps: List<Pair<String, String>>,
    aliases: Map<String, String>,
    hiddenPackages: Set<String>,
    onBackHome: () -> Unit,
    onLaunchApp: (String) -> Unit
) {
    val visibleApps = apps.filterNot { hiddenPackages.contains(it.first) }
    var totalDx by remember { mutableFloatStateOf(0f) }
    var totalDy by remember { mutableFloatStateOf(0f) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDrag = { _, dragAmount ->
                        totalDx += dragAmount.x
                        totalDy += dragAmount.y
                    },
                    onDragEnd = {
                        if (kotlin.math.abs(totalDy) > kotlin.math.abs(totalDx) && totalDy > 90f) {
                            onBackHome()
                        }
                        totalDx = 0f
                        totalDy = 0f
                    }
                )
            }
            .padding(start = 18.dp, end = 18.dp, bottom = 18.dp, top = 36.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text("Apps", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }

        if (apps.isEmpty()) {
            Text("앱 목록 불러오는 중...", color = Color.White.copy(alpha = 0.7f))
        } else {
            LazyColumn(
                modifier = Modifier.pointerInput(Unit) {
                    detectDragGestures(
                        onDrag = { _, dragAmount ->
                            totalDx += dragAmount.x
                            totalDy += dragAmount.y
                        },
                        onDragEnd = {
                            if (kotlin.math.abs(totalDy) > kotlin.math.abs(totalDx) && totalDy > 120f) {
                                onBackHome()
                            }
                            totalDx = 0f
                            totalDy = 0f
                        }
                    )
                },
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(visibleApps) { (pkg, originalName) ->
                    val label = aliases[pkg].takeUnless { it.isNullOrBlank() } ?: originalName
                    Text(
                        text = label,
                        color = Color.White,
                        fontSize = 18.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onLaunchApp(pkg) }
                            .padding(vertical = 8.dp)
                    )
                }
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
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = Color.Transparent,
            contentColor = Color.White
        )
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
    onSaveSettings: (region: String, interests: String, todo: String, tone: String, fontSize: String, homeTopPadding: String) -> Unit,
    onToggleHidden: (packageName: String, hidden: Boolean) -> Unit,
    onAliasChanged: (packageName: String, alias: String) -> Unit
) {
    var region by remember { mutableStateOf(settings.region) }
    var interests by remember { mutableStateOf(settings.interests) }
    var todo by remember { mutableStateOf(settings.dailyTodo) }
    var tone by remember { mutableStateOf(settings.textTone) }
    var fontSize by remember { mutableStateOf(settings.fontSize) }
    var homeTopPadding by remember { mutableStateOf(settings.homeTopPadding) }

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
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF141414))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("런처 설정", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            MonoButton(label = "취소", onClick = onBackHome)
        }

        CompositionLocalProvider(LocalTextSelectionColors provides whiteSelectionColors) {
            OutlinedTextField(
                value = region,
                onValueChange = { region = it },
                label = { Text("지역") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                modifier = Modifier.fillMaxWidth(),
                colors = settingsTextFieldColors
            )
            OutlinedTextField(
                value = interests,
                onValueChange = { interests = it },
                label = { Text("관심사") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                modifier = Modifier.fillMaxWidth(),
                colors = settingsTextFieldColors
            )
            OutlinedTextField(
                value = todo,
                onValueChange = { todo = it },
                label = { Text("오늘 할 일") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                modifier = Modifier.fillMaxWidth(),
                colors = settingsTextFieldColors
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MonoButton(label = "라이트") { tone = "light" }
            MonoButton(label = "다크") { tone = "dark" }
            Text("현재: $tone", color = Color.White, modifier = Modifier.align(Alignment.CenterVertically))
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MonoButton(label = "작게") { fontSize = "small" }
            MonoButton(label = "보통") { fontSize = "medium" }
            MonoButton(label = "크게") { fontSize = "large" }
            Text("현재: $fontSize", color = Color.White, modifier = Modifier.align(Alignment.CenterVertically))
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MonoButton(label = "위로") { homeTopPadding = "high" }
            MonoButton(label = "중간") { homeTopPadding = "mid" }
            MonoButton(label = "아래") { homeTopPadding = "low" }
            Text("위치: $homeTopPadding", color = Color.White, modifier = Modifier.align(Alignment.CenterVertically))
        }

        MonoButton(
            label = "저장",
            modifier = Modifier.fillMaxWidth(),
            onClick = { onSaveSettings(region, interests, todo, tone, fontSize, homeTopPadding) }
        )

        Text("앱 이름/숨김 설정", color = Color.White, fontWeight = FontWeight.SemiBold)

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(apps) { (pkg, originalName) ->
                val currentAlias = aliasDrafts[pkg] ?: aliases[pkg].orEmpty()

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White.copy(alpha = 0.03f))
                        .padding(10.dp)
                ) {
                    Text(originalName, color = Color.White, fontWeight = FontWeight.Medium)
                    Text(pkg, color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp)

                    CompositionLocalProvider(LocalTextSelectionColors provides whiteSelectionColors) {
                        OutlinedTextField(
                            value = currentAlias,
                            onValueChange = { aliasDrafts[pkg] = it },
                            label = { Text("런처 표시 이름") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = settingsTextFieldColors
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MonoButton(label = "이름 저장") { onAliasChanged(pkg, aliasDrafts[pkg] ?: "") }
                        if (hiddenPackages.contains(pkg)) {
                            MonoButton(label = "숨김 해제") { onToggleHidden(pkg, false) }
                        } else {
                            MonoButton(label = "숨기기") { onToggleHidden(pkg, true) }
                        }
                    }
                }
            }
        }
    }
}
