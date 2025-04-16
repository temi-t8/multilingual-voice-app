package ca.mohawk.temi8

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Singleton object for creating and managing a Retrofit client configured to communicate with OpenAI's API.
 */
object RetrofitClient {
    private const val OPENAI_BASE_URL = "https://api.openai.com/"
    
    // OkHttpClient with interceptors for logging and setting authorization headers
    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor { chain ->
            val original = chain.request()
            val request = original.newBuilder()
                .addHeader("Authorization", "Bearer YOUR_OPEN_API_KEY")
                .build()
            chain.proceed(request)
        }
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        })
        .build()
    /**
     * A lazy-initialized instance of OpenAIApiService for making API requests.
     */
    val openAIApiService: OpenAIApiService by lazy {
        Retrofit.Builder()
            .baseUrl(OPENAI_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(OpenAIApiService::class.java)
    }
}
