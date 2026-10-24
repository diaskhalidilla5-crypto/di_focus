package com.difocus.app

import android.content.Context

enum class TaskType {
    PHOTO_PROOF,
    STUDY_FILE
}

data class ChatMessage(
    val role: String, // "user" or "model"
    val text: String,
    val timestamp: String
)

data class FocusTask(
    val id: String,
    val dayKey: String, // e.g., "2026-10-25"
    val title: String,
    val description: String,
    val type: TaskType,
    val problemFileName: String? = null,
    val problemFileContent: String? = null,
    val answerKeyFileName: String? = null,
    val answerKeyContent: String? = null,
    var userSolution: String = "",
    var completed: Boolean = false,
    var aiFeedback: String? = null,
    val chatHistory: MutableList<ChatMessage> = mutableListOf()
)

object FocusRepository {
    private const val PREFS_NAME = "di_focus_prefs"
    private const val KEY_EMERGENCY_UNTIL = "emergency_until_ms"
    private const val KEY_API_KEY = "gemini_api_key"

    // Default user-supplied key
    const val DEFAULT_API_KEY = "AQ.Ab8RN6JOd9g6FgvS1X_ZDeH1p7eRW6b00VGxjmzs1G47-X9p-A"

    val blockedPackages = mutableSetOf(
        "com.zhiliaoapp.musically", // TikTok
        "com.instagram.android",    // Instagram
        "com.google.android.youtube", // YouTube
        "org.telegram.messenger",   // Telegram
        "com.vkontakte.android",    // VK
        "com.supercell.brawlstars"  // Games
    )

    val tasks = mutableListOf(
        FocusTask(
            id = "1",
            dayKey = "Сегодня",
            title = "Решить задачи по алгебре и логарифмам",
            description = "Прорешать 3 уравнения из прикрепленного файла и отправить ответы на проверку ИИ.",
            type = TaskType.STUDY_FILE,
            problemFileName = "algebra_tasks_day1.txt",
            problemFileContent = "Задание 1: Решите уравнение 2^(x+3) - 2^x = 56.\nЗадание 2: Найдите значение выражения log_3(81) + log_3(9).\nЗадание 3: Производная функции f(x) = 3x^2 - 12x + 7 в точке x = 3.",
            answerKeyFileName = "algebra_answers_secret.txt",
            answerKeyContent = "Ответ 1: x = 3 (так как 2^x * 7 = 56 => 2^x = 8 => x = 3).\nОтвет 2: 6 (4 + 2 = 6).\nОтвет 3: f'(x) = 6x - 12, при x = 3 ответ 6."
        ),
        FocusTask(
            id = "2",
            dayKey = "Сегодня",
            title = "Тренировка или утренняя разминка (Фото-пруф)",
            description = "Сделать живое фото спортивного инвентаря, зала или коврика после разминки.",
            type = TaskType.PHOTO_PROOF
        )
    )

    fun hasIncompleteTasksToday(): Boolean {
        return tasks.any { it.dayKey == "Сегодня" && !it.completed }
    }

    fun isEmergencyBypassActive(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val until = prefs.getLong(KEY_EMERGENCY_UNTIL, 0L)
        return System.currentTimeMillis() < until
    }

    fun activateEmergencyBypass(context: Context, minutes: Int = 5) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putLong(KEY_EMERGENCY_UNTIL, System.currentTimeMillis() + minutes * 60_000L)
            .apply()
    }

    fun getApiKey(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_API_KEY, DEFAULT_API_KEY) ?: DEFAULT_API_KEY
    }

    fun setApiKey(context: Context, key: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_API_KEY, key.trim()).apply()
    }
}
