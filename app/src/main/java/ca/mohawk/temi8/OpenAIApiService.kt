package ca.mohawk.temi8

import okhttp3.MultipartBody
import retrofit2.http.Body
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

/**
 * Retrofit interface for OpenAI API endpoints, including audio transcription and GPT-based translation.
 */
interface OpenAIApiService {
    /**
     * Transcribes audio files using Whisper endpoint.
     * @param file The audio file part.
     * @param model The model specification part (e.g., "whisper-1").
     * @param language An optional language part for the audio.
     */
    
    @Multipart
    @POST("v1/audio/transcriptions")
    suspend fun transcribeAudio(
        @Part file: MultipartBody.Part,
        @Part model: MultipartBody.Part,
        @Part language: MultipartBody.Part? = null
    ): TranscriptionResponse
    
    /**
     * Translates or processes text using GPT-based chat completions.
     * @param request The GPTTranslationRequest containing messages and settings.
     */
    @POST("v1/chat/completions")
    suspend fun translateWithGPT(@Body request: GPTTranslationRequest): GPTTranslationResponse
}

/**
 * Request body for a GPT-based translation or transformation call.
 * @param model The GPT model to use.
 * @param messages A list of GPTMessage objects to provide context.
 * @param temperature Controls randomness of the GPT model.
 */
data class GPTTranslationRequest(
    val model: String = "gpt-3.5-turbo",
    val messages: List<GPTMessage>,
    val temperature: Double = 0.2
)

/**
 * Represents a single message in the GPT conversation.
 * @param role The role (e.g., "system", "user").
 * @param content The textual content of the message.
 */
data class GPTMessage(
    val role: String,
    val content: String
)

/**
 * Response body from GPT-based translations or completions.
 * @param choices A list of GPTChoice objects containing translated or generated text.
 */
data class GPTTranslationResponse(
    val choices: List<GPTChoice>
)

/**
 * Represents a single choice of completion from the GPT API.
 * @param message The GPTMessage returned by the model.
 */
data class GPTChoice(
    val message: GPTMessage
)

/**
 * Response from the Whisper transcription endpoint.
 * @param text The transcribed text.
 */
data class TranscriptionResponse(val text: String)
