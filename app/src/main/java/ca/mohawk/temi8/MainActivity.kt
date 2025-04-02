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
import android.widget.ImageButton

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var micAnimation: LottieAnimationView
    private lateinit var recordingStatus: TextView
    private lateinit var transcriptionRecyclerView: RecyclerView
    private lateinit var messageAdapter: MessageAdapter
    private lateinit var languageSpinner: Spinner
    private val messages = mutableListOf<Message>()

    private var mediaRecorder: MediaRecorder? = null
    private var isMicActive = false
    private var audioFile: File? = null
    private lateinit var textToSpeech: TextToSpeech
    private var isTtsReady = false
    private var isNewConversation = true
    private var isSpeaking = false

    private val SILENCE_THRESHOLD = 150
    private val SILENCE_TIMEOUT = 4000L
    private var lastSoundTime: Long = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        textToSpeech = TextToSpeech(this, this)

        micAnimation = findViewById(R.id.micAnimation)
        recordingStatus = findViewById(R.id.recordingStatus)
        languageSpinner = findViewById(R.id.languageSpinner)
        transcriptionRecyclerView = findViewById(R.id.transcriptionRecyclerView)

        messageAdapter = MessageAdapter(messages)
        transcriptionRecyclerView.layoutManager = LinearLayoutManager(this)
        transcriptionRecyclerView.adapter = messageAdapter

//        scrollUpButton.setOnClickListener {
//            val firstVisible = (transcriptionRecyclerView.layoutManager as LinearLayoutManager).findFirstVisibleItemPosition()
//            val newPosition = (firstVisible - 5).coerceAtLeast(0)
//            transcriptionRecyclerView.smoothScrollToPosition(newPosition)
//        }
//
//        scrollDownButton.setOnClickListener {
//            val lastVisible = (transcriptionRecyclerView.layoutManager as LinearLayoutManager).findLastVisibleItemPosition()
//            val newPosition = (lastVisible + 5).coerceAtMost(messages.size - 1)
//            transcriptionRecyclerView.smoothScrollToPosition(newPosition)
//        }

        val toggleButton = findViewById<ImageButton>(R.id.btnToggleInterface)
        toggleButton.setOnClickListener {
            val intent = Intent(this, VoiceActivity::class.java)
            startActivity(intent)
        }

        val btnNewChat = findViewById<ImageButton>(R.id.btnNewChat)
        btnNewChat.setOnClickListener {
            // Stop everything just in case
            stopConversation()
            textToSpeech.stop()

            // Reset UI state
            messages.clear()
            messageAdapter.notifyDataSetChanged()
            transcriptionRecyclerView.scrollToPosition(0)

            recordingStatus.text = "Tap mic to start conversation"
            recordingStatus.visibility = View.VISIBLE
            isNewConversation = true

            Toast.makeText(this, "New chat started", Toast.LENGTH_SHORT).show()
        }


        val languages = resources.getStringArray(R.array.languages)
        val adapter = ArrayAdapter(this, R.layout.spinner_item, languages)
        adapter.setDropDownViewResource(R.layout.spinner_item)
        languageSpinner.adapter = adapter

        textToSpeech.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                isMicActive = false
            }

            override fun onDone(utteranceId: String?) {
                Handler(Looper.getMainLooper()).post {
                    if (WebSocketManager.isWebSocketConnected()) {
                        isMicActive = true
                        startRecording()
                        recordingStatus.text = "Listening..."
                    }
                }
            }

            override fun onError(utteranceId: String?) {}
        })

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 1)
        }

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
                    messages.add(Message(translatedResponse, isUser = false))
                    messageAdapter.notifyItemInserted(messages.size - 1)
                    transcriptionRecyclerView.smoothScrollToPosition(messages.size - 1)

                    if (isTtsReady) {
                        val params = Bundle()
                        textToSpeech.speak(translatedResponse, TextToSpeech.QUEUE_FLUSH, params, "reply_id")
                    }
                    recordingStatus.text = "Speaking..."
                }
            }
        }

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

        languageSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                updateTtsLanguage()
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
    }

    private fun stopConversation() {
        isMicActive = false
        stopRecording()
        WebSocketManager.disconnect()
        micAnimation.pauseAnimation()
        recordingStatus.text = "Tap mic to start conversation"
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
            Log.e("AudioRecording", "Start Error: ${e.message}")
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
                        recordingStatus.text = "Generating response..."
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
            Log.e("AudioRecording", "Stop Error: ${e.message}")
        }
        mediaRecorder = null
    }

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
                                    GPTMessage("system", "Translate this into English."),
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

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            isTtsReady = true
            updateTtsLanguage()
        }
    }

    private fun updateTtsLanguage() {
        val languageCodes = resources.getStringArray(R.array.language_codes)
        val locale = Locale.forLanguageTag(languageCodes[languageSpinner.selectedItemPosition])
        if (textToSpeech.isLanguageAvailable(locale) >= TextToSpeech.LANG_AVAILABLE) {
            textToSpeech.language = locale
        }
    }

    override fun onDestroy() {
        textToSpeech.shutdown()
        super.onDestroy()
    }
}
