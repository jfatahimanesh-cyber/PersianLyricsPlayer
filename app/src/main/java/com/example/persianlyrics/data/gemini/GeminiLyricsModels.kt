package com.example.persianlyrics.data.gemini

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

enum class PersianLyricStyle(val promptInstruction: String) {
    FLUENT_TRANSLATION(
        "ترجمه فارسی روان، طبیعی و وفادار به معنی اصلی هر خط ارائه بده."
    ),
    POETIC_RHYTHMIC(
        "ترجمه/بازنویسی فارسی موزیکال و خوش‌ریتم ارائه بده که با حال‌وهوای آهنگ هم‌خوانی داشته باشد ولی معنی اصلی خط را حفظ کند."
    )
}

@Serializable
data class GeminiGenerateRequest(
    val contents: List<GeminiContent>,
    val systemInstruction: GeminiContent? = null,
    val generationConfig: GeminiGenerationConfig? = null
)

@Serializable
data class GeminiContent(
    val role: String? = null,
    val parts: List<GeminiPart>
)

@Serializable
data class GeminiPart(
    val text: String
)

@Serializable
data class GeminiGenerationConfig(
    val temperature: Float = 0.4f,
    val responseMimeType: String = "application/json"
)

@Serializable
data class GeminiGenerateResponse(
    val candidates: List<GeminiCandidate> = emptyList()
)

@Serializable
data class GeminiCandidate(
    val content: GeminiContent? = null,
    val finishReason: String? = null
)

@Serializable
data class TranslatedLineItem(
    val id: Int,
    @SerialName("translation") val persianTranslation: String
)

@Serializable
internal data class TranslationBatchPayload(
    val lines: List<TranslatedLineItem>
)
