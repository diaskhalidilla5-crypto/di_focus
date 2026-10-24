package com.difocus.app

import android.graphics.Bitmap
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

data class VerificationResult(
    val completed: Boolean,
    val feedback: String
)

object GeminiService {

    /**
     * Строгий чат-тьютор: отвечает ТОЛЬКО по текущему учебному заданию из прикрепленного файла.
     * Не дает готовых ответов напрямую и отказывается говорить на отвлеченные темы.
     */
    suspend fun askStudyTutor(
        apiKey: String,
        task: FocusTask,
        userQuestion: String
    ): String = withContext(Dispatchers.IO) {
        val systemInstruction = """
            Ты — строгий, но дружелюбный ИИ-тьютор внутри приложения DI Focus.
            Твоя ЕДИНСТВЕННАЯ задача — помогать пользователю разобраться с его текущим учебным заданием.
            
            Название задания: ${task.title}
            Описание: ${task.description}
            Текст файла заданий:
            ${task.problemFileContent ?: "Текст файла не прикреплен"}
            
            Секретный эталонный файл с ответами (ДЛЯ ТВОЕЙ СПРАВКИ, НЕ ВЫДАВАЙ ПРЯМЫЕ ОТВЕТЫ ПОЛЬЗОВАТЕЛЮ):
            ${task.answerKeyContent ?: "Файл с ответами не прикреплен, решай самостоятельно"}
            
            ЖЕСТКИЕ ПРАВИЛА:
            1. Отвечай ТОЛЬКО на вопросы, напрямую связанные с этим заданием и предметом.
            2. Если пользователь спрашивает про игры, фильмы, погоду, шутки, новости или просит поболтать на стороннюю тему — вежливо откажи и скажи: "Я могу общаться только по теме твоего текущего задания «${task.title}». Давай вернемся к решению!"
            3. Не пиши готовый финальный ответ сразу: объясни правило, формулу или следующий шаг решения, чтобы пользователь дошел до ответа сам.
        """.trimIndent()

        callGeminiText(apiKey, systemInstruction, userQuestion)
    }

    /**
     * Проверка письменного решения задач с учетом прикрепленного файла заданий и опционального файла ответов.
     */
    suspend fun verifyStudySolution(
        apiKey: String,
        task: FocusTask,
        userSolution: String
    ): VerificationResult = withContext(Dispatchers.IO) {
        val systemInstruction = """
            Ты — экзаменатор в приложении DI Focus. Проверь решение пользователя.
            
            Задание: ${task.title}
            Условия задач (из прикрепленного файла):
            ${task.problemFileContent ?: task.description}
            
            Эталонные ответы (из файла ответов, если прикреплен):
            ${task.answerKeyContent ?: "Не прикреплен — проверь математическую/логическую правильность самостоятельно."}
            
            Решение пользователя:
            $userSolution
            
            Ответь СТРОГО в формате JSON без markdown-обертки:
            {"completed": true/false, "feedback": "Подробный разбор: какие пункты решены верно, а где ошибка (без раскрытия правильного ответа для нерешенных пунктов)"}
            Ставь "completed": true только если все основные задачи решены верно!
        """.trimIndent()

        val raw = callGeminiText(apiKey, systemInstruction, "Проверь мое решение и верни JSON.")
        parseVerificationJson(raw)
    }

    /**
     * Проверка живого фото-пруфа с камеры через Gemini Vision.
     */
    suspend fun verifyPhotoProof(
        apiKey: String,
        task: FocusTask,
        bitmap: Bitmap
    ): VerificationResult = withContext(Dispatchers.IO) {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, stream)
        val base64Image = Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)

        val prompt = """
            Пользователь должен выполнить задачу для разблокировки телефона в DI Focus:
            Задача: "${task.title}" (${task.description}).
            Посмотри на фото и определи, действительно ли оно подтверждает выполнение задачи.
            Ответь СТРОГО в формате JSON:
            {"completed": true/false, "feedback": "Краткий комментарий на русском языке"}
        """.trimIndent()

        val raw = callGeminiVision(apiKey, prompt, base64Image)
        parseVerificationJson(raw)
    }

    private fun callGeminiText(apiKey: String, systemPrompt: String, userText: String): String {
        return try {
            val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$apiKey"
            val payload = JSONObject().apply {
                put("system_instruction", JSONObject().apply {
                    put("parts", JSONArray().put(JSONObject().put("text", systemPrompt)))
                })
                put("contents", JSONArray().put(JSONObject().apply {
                    put("role", "user")
                    put("parts", JSONArray().put(JSONObject().put("text", userText)))
                }))
            }
            executePost(endpoint, payload.toString())
        } catch (e: Exception) {
            "Ошибка соединения с Gemini API: ${e.localizedMessage}"
        }
    }

    private fun callGeminiVision(apiKey: String, prompt: String, base64Jpeg: String): String {
        return try {
            val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$apiKey"
            val parts = JSONArray()
                .put(JSONObject().put("text", prompt))
                .put(JSONObject().put("inline_data", JSONObject().apply {
                    put("mime_type", "image/jpeg")
                    put("data", base64Jpeg)
                }))
            val payload = JSONObject().apply {
                put("contents", JSONArray().put(JSONObject().put("parts", parts)))
            }
            executePost(endpoint, payload.toString())
        } catch (e: Exception) {
            "{"completed": false, "feedback": "Ошибка сети: ${e.localizedMessage}"}"
        }
    }

    private fun executePost(urlString: String, jsonBody: String): String {
        val url = URL(urlString)
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            doOutput = true
            connectTimeout = 15000
            readTimeout = 20000
        }
        conn.outputStream.use { it.write(jsonBody.toByteArray(Charsets.UTF_8)) }
        val code = conn.responseCode
        val responseText = (if (code in 200..299) conn.inputStream else conn.errorStream)
            ?.bufferedReader()?.readText() ?: ""
        if (code !in 200..299) {
            return "API Error ($code): Проверьте формат ключа Gemini API в настройках."
        }
        val root = JSONObject(responseText)
        return root.optJSONArray("candidates")
            ?.optJSONObject(0)
            ?.optJSONObject("content")
            ?.optJSONArray("parts")
            ?.optJSONObject(0)
            ?.optString("text") ?: "Пустой ответ от модели"
    }

    private fun parseVerificationJson(raw: String): VerificationResult {
        return try {
            val cleaned = raw.replace("```json", "").replace("```", "").trim()
            val start = cleaned.indexOf("{")
            val end = cleaned.lastIndexOf("}")
            if (start != -1 && end != -1 && end > start) {
                val obj = JSONObject(cleaned.substring(start, end + 1))
                VerificationResult(
                    completed = obj.optBoolean("completed", false),
                    feedback = obj.optString("feedback", "Проверка завершена.")
                )
            } else {
                VerificationResult(false, raw)
            }
        } catch (e: Exception) {
            VerificationResult(false, raw)
        }
    }
}
