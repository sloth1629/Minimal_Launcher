package com.example.minimalwidget.data.repository

interface TodoRepository {
    suspend fun getDailyTodo(): String
}

class MockTodoRepository : TodoRepository {
    override suspend fun getDailyTodo(): String {
        return "To-do: 10 min walk"
    }
}
