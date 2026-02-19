package com.example.minimalwidget.data.repository

import com.example.minimalwidget.data.model.WeatherInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.roundToInt

interface WeatherRepository {
    suspend fun getCurrentWeather(region: String = "Seoul"): WeatherInfo
}

class SeoulWeatherRepository : WeatherRepository {
    override suspend fun getCurrentWeather(region: String): WeatherInfo = withContext(Dispatchers.IO) {
        runCatching {
            val (lat, lon) = coordinates(region)
            val temperature = fetchTemperatureCelsius(lat, lon)
            val pm10 = fetchPm10(lat, lon)
            WeatherInfo(
                temperatureCelsius = temperature.roundToInt(),
                airQualitySummary = "미세먼지 ${toAirQualityLabel(pm10)}"
            )
        }.getOrElse {
            runCatching { fetchWeatherFromWttr(region) }
                .getOrElse {
                    WeatherInfo(
                        temperatureCelsius = 12,
                        airQualitySummary = "날씨 정보를 불러오지 못했어요"
                    )
                }
        }
    }

    private fun coordinates(region: String): Pair<Double, Double> {
        return when (region.trim().lowercase()) {
            "seoul", "서울" -> 37.5665 to 126.9780
            "busan", "부산" -> 35.1796 to 129.0756
            "incheon", "인천" -> 37.4563 to 126.7052
            "daegu", "대구" -> 35.8722 to 128.6025
            "daejeon", "대전" -> 36.3504 to 127.3845
            else -> 37.5665 to 126.9780
        }
    }

    private fun fetchTemperatureCelsius(lat: Double, lon: Double): Double {
        val json = requestJson(
            "https://api.open-meteo.com/v1/forecast" +
                "?latitude=$lat&longitude=$lon" +
                "&current=temperature_2m&timezone=Asia%2FSeoul"
        )
        return json.getJSONObject("current").getDouble("temperature_2m")
    }

    private fun fetchPm10(lat: Double, lon: Double): Double {
        val json = requestJson(
            "https://air-quality-api.open-meteo.com/v1/air-quality" +
                "?latitude=$lat&longitude=$lon" +
                "&current=pm10&timezone=Asia%2FSeoul"
        )
        val current = json.getJSONObject("current")
        return current.optDouble("pm10", 35.0)
    }

    private fun fetchWeatherFromWttr(region: String): WeatherInfo {
        val encodedRegion = java.net.URLEncoder.encode(region, Charsets.UTF_8.name())
        val json = requestJson("https://wttr.in/$encodedRegion?format=j1")
        val current = json.getJSONArray("current_condition").getJSONObject(0)
        val temp = current.optString("temp_C", "12").toIntOrNull() ?: 12
        val desc = current.optJSONArray("weatherDesc")
            ?.optJSONObject(0)
            ?.optString("value")
            ?.takeIf { it.isNotBlank() }
            ?: "날씨 정보"

        return WeatherInfo(
            temperatureCelsius = temp,
            airQualitySummary = desc
        )
    }

    private fun requestJson(url: String): JSONObject {
        val connection = URL(url).openConnection() as HttpURLConnection
        return connection.run {
            requestMethod = "GET"
            connectTimeout = 5000
            readTimeout = 5000
            try {
                if (responseCode !in 200..299) {
                    throw IOException("HTTP $responseCode for $url")
                }
                inputStream.bufferedReader().use { reader ->
                    JSONObject(reader.readText())
                }
            } finally {
                disconnect()
            }
        }
    }

    private fun toAirQualityLabel(pm10: Double): String {
        return when {
            pm10 <= 30.0 -> "좋음"
            pm10 <= 80.0 -> "보통"
            pm10 <= 150.0 -> "나쁨"
            else -> "매우 나쁨"
        }
    }
}

class MockWeatherRepository : WeatherRepository {
    override suspend fun getCurrentWeather(region: String): WeatherInfo {
        return WeatherInfo(
            temperatureCelsius = 12,
            airQualitySummary = "미세먼지 좋음"
        )
    }
}
