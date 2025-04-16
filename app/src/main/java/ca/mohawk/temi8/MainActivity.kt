package ca.mohawk.temi8

import android.Manifest
import android.content.Intent
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
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.airbnb.lottie.LottieAnimationView
import kotlinx.coroutines.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.*

/**
 * MainActivity is the primary activity for the Temi8 app. It handles:
 * - Audio recording (MediaRecorder) and silence detection.
 * - Speech-to-text transcription through Whisper/OpenAI.
 * - Text-to-speech output.
 * - WebSocket communication with a server.
 * - Language selection for both input and output translations.
 */
class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var micAnimation: LottieAnimationView
    private lateinit var recordingStatus: TextView
    private lateinit var transcriptionRecyclerView: RecyclerView
    private lateinit var messageAdapter: MessageAdapter
    private lateinit var languageSpinner: Spinner
    private val messages = mutableListOf<Message>()
    private lateinit var btnStop: ImageButton


    private var mediaRecorder: MediaRecorder? = null
    private var isMicActive = false
    private var audioFile: File? = null
    private lateinit var textToSpeech: TextToSpeech
    private var isTtsReady = false
    private var pendingSpeech: String? = null

    private val SILENCE_THRESHOLD = 200
    private val SILENCE_TIMEOUT = 2000L
    private var lastSoundTime: Long = 0

     /**
     * Called when the activity is starting. Initializes views, TTS, 
     * sets up UI listeners, and configures WebSocketManager.
     */

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize TextToSpeech with this activity as the listener
        textToSpeech = TextToSpeech(this, this)

        // Link UI elements from layout
        micAnimation = findViewById(R.id.micAnimation)
        recordingStatus = findViewById(R.id.recordingStatus)
        languageSpinner = findViewById(R.id.languageSpinner)
        transcriptionRecyclerView = findViewById(R.id.transcriptionRecyclerView)
        
        // Button to toggle to a different activity (VoiceActivity)
        findViewById<ImageButton>(R.id.btnToggleInterface).setOnClickListener {
            val intent = Intent(this, VoiceActivity::class.java)
            startActivity(intent)
        }

        // Stop button reference
        btnStop = findViewById(R.id.btnStop)
        
        // Stop button click listener stops speech and restarts mic
        btnStop.setOnClickListener {
            textToSpeech.stop()
            stopSpeakingAndRestartMic()
        }
        
        // Set up RecyclerView with a custom adapter for messages
        messageAdapter = MessageAdapter(messages)
        transcriptionRecyclerView.layoutManager = LinearLayoutManager(this)
        transcriptionRecyclerView.adapter = messageAdapter
        
        // Populate the language spinner with languages from resources
        val languages = resources.getStringArray(R.array.languages)
        val adapter = ArrayAdapter(this, R.layout.spinner_item, languages)
        adapter.setDropDownViewResource(R.layout.spinner_item)
        languageSpinner.adapter = adapter

        // Listen for TTS progress to manage record/speak states
        textToSpeech.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            // During TTS playback, we set isMicActive to false and stop recording
            override fun onStart(utteranceId: String?) {
                isMicActive = false
                stopRecording()
                WebSocketManager.disconnect()
                runOnUiThread {
                    btnStop.visibility = View.VISIBLE
                    recordingStatus.text = "Speaking..."
                }
            }

            // Once speaking is done, hide stop button and resume recording if connected
            override fun onDone(utteranceId: String?) {
                Handler(Looper.getMainLooper()).post {
                    btnStop.visibility = View.GONE
                    if (WebSocketManager.isWebSocketConnected()) {
                        isMicActive = true
                        startRecording()
                        recordingStatus.text = "Listening..."
                    }
                }
            }


            override fun onError(utteranceId: String?) {}
        })

        // Request microphone permission if not already granted
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 1)
        }

                // WebSocket callback for receiving messages (assistant replies)
        WebSocketManager.onMessageReceived = { reply ->
            CoroutineScope(Dispatchers.IO).launch {
                val languageCodes = resources.getStringArray(R.array.language_codes)
                val selectedLang = languageCodes[languageSpinner.selectedItemPosition]

                // If the selected language isn't English, try to translate the reply
                val translatedResponse = if (selectedLang != "en") {
                    try {
                        val gptResponse = RetrofitClient.openAIApiService.translateWithGPT(
                            GPTTranslationRequest(
                                messages = listOf(
                                    GPTMessage("system", "Translate this into casual, everyday ${selectedLang} like you’d say in a friendly conversation."),
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
                
                // Update UI with the assistant's translated response
                withContext(Dispatchers.Main) {
                    messages.add(Message(translatedResponse, isUser = false))
                    messageAdapter.notifyItemInserted(messages.size - 1)
                    transcriptionRecyclerView.smoothScrollToPosition(messages.size - 1)

                    val params = Bundle()
                    
                    // If TTS is ready, speak immediately; otherwise queue for later
                    if (isTtsReady) {
                        textToSpeech.speak(translatedResponse, TextToSpeech.QUEUE_FLUSH, params, "reply_id")
                    } else {
                        pendingSpeech = translatedResponse
                    }

                    recordingStatus.text = "Speaking..."
                }
            }
        }
        
        // Microphone animation click toggles between listening and stopping conversation
        micAnimation.setOnClickListener {
            if (!isMicActive) {
                isMicActive = true
                WebSocketManager.connect()
                micAnimation.playAnimation()
                recordingStatus.text = "Listening..."
                startRecording()
            } else {
                stopConversation()
            }
        }
        
        // Listener for language spinner selection changes
        languageSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                updateTtsLanguage()
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
    }
    
     /**
     * Stops any ongoing speech, hides the stop button, and restarts microphone recording.
     */
    private fun stopSpeakingAndRestartMic() {
        btnStop.visibility = View.GONE
        isMicActive = true
        WebSocketManager.connect()
        startRecording()
        recordingStatus.text = "Listening..."
        micAnimation.playAnimation()
    }

    /**
     * Stops the conversation by disabling mic, stopping recording, disconnecting WebSocket,
     * and updating UI accordingly.
     */

    private fun stopConversation() {
        isMicActive = false
        stopRecording()
        WebSocketManager.disconnect()
        micAnimation.pauseAnimation()
        recordingStatus.text = "Tap mic to start conversation"
    }
    
    /**
     * Starts audio recording using MediaRecorder, saving to a temporary file.
     * Also triggers silence detection in a background coroutine.
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
     * Continuously checks the recorded audio amplitude in the background.
     * If silence is detected for a certain duration (SILENCE_TIMEOUT), 
     * triggers a pause event to transcribe and handle the input.
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
                val silenceThreshold = averageNoise * 0.5 // More adaptive

                // Detect actual silence
                val isSilent = amplitude < silenceThreshold
                if (!isSilent) {
                    lastSoundTime = now
                }

                val silentDuration = now - lastSoundTime
                val elapsed = now - startTime
                
                // If silence or fallback time is reached, stop recording and process the input
                if (silentDuration > SILENCE_TIMEOUT || elapsed > maxWaitTime) {
                    withContext(Dispatchers.Main) {
                        stopRecording()
                        recordingStatus.text = "Generating response..."
                        onVoicePauseDetected()
                    }
                    break
                }

                delay(250)
            }
        }
    }


    /**
     * Stops the MediaRecorder safely, ignoring errors if it's not running.
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
     * Called when a pause in voice is detected. Submits the recorded audio for transcription
     * to OpenAI, optionally translates the transcribed text to English, and sends it
     * over WebSocket to get a reply.
     */
    private fun onVoicePauseDetected() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (audioFile == null || audioFile!!.length() < 1024) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@MainActivity, "Recording too short", Toast.LENGTH_SHORT).show()
                        if (isMicActive) startRecording()
                    }
                    return@launch
                }

                val requestFile = audioFile!!.asRequestBody("audio/mpeg".toMediaType())
                val audioPart = MultipartBody.Part.createFormData("file", "audio.mp3", requestFile)
                val modelField = MultipartBody.Part.createFormData("model", null, "whisper-1".toRequestBody("text/plain".toMediaType()))

                // Transcribe audio using the Retrofit client
                val transcriptionResponse = RetrofitClient.openAIApiService.transcribeAudio(
                    audioPart, modelField
                )

                val originalText = transcriptionResponse.text
                val languageCodes = resources.getStringArray(R.array.language_codes)
                val selectedLanguage = languageCodes[languageSpinner.selectedItemPosition]
                
                // If selected language is not English, translate the user input back to English
                val translatedToEnglish = if (selectedLanguage != "en") {
                    try {
                        val gptResponse = RetrofitClient.openAIApiService.translateWithGPT(
                            GPTTranslationRequest(
                                messages = listOf(
                                    GPTMessage("system", "Translate this into ${selectedLanguage}."),
                                    GPTMessage("user", originalText)
                                )
                            )
                        )
                        gptResponse.choices[0].message.content
                    } catch (e: Exception) {
                        Log.e("STTTranslate", "To-English failed: ${e.message}")
                        originalText
                    }
                } else originalText
                
                // Update UI with the user's transcribed message, then send to WebSocket
                withContext(Dispatchers.Main) {
                    messages.add(Message(originalText, isUser = true))
                    messageAdapter.notifyItemInserted(messages.size - 1)
                    transcriptionRecyclerView.smoothScrollToPosition(messages.size - 1)
                    WebSocketManager.sendMessage(translatedToEnglish)
                }

            } catch (e: Exception) {
                Log.e("Transcription", "Error: ${e.message}")
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                    recordingStatus.text = "Tap mic to try again"
                }
            }
        }
    }
    
    /**
     * Callback from TextToSpeech when initialization is complete.
     * If successful, updates language settings and processes any pending speech.
     */
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            isTtsReady = true
            updateTtsLanguage()

            // If there's something waiting to be spoken, do it now
            pendingSpeech?.let {
                val params = Bundle()
                textToSpeech.speak(it, TextToSpeech.QUEUE_FLUSH, params, "reply_id")
                pendingSpeech = null
            }
        } else {
            Log.e("TTS", "Initialization failed")
        }
    }

    /**
     * Updates the TextToSpeech engine's language based on the selected item
     * in the language spinner, if available.
     */
    private fun updateTtsLanguage() {
        val languageCodes = resources.getStringArray(R.array.language_codes)
        val locale = Locale.forLanguageTag(languageCodes[languageSpinner.selectedItemPosition])
        if (textToSpeech.isLanguageAvailable(locale) >= TextToSpeech.LANG_AVAILABLE) {
            textToSpeech.language = locale
        }
    }
    /**
     * Lifecycle callback invoked when the activity is about to be destroyed.
     * Shuts down TextToSpeech to release resources.
     */
    override fun onDestroy() {
        textToSpeech.shutdown()
        super.onDestroy()
    }
}
