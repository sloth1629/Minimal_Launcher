package com.example.minimalwidget

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
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
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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

    when (currentScreen) {
        LauncherScreen.Home -> HomeScreen(
            settings = settings,
            weather = weather,
            weatherLoading = weatherLoading,
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
                        val fallback = Intent(Intent.ACTION_VIEW, Uri.parse("sms:"))
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
            onSaveSettings = { region, interests, todo, tone, fontSize ->
                scope.launch {
                    settingsRepository.updateRegion(region.ifBlank { "Seoul" })
                    settingsRepository.updateInterests(interests.ifBlank { "technology" })
                    settingsRepository.updateDailyTodo(todo.ifBlank { "오늘 할 일을 입력해 주세요" })
                    settingsRepository.updateTextTone(tone)
                    settingsRepository.updateFontSize(fontSize)
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
    onSwipeLeft: () -> Unit,
    onSwipeRight: () -> Unit,
    onSwipeUp: () -> Unit,
    onLongPress: () -> Unit
) {
    var totalDx by remember { mutableStateOf(0f) }
    var totalDy by remember { mutableStateOf(0f) }

    val backgroundColor = if (settings.textTone == "dark") Color(0xFF0B0B0B) else Color(0xFFF6F6F6)
    val textColor = if (settings.textTone == "dark") Color(0xFFF2F2F2) else Color(0xFF111111)

    Box(
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
                onClick = {},
                onLongClick = onLongPress
            )
            .padding(horizontal = 20.dp, vertical = 28.dp)
    ) {
        Column(
            modifier = Modifier.align(Alignment.TopStart),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")),
                color = textColor,
                fontSize = 40.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = if (weatherLoading) "날씨 불러오는 중..." else "${weather.temperatureCelsius}°C · ${weather.airQualitySummary}",
                color = textColor,
                fontSize = 16.sp
            )
            Text(
                text = "오늘 할 일: ${settings.dailyTodo}",
                color = textColor,
                fontSize = 16.sp
            )
        }

        Text(
            text = "← 전화  |  메시지 →\n위로 스와이프: 앱 목록\n길게 누르기: 설정",
            color = textColor.copy(alpha = 0.65f),
            fontSize = 12.sp,
            modifier = Modifier.align(Alignment.BottomStart)
        )
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
            .background(Color(0xFF0E0E0E))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Apps", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Button(onClick = onBackHome) { Text("홈") }
        }

        if (apps.isEmpty()) {
            Text("앱 목록 불러오는 중...", color = Color.White.copy(alpha = 0.7f))
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(visibleApps) { (pkg, originalName) ->
                    val label = aliases[pkg].takeUnless { it.isNullOrBlank() } ?: originalName
                    Text(
                        text = label,
                        color = Color.White,
                        fontSize = 18.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onLaunchApp(pkg) }
                            .padding(vertical = 6.dp)
                    )
                    Divider(color = Color.White.copy(alpha = 0.12f))
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen(
    apps: List<Pair<String, String>>,
    aliases: Map<String, String>,
    hiddenPackages: Set<String>,
    settings: WidgetSettings,
    onBackHome: () -> Unit,
    onSaveSettings: (region: String, interests: String, todo: String, tone: String, fontSize: String) -> Unit,
    onToggleHidden: (packageName: String, hidden: Boolean) -> Unit,
    onAliasChanged: (packageName: String, alias: String) -> Unit
) {
    var region by remember { mutableStateOf(settings.region) }
    var interests by remember { mutableStateOf(settings.interests) }
    var todo by remember { mutableStateOf(settings.dailyTodo) }
    var tone by remember { mutableStateOf(settings.textTone) }
    var fontSize by remember { mutableStateOf(settings.fontSize) }

    val aliasDrafts = remember { mutableStateMapOf<String, String>() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF141414))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("런처 설정", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Button(onClick = onBackHome) { Text("취소") }
        }

        OutlinedTextField(
            value = region,
            onValueChange = { region = it },
            label = { Text("지역") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = interests,
            onValueChange = { interests = it },
            label = { Text("관심사") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = todo,
            onValueChange = { todo = it },
            label = { Text("오늘 할 일") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
            modifier = Modifier.fillMaxWidth()
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { tone = "light" }) { Text("라이트") }
            Button(onClick = { tone = "dark" }) { Text("다크") }
            Text("현재: $tone", color = Color.White, modifier = Modifier.align(Alignment.CenterVertically))
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { fontSize = "small" }) { Text("작게") }
            Button(onClick = { fontSize = "medium" }) { Text("보통") }
            Button(onClick = { fontSize = "large" }) { Text("크게") }
            Text("현재: $fontSize", color = Color.White, modifier = Modifier.align(Alignment.CenterVertically))
        }

        Button(
            onClick = { onSaveSettings(region, interests, todo, tone, fontSize) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("저장")
        }

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

                    OutlinedTextField(
                        value = currentAlias,
                        onValueChange = { aliasDrafts[pkg] = it },
                        label = { Text("런처 표시 이름") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { onAliasChanged(pkg, aliasDrafts[pkg] ?: "") }) { Text("이름 저장") }
                        if (hiddenPackages.contains(pkg)) {
                            Button(onClick = { onToggleHidden(pkg, false) }) { Text("숨김 해제") }
                        } else {
                            Button(onClick = { onToggleHidden(pkg, true) }) { Text("숨기기") }
                        }
                    }
                }
            }
        }
    }
}
