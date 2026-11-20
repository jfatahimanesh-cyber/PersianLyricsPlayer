package com.example.persianlyrics.data.gemini

import com.example.persianlyrics.data.lrclib.LyricLine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString

class GeminiLyricsTranslatorService(
    private val apiService: GeminiApiService = GeminiClientFactory.create()
) {

    suspend fun translateLinesKeepTimestamps(
        lines: List<LyricLine>,
        apiKey: String,
        model: String = "gemini-2.5-flash",
        style: PersianLyricStyle = PersianLyricStyle.FLUENT_TRANSLATION,
        batchSize: Int = 40
    ): Result<List<LyricLine>> = withContext(Dispatchers.IO) {
        runCatching {
            require(apiKey.isNotBlank()) { "کلید API جمنای وارد نشده است." }
            if (lines.isEmpty()) return@runCatching emptyList()

            val translationsById = mutableMapOf<Int, String>()
            val batches = lines.chunked(batchSize.coerceAtLeast(1))

            batches.forEachIndexed { batchIndex, batch ->
                val batchResult = translateSingleBatch(
                    batch = batch,
                    apiKey = apiKey.trim(),
                    model = model.trim(),
                    style = style
                )
                translationsById.putAll(batchResult)

                if (batchIndex < batches.lastIndex) {
                    delay(400L)
                }
            }

            lines.map { originalLine ->
                originalLine.copy(
                    translation = translationsById[originalLine.id]
                        ?: originalLine.translation
                        ?: originalLine.text
                )
            }
        }
    }

    private suspend fun translateSingleBatch(
        batch: List<LyricLine>,
        apiKey: String,
        model: String,
        style: PersianLyricStyle
    ): Map<Int, String> {
        val compactInput = batch.map { line ->
            mapOf(
                "id" to line.id,
                "timeMs" to line.startTimeMs,
                "text" to line.text
            )
        }

        val systemPrompt = """
            تو یک مترجم حرفه‌ای لیریک آهنگ به زبان فارسی هستی.
            قوانین حیاتی:
            1. ${style.promptInstruction}
            2. هر آیتم را دقیقاً بر اساس فیلد "id" ترجمه کن؛ هیچ خطی را ادغام یا حذف نکن.
            3. فیلد "timeMs" را تغییر نده؛ زمان‌بندی در سمت کلاینت حفظ می‌شود.
            4. خروجی باید فقط یک شیء JSON معتبر با ساختار {"lines":[{"id":1,"translation":"..."}]} باشد و هیچ متن اضافی یا بلاک کد نداشته باشد.
        """.trimIndent()

        val userPrompt = """
            این خطوط لیریک را به فارسی ترجمه کن:
            ${GeminiClientFactory.json.encodeToString(compactInput)}
        """.trimIndent()

        val request = GeminiGenerateRequest(
            systemInstruction = GeminiContent(
                parts = listOf(GeminiPart(text = systemPrompt))
            ),
            contents = listOf(
                GeminiContent(
                    role = "user",
                    parts = listOf(GeminiPart(text = userPrompt))
                )
            ),
            generationConfig = GeminiGenerationConfig(
                temperature = if (style == PersianLyricStyle.POETIC_RHYTHMIC) 0.7f else 0.3f,
                responseMimeType = "application/json"
            )
        )

        val response = apiService.generateContent(
            model = model,
            apiKey = apiKey,
            request = request
        )

        if (!response.isSuccessful) {
            val errorBody = response.errorBody()?.string().orEmpty()
            if (response.code() == 429) {
                error("محدودیت نرخ درخواست (HTTP 429) برای کلید رایگان Gemini رخ داد.")
            }
            error("خطای Gemini API (HTTP ${response.code()}):$errorBody")
        }

        val rawJsonText = response.body()
            ?.candidates
            ?.firstOrNull()
            ?.content
            ?.parts
            ?.firstOrNull()
            ?.text
            ?.trim()
            .orEmpty()

        val cleanJson = sanitizeJsonOutput(rawJsonText)
        val parsed = GeminiClientFactory.json.decodeFromString<TranslationBatchPayload>(cleanJson)

        return parsed.lines.associate { it.id to it.persianTranslation.trim() }
    }

    private fun sanitizeJsonOutput(raw: String): String {
        return raw
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
    }
}
