# MultiLingual Voice Android Application 🎤🤖

## Installation 🛠️

### Prerequisites
- Android Studio (latest version recommended)
- OpenAI API key

### Steps
1. **Clone the Repository**  
   ```bash
   git clone https://github.com/temi-t8/multilingual-voice-app.git
   ```

2. **Open the Project in Android Studio**
- Launch Android Studio.
- Select Open an Existing Project and navigate to the cloned repository.

3. **Add Your OpenAI API Key**
- Open RetrofitClient.kt.
- Replace YOUR_API_KEY with your actual OpenAI API key:
`.addHeader("Authorization", "Bearer YOUR_API_KEY")`

4. **Run the Application**
- Connect an Android device or emulator.
- Click Run in Android Studio to build and deploy the app.

## Usage 🚀

### **Select a Language**
- Choose your preferred language from the dropdown menu at the top of the screen.

### **Start Recording**
- Tap the mic button to start recording your voice.
- The button will animate, and a "Recording in progress" message will appear.

### **Stop Recording**
- Tap the mic button again to stop recording.
- The app will transcribe your voice and send it to OpenAI.

### **View and Hear Responses**
- The transcribed text and AI response will appear in a chat-like interface.
- The AI's response will be spoken aloud in the selected language.

## Technologies Used 🛠️
- Kotlin: Primary programming language for Android development.
- Retrofit: For making API calls to OpenAI.
- OkHttp: For handling network requests and logging.
- TextToSpeech: For converting text responses to speech.
- Lottie: For smooth and engaging animations.
- RecyclerView: For displaying user and AI messages in a chat-like interface.

