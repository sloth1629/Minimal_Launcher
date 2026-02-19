package com.example.minimalwidget.data.repository

object Repositories {
    val weather: WeatherRepository = SeoulWeatherRepository()
    val todo: TodoRepository = MockTodoRepository()
    val news: NewsRepository = DcSingularityRecommendNewsRepository()
}
