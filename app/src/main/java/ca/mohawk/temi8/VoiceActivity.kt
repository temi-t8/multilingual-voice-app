package ca.mohawk.temi8

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.*
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.airbnb.lottie.LottieAnimationView
import kotlinx.coroutines.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.*

class VoiceActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var waveAnim: LottieAnimationView
    private lateinit var micAnim: LottieAnimationView
    private lateinit var toggleButton: ImageButton

    private lateinit var textToSpeech: TextToSpeech
    private var isTtsReady = false
    private var isMicActive = false
    private var mediaRecorder: MediaRecorder? = null
    private var audioFile: File? = null

    private val SILENCE_THRESHOLD = 150
    private val SILENCE_TIMEOUT = 4000L
    private var lastSoundTime: Long = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_voice)

        waveAnim = findViewById(R.id.stateWaveAnimation)
        micAnim = findViewById(R.id.micButton)
        toggleButton = findViewById(R.id.btnToggleInterface)

        textToSpeech = TextToSpeech(this, this)

        toggleButton.setOnClickListener { finish() }

        micAnim.setOnClickListener {
            if (!isMicActive) {
                isMicActive = true
                WebSocketManager.connect()
                micAnim.playAnimation()
                updateWaveAnimation("listening")
                startRecording()
            } else {
                stopConversation()
            }
        }
        updateWaveAnimation("listening")

        WebSocketManager.onMessageReceived = { reply ->
            CoroutineScope(Dispatchers.IO).launch {
                updateWaveAnimation("thinking")

                val responseText = try {
                    val language = "en"
                    val gptResponse = RetrofitClient.openAIApiService.translateWithGPT(
                        GPTTranslationRequest(
                            messages = listOf(
                                GPTMessage("system", "Translate this into $language."),
                                GPTMessage("user", reply)
                            )
                        )
                    )
                    gptResponse.choices[0].message.content
                } catch (e: Exception) {
                    Log.e("TTSFlow", "GPT Error: ${e.message}")
                    reply
                }

                withContext(Dispatchers.Main) {
                    updateWaveAnimation("answering")
                    if (isTtsReady) {
                        val params = Bundle()
                        textToSpeech.speak(responseText, TextToSpeech.QUEUE_FLUSH, params, "reply_id")
                    }
                }
            }
        }

        textToSpeech.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                updateWaveAnimation("answering")
            }

            override fun onDone(utteranceId: String?) {
                Handler(Looper.getMainLooper()).post {
                    updateWaveAnimation("listening")
                    if (isMicActive) startRecording()
                }
            }

            override fun onError(utteranceId: String?) {
                updateWaveAnimation("listening")
            }
        })

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 1)
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            isTtsReady = true
            textToSpeech.language = Locale.ENGLISH
        }
    }

    private fun stopConversation() {
        isMicActive = false
        stopRecording()
        WebSocketManager.disconnect()
        micAnim.pauseAnimation()
        updateWaveAnimation("listening")
    }

    private fun startRecording() {
        try {
            audioFile = File.createTempFile("audio", ".mp3", cacheDir)
            mediaRecorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(audioFile?.absolutePath)
                prepare()
                start()
            }
            lastSoundTime = System.currentTimeMillis()
            detectSilence()
        } catch (e: Exception) {
            Log.e("VoiceRecording", "Start error: ${e.message}")
        }
    }

    private fun detectSilence() {
        CoroutineScope(Dispatchers.IO).launch {
            while (isMicActive && mediaRecorder != null) {
                val amplitude = mediaRecorder?.maxAmplitude ?: 0
                val now = System.currentTimeMillis()
                val silentDuration = if (amplitude > SILENCE_THRESHOLD) {
                    lastSoundTime = now
                    0
                } else now - lastSoundTime

                if (silentDuration > SILENCE_TIMEOUT) {
                    withContext(Dispatchers.Main) {
                        stopRecording()
                        updateWaveAnimation("thinking")
                        onVoicePauseDetected()
                    }
                    break
                }
                delay(200)
            }
        }
    }

    private fun stopRecording() {
        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
        } catch (e: Exception) {
            Log.e("VoiceRecording", "Stop error: ${e.message}")
        }
        mediaRecorder = null
    }

    private fun onVoicePauseDetected() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (audioFile == null || audioFile!!.length() < 1024) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@VoiceActivity, "Recording too short", Toast.LENGTH_SHORT).show()
                        if (isMicActive) startRecording()
                    }
                    return@launch
                }

                val requestFile = audioFile!!.asRequestBody("audio/mpeg".toMediaType())
                val audioPart = MultipartBody.Part.createFormData("file", "audio.mp3", requestFile)
                val modelField = MultipartBody.Part.createFormData("model", null, "whisper-1".toRequestBody("text/plain".toMediaType()))

                val transcriptionResponse = RetrofitClient.openAIApiService.transcribeAudio(audioPart, modelField)
                val userText = transcriptionResponse.text

                withContext(Dispatchers.Main) {
                    WebSocketManager.sendMessage(userText)
                }

            } catch (e: Exception) {
                Log.e("VoicePause", "Error: ${e.message}")
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@VoiceActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                    updateWaveAnimation("listening")
                }
            }
        }
    }

    private fun updateWaveAnimation(state: String) {
        val animationRes = when (state) {
            "thinking" -> R.raw.wave_thinking
            "answering" -> R.raw.wave_answering
            else -> R.raw.wave_listening
        }
        waveAnim.setAnimation(animationRes)
        waveAnim.playAnimation()
    }

    override fun onDestroy() {
        stopConversation()
        textToSpeech.shutdown()
        super.onDestroy()
    }
}
