package ca.mohawk.temi8

import okhttp3.MultipartBody
import retrofit2.http.Body
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part


interface OpenAIApiService {
    @Multipart
    @POST("v1/audio/transcriptions")
    suspend fun transcribeAudio(
        @Part file: MultipartBody.Part,
        @Part model: MultipartBody.Part,
        @Part language: MultipartBody.Part? = null
    ): TranscriptionResponse

    @POST("v1/chat/completions")
    suspend fun translateWithGPT(@Body request: GPTTranslationRequest): GPTTranslationResponse
}


data class GPTTranslationRequest(
    val model: String = "gpt-3.5-turbo",
    val messages: List<GPTMessage>,
    val temperature: Double = 0.2
)

data class GPTMessage(
    val role: String,
    val content: String
)

data class GPTTranslationResponse(
    val choices: List<GPTChoice>
)

data class GPTChoice(
    val message: GPTMessage
)


data class TranscriptionResponse(val text: String)