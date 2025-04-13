# 🤖 TEMI Multilingual Voice App

Welcome to the **TEMI Multilingual Voice App**! This is an Android application that records audio through the device microphone and interacts with the OpenAI API for transcription, translation, and speech synthesis. It also supports real-time conversation via WebSocket. 

## ✨ Key Features
- **Voice Recording**: Captures audio in real-time via the device microphone.  
- **Transcription & Translation**: Utilizes OpenAI’s Whisper & GPT models to transcribe and optionally translate speech to different languages.  
- **Text-to-Speech (TTS)**: Replies are spoken back to the user using Android’s TTS engine.  
- **Real-Time Chat**: Communicates with a backend server via WebSocket for immediate response generation.  
- **TEMI Robot Integration**: Designed to run on the TEMI robot (or an actual Android device) rather than on an emulator.

---

## 📋 Requirements
- **Android Studio** (latest version recommended)
- **TEMI Robot** or an Android device with a microphone (the emulator will **not** work because it lacks microphone support)
- **OpenAI API Key** (for GPT-3.5-turbo or Whisper model calls)

---

## 🚀 Installation

1. **Clone the GitHub repository**:
   ```bash
   git clone https://github.com/temi-t8/multilingual-voice-app.git
   ```
2. Open the Project in Android Studio:
- Launch Android Studio.
- Click Open an Existing Project.
- Navigate to the cloned `multilingual-voice-app` folder and select it.

3. Add your OpenAI API Key:
- Open `RetrofitClient.kt`.
- Replace "Bearer YOUR_OPEN_API_KEY" with your actual OpenAI API key in:
- Open `WebSocketManager.kt`.
- Replace the `SERVER_URL` with your own endpoint (local or deployed) so it can interact with your backend server.

---

## Running the Application
1. Connect TEMI Robot (or physical Android device) to your development machine:
- On the TEMI device, open `Settings → Developer Settings`.
- Tap Open Port to enable ADB over Wi-Fi.
- In Android Studio’s Terminal, run:
```bash
adb connect <TEMI_IP_ADDRESS>
```
- <TEMI_IP_ADDRESS> will be shown on the TEMI screen after you enable Open Port.
- A successful connection message should appear.

2. Run/Build from Android Studio:
- In Android Studio, click the Run button.
- Select your TEMI device (or Android device) from the device list.
- The app will install and launch on the device.

**_NOTE:_** The app does not run on an emulator because it requires an actual microphone.

---

## Project Structure

```bash
multilingual-voice-app/
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/
│   │   │   │   └── <package-name>/
│   │   │   │       ├── MainActivity.kt
│   │   │   │       ├── WebSocketManager.kt
│   │   │   │       ├── RetrofitClient.kt
│   │   │   │       └── ... (other classes)
│   │   │   ├── res/
│   │   │   │   └── layout/ ... (XML layouts)
│   │   │   └── AndroidManifest.xml
│   └── build.gradle
└── README.md
```
Key files of interest:
- `MainActivity.kt`: Handles voice recording, TTS, and UI components.
- `WebSocketManager.kt`: Manages the WebSocket connection to the backend server.
- `RetrofitClient.kt`: Configures API calls (OpenAI for transcription & translation).

---

## Usage
1. Tap the Microphone icon (Lottie animation) to start recording.
2. Speak your query or statement in your chosen language (selected from the Spinner).
3. Once you pause or hit silence:
  - The app sends audio to OpenAI’s Whisper for transcription.
  - Translates the user speech to English (if the selected language is not English).
  - Sends the processed text via WebSocket to the backend for a response.
4. When a response arrives from the backend:
  - The app will translate it back to the user’s chosen language.
  - The text is spoken aloud via Android’s TTS engine.

---

## TEMI Integration Details
- ADB Connection: The TEMI device allows ADB over Wi-Fi. Use the IP address shown in TEMI’s Developer Settings after enabling Open Port.
- Speech: TEMI has built-in microphone and speakers, making conversation intuitive.

---

## Tips & Troubleshooting
- Ensure Microphone Permission is granted. If not, the app will request it on launch.
- If the WebSocket fails to connect, verify that:
  - The `SERVER_URL` in WebSocketManager is correct.
  - Your backend server is running and reachable over the network.

- If you see `Recording too short` or no audio data:
  - Speak clearly and ensure the environment isn’t too noisy.
- For language translations, make sure the Spinner has the correct language codes and your OpenAI API key is valid.

---

