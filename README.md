# Tome - Your On-Device AI Chat Companion

Tome is a 100% offline, private, and secure Android chatbot application. It runs a powerful large language model (LLM) directly on your device, meaning none of your data ever leaves your phone.

Built with the latest Android technologies, this app is a showcase of modern, on-device AI capabilities.

## ✨ Features

*   **Offline-First AI:** Chat with an AI assistant anytime, anywhere, without needing an internet connection.
*   **Privacy-Focused:** All conversations are stored locally and no data is sent to the cloud.
*   **Flexible Model Support:**
    *   Download the recommended default model with a single tap.
    *   Load your own custom, compatible `.task` model file from your device.
*   **Conversation Management:** Create, switch between, and delete multiple chat sessions.
*   **Modern UI:**
    *   A sleek, intuitive interface built with Jetpack Compose.
    *   Supports both Light and Dark themes.
    *   Renders AI responses in Markdown for rich formatting.
*   **Persistent History:** Your conversations and settings are saved locally for you to pick up right where you left off.

## 🛠️ How It's Built

Tome is built on a modern Android tech stack:

*   **Language:** 100% [Kotlin](https://kotlinlang.org/)
*   **UI:** [Jetpack Compose](https://developer.android.com/jetpack/compose) for declarative and reactive UI development.
*   **On-Device AI:** [Google MediaPipe's `LlmInference` task](https://developers.google.com/mediapipe/solutions/genai/llm_inference/android) for running the language model efficiently on-device.
*   **Concurrency:** [Kotlin Coroutines](https://kotlinlang.org/docs/coroutines-overview.html) for managing background tasks like AI inference.
*   **Data Persistence:** [SharedPreferences](https://developer.android.com/training/data-storage/shared-preferences) with a `DataRepository` pattern to save app state.
*   **Serialization:** [Gson](https://github.com/google/gson) for converting conversation data to and from JSON.

## 🚀 Getting Started

1.  **Clone the repository:**
    ```bash
    git clone <repository-url>
    ```
2.  **Open in Android Studio:** Open the cloned directory in the latest version of Android Studio.
3.  **Build & Run:** Build the project and run it on an Android emulator or a physical device.
4.  **Download a Model:** On first launch, navigate to **Settings** and download the default model to begin chatting.

