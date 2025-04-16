package ca.mohawk.temi8

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.*
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import android.view.View
import android.widget.*
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

/**
 * VoiceActivity manages audio input/output using MediaRecorder and TextToSpeech.
 * It also communicates with a WebSocket to handle conversation interactions.
 */
class VoiceActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var micButton: LottieAnimationView
    private lateinit var stateWaveAnimation: LottieAnimationView
    private lateinit var languageSpinner: Spinner
    private lateinit var statusLabel: TextView
    private lateinit var btnStop: ImageButton

    private var mediaRecorder: MediaRecorder? = null
    private var isMicActive = false
    private var audioFile: File? = null
    private lateinit var textToSpeech: TextToSpeech
    private var isTtsReady = false
    private var lastSoundTime: Long = 0
    private val SILENCE_TIMEOUT = 2000L

    /**
     * Called when the activity is created. Sets up UI components, TTS, permission checks,
     * and configures WebSocket callbacks.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_voice)


        micButton = findViewById(R.id.micButton)
        stateWaveAnimation = findViewById(R.id.stateWaveAnimation)
        languageSpinner = findViewById(R.id.languageSpinner)
        statusLabel = findViewById(R.id.recordingStatus)
        btnStop = findViewById(R.id.btnStop)

        textToSpeech = TextToSpeech(this, this)

        val languages = resources.getStringArray(R.array.languages)
        val adapter = ArrayAdapter(this, R.layout.spinner_item, languages)
        adapter.setDropDownViewResource(R.layout.spinner_item)
        languageSpinner.adapter = adapter

        // Update TTS language when spinner selection changes
        languageSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                updateTtsLanguage()
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        // Stop TTS and restart mic when btnStop is clicked
        findViewById<ImageButton>(R.id.btnStop).setOnClickListener {
            textToSpeech.stop()
            findViewById<ImageButton>(R.id.btnStop).visibility = View.GONE
            isMicActive = true
            WebSocketManager.connect()
            startRecording()
            setWaveState(WaveState.LISTENING)
            findViewById<TextView>(R.id.recordingStatus).text = "Listening..."
            micButton.playAnimation()
        }

        // Start a new chat by stopping the current conversation and resetting UI
        findViewById<ImageButton>(R.id.btnNewChat).setOnClickListener {
            stopConversation()
            Toast.makeText(this, "New chat started!", Toast.LENGTH_SHORT).show()
            statusLabel.text = "Start speaking to begin a new chat!"
            setWaveState(WaveState.IDLE)
            micButton.pauseAnimation()
        }

        // Toggle mic on or off based on current state
        micButton.setOnClickListener {
            if (!isMicActive) {
                isMicActive = true
                WebSocketManager.connect()
                micButton.playAnimation()
                setWaveState(WaveState.LISTENING)
                statusLabel.text = "Listening..."
                startRecording()
            } else {
                stopConversation()
            }
        }
        
        // Stop speaking and restart mic when btnStop is clicked
        btnStop.setOnClickListener {
            stopSpeakingAndRestartMic()
        }
        
        // Return to previous activity (MainActivity)
        findViewById<ImageButton>(R.id.btnToggleInterface).setOnClickListener {
            finish()
        }
        
        // Check for audio recording permission
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 1)
        }

        setWaveState(WaveState.IDLE)
        statusLabel.text = "Tap the mic and ask me anything!"
        micButton.pauseAnimation()

        // Handle messages received from WebSocket (assistant replies)
        WebSocketManager.onMessageReceived = { reply ->
            CoroutineScope(Dispatchers.IO).launch {
                val languageCodes = resources.getStringArray(R.array.language_codes)
                val selectedLang = languageCodes[languageSpinner.selectedItemPosition]

                val translatedResponse = if (selectedLang != "en") {
                    try {
                        val gptResponse = RetrofitClient.openAIApiService.translateWithGPT(
                            GPTTranslationRequest(
                                messages = listOf(
                                    GPTMessage("system", "Translate this into $selectedLang."),
                                    GPTMessage("user", reply)
                                )
                            )
                        )
                        gptResponse.choices[0].message.content
                    } catch (e: Exception) {
                        Log.e("TranslateBack", "Failed: ${e.message}")
                        reply
                    }
                } else reply

                withContext(Dispatchers.Main) {
                    if (isTtsReady) {
                        val params = Bundle()
                        textToSpeech.speak(translatedResponse, TextToSpeech.QUEUE_FLUSH, params, "reply_id")
                    }
                    setWaveState(WaveState.SPEAKING)
                    statusLabel.text = "Speaking..."
                }
            }
        }
    }
    
    /**
     * Stops ongoing conversation by halting mic and disconnecting WebSocket.
     */
    private fun stopConversation() {
        isMicActive = false
        stopRecording()
        WebSocketManager.disconnect()
        micButton.pauseAnimation()
        statusLabel.text = "Tap mic to start conversation"
        setWaveState(WaveState.IDLE)
    }
    
    /**
     * Stops TTS playback, reconnects WebSocket, and restarts audio recording for a new conversation.
     */
    private fun stopSpeakingAndRestartMic() {
        textToSpeech.stop()
        isMicActive = true
        WebSocketManager.connect()
        startRecording()
        setWaveState(WaveState.LISTENING)
        statusLabel.text = "Listening..."
        micButton.playAnimation()
    }

    /**
     * Starts audio recording using MediaRecorder, storing the output in a temp file.
     * Also initiates silence detection in a background coroutine.
     */
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
            Log.e("AudioRecording", "Start Error: ${e.message}")
        }
    }
    
    /**
     * Continuously checks whether the user has stopped speaking by measuring
     * the amplitude of recorded audio. If silence is detected for a given
     * duration (SILENCE_TIMEOUT) or a fallback time is reached, processing is triggered.
     */
    private fun detectSilence() {
        CoroutineScope(Dispatchers.IO).launch {
            val noiseSamples = mutableListOf<Int>()
            val maxWaitTime = 10_000L // Fallback timeout (10s)
            val startTime = System.currentTimeMillis()

            while (isMicActive && mediaRecorder != null) {
                val amplitude = mediaRecorder?.maxAmplitude ?: 0
                val now = System.currentTimeMillis()

                // Smooth noise sample buffer
                noiseSamples.add(amplitude)
                if (noiseSamples.size > 30) noiseSamples.removeAt(0)

                val averageNoise = noiseSamples.average().toInt()
                val silenceThreshold = averageNoise * 0.5 // Adaptive threshold

                // Detect actual silence
                val isSilent = amplitude < silenceThreshold
                if (!isSilent) {
                    lastSoundTime = now
                }

                val silentDuration = now - lastSoundTime
                val elapsed = now - startTime

                if (silentDuration > SILENCE_TIMEOUT || elapsed > maxWaitTime) {
                    withContext(Dispatchers.Main) {
                        stopRecording()
                        setWaveState(WaveState.THINKING)
                        statusLabel.text = "Generating response..."
                        onVoicePauseDetected()
                    }
                    break
                }

                delay(250)
            }
        }
    }

    /**
     * Stops recording safely, releasing MediaRecorder resources.
     */
    private fun stopRecording() {
        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
        } catch (e: Exception) {
            Log.e("AudioRecording", "Stop Error: ${e.message}")
        }
        mediaRecorder = null
    }

    /**
     * Called when a period of silence is detected. Sends the recorded audio
     * for transcription and then passes the transcribed text to the WebSocket.
     */
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

                val transcriptionResponse = RetrofitClient.openAIApiService.transcribeAudio(
                    audioPart, modelField
                )

                val originalText = transcriptionResponse.text
                val languageCodes = resources.getStringArray(R.array.language_codes)
                val selectedLanguage = languageCodes[languageSpinner.selectedItemPosition]

                val translatedToEnglish = if (selectedLanguage != "en") {
                    try {
                        val gptResponse = RetrofitClient.openAIApiService.translateWithGPT(
                            GPTTranslationRequest(
                                messages = listOf(
                                    GPTMessage("system", "Translate this to English"),
                                    GPTMessage("user", originalText)
                                )
                            )
                        )
                        gptResponse.choices[0].message.content
                    } catch (e: Exception) {
                        originalText
                    }
                } else originalText

                withContext(Dispatchers.Main) {
                    WebSocketManager.sendMessage(translatedToEnglish)
                }

            } catch (e: Exception) {
                Log.e("Transcription", "Error: ${e.message}")
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@VoiceActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                    statusLabel.text = "Tap mic to try again"
                }
            }
        }
    }

    /**
     * Lifecycle callback invoked when TTS is initialized.
     * Sets up TTS language and UtteranceProgressListener.
     */
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            isTtsReady = true
            updateTtsLanguage()

            textToSpeech.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    isMicActive = false
                    stopRecording()
                    WebSocketManager.disconnect()
                    runOnUiThread {
                        btnStop.visibility = View.VISIBLE
                        setWaveState(WaveState.SPEAKING)
                        statusLabel.text = "Speaking..."
                    }
                }

                override fun onDone(utteranceId: String?) {
                    Handler(Looper.getMainLooper()).post {
                        btnStop.visibility = View.GONE
                        if (WebSocketManager.isWebSocketConnected()) {
                            isMicActive = true
                            startRecording()
                            setWaveState(WaveState.LISTENING)
                            statusLabel.text = "Listening..."
                        }
                    }
                }

                override fun onError(utteranceId: String?) {
                    runOnUiThread {
                        statusLabel.text = "Error speaking"
                        setWaveState(WaveState.IDLE)
                    }
                }
            })
        }
    }

    /**
     * Updates the TTS engine's language based on the user's selection from the spinner.
     */
    private fun updateTtsLanguage() {
        val languageCodes = resources.getStringArray(R.array.language_codes)
        val locale = Locale.forLanguageTag(languageCodes[languageSpinner.selectedItemPosition])
        if (textToSpeech.isLanguageAvailable(locale) >= TextToSpeech.LANG_AVAILABLE) {
            textToSpeech.language = locale
        }
    }
    
    /**
     * Called when the activity is destroyed. Shuts down TTS and stops the current conversation.
     */
    override fun onDestroy() {
        textToSpeech.shutdown()
        stopConversation()
        super.onDestroy()
    }

    /**
     * Enumeration to represent various states of the waveform animation.
     * LISTENING, THINKING, SPEAKING, or IDLE.
     */

    enum class WaveState {
        LISTENING, THINKING, SPEAKING, IDLE
    }

    /**
     * Sets the wave animation based on the current state of the VoiceActivity.
     * @param state The WaveState to be displayed.
     */

    private fun setWaveState(state: WaveState) {
        val resId = when (state) {
            WaveState.LISTENING -> R.raw.wave_listening
            WaveState.THINKING -> R.raw.wave_thinking
            WaveState.SPEAKING -> R.raw.wave_answering
            WaveState.IDLE -> R.raw.idle
        }
        stateWaveAnimation.setAnimation(resId)
        stateWaveAnimation.playAnimation()
    }
}
