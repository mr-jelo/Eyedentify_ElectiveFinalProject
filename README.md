# Eyedentify 📱

> A mobile application developed as a student project, designed for real-time image capture, advanced object detection, and text recognition, with results stored persistently and audibly presented.

## Table of Contents
- [About the Project](#about-the-project)
- [Features](#features)
- [Development Process Highlights](#development-process-highlights)
- [Technologies Used](#technologies-used)
- [Project Structure & Key Components](#project-structure--key-components)
- [Installation for Development](#installation-for-development)
- [Usage Workflow](#usage-workflow)
- [Future Enhancements](#future-enhancements)

## About the Project
Eyedentify is an Android mobile application created as a final project. Its core functionality revolves around visual analysis: taking pictures with the device camera, then processing these images to identify objects and extract text. All analysis results are stored in a cloud database (Firebase Firestore) for historical tracking and can be reviewed in an organized history view. A unique aspect of the app is its ability to vocalize the detected objects and text using Text-to-Speech, aiming to provide an accessible and interactive experience. This project demonstrates integration of camera APIs, cloud services, and external machine learning inference.

## Features
✨ Key functionalities developed within the Eyedentify app:
-   **Real-time Camera Preview & Image Capture:** Utilizes CameraX API for a seamless live camera feed and high-quality photo capture.
-   **Optical Character Recognition (OCR):** Employs **Google ML Kit Text Recognition** to accurately detect and extract text from captured images.
-   **Advanced Object Detection:** Integrates with the **Hugging Face Inference API** to leverage a sophisticated, cloud-based **DETR-ResNet-50** model for identifying various objects within images. Includes a fallback to ML Kit's image labeling if DETR detection is inconclusive or unavailable.
-   **Scan History & Persistence:** All captured images (via URI) and their corresponding analysis results (detected text and objects) are securely saved to **Google Firebase Firestore**.
-   **Interactive History View:** Provides a dedicated section to browse a chronological list of all past scans, displaying summarized results and allowing full-image previews.
-   **Text-to-Speech Output:** Narrates the detected objects and recognized text aloud, enhancing accessibility and user interaction.
-   **Robust Permissions Handling:** Manages Android camera permissions to ensure smooth operation.
-   **Firebase Integration & Testing:** Includes built-in test functionalities for verifying Firebase connectivity and data operations.

## Development Process Highlights
This project showcases the integration of several modern Android development patterns and third-party services:
-   **Asynchronous Operations:** Extensive use of **Kotlin Coroutines** and `lifecycleScope` for managing background tasks, especially during image processing and database interactions, ensuring a responsive UI.
-   **Cloud Database:** Implementation of **Firebase Firestore** for scalable and real-time data storage, including custom repository pattern for data access.
-   **External ML Inference:** A key learning point involved connecting to an external Machine Learning API (Hugging Face) for state-of-the-art object detection, demonstrating client-server interaction for ML.
-   **Local Image Handling:** Efficient handling of captured image URIs and loading bitmaps for display and processing across different Android versions.

## Technologies Used
🛠️ A comprehensive list of the primary technologies, frameworks, and libraries used in this project:

-   **Development Language:** Kotlin
-   **Platform:** Android (API Level 21+)
-   **UI Toolkit:** Android XML Layouts
-   **Camera API:** AndroidX CameraX (Preview, ImageCapture)
-   **Backend & Database:** Google Firebase Firestore
-   **Machine Learning (On-Device):**
    -   Google ML Kit Text Recognition (for OCR)
    -   Google ML Kit Image Labeling (as a fallback for object detection)
-   **Machine Learning (Cloud Inference):**
    -   Hugging Face Inference API
    -   Model Used: `facebook/detr-resnet-50` for object detection
-   **Networking:**
    -   Retrofit (Type-safe HTTP client for API calls)
    -   OkHttp (HTTP client, used by Retrofit)
    -   OkHttp Logging Interceptor (for network request logging)
    -   Gson (JSON Serialization/Deserialization)
-   **Concurrency:** Kotlin Coroutines & Lifecycle-aware Coroutine Scopes
-   **Text-to-Speech (TTS):** Android's built-in `TextToSpeech` API
-   **Utility Libraries:** AndroidX Core, AppCompat, RecyclerView, DiffUtil.

## Project Structure & Key Components

-   `MainActivity.kt`: Manages the camera preview, image capture, UI interaction (scan, history buttons), and basic Firebase initialization/testing.
-   `ResultsActivity.kt`: The central activity for processing captured images. It orchestrates text recognition, object detection (via `DetrObjectDetector`), updates the UI with results, saves data to Firebase, and handles Text-to-Speech.
-   `DetrObjectDetector.kt`: Contains the logic for integrating with the Hugging Face Inference API for DETR-based object detection. Handles image Base64 encoding and API communication.
-   `ScanRepository.kt`: Implements the repository pattern for all CRUD (Create, Read, Update, Delete) operations with Firebase Firestore for `ScanDatabase` objects, including real-time data observation using Kotlin Flows.
-   `ScanDatabase.kt`: A data class representing the structure of a single scan result, including image URI, detected text, detected objects, and a timestamp.
-   `ScanHistoryAdapter.kt`: An `RecyclerView.Adapter` responsible for displaying the list of `ScanDatabase` objects in the history view, including loading thumbnails and formatting results.

## Installation for Development
To set up and run the Eyedentify project in your Android Studio environment:

1.  **Clone the repository:**
    ```bash
    git clone [https://github.com/mr-jelo/Eyedentify_ElectiveFinalProject.git](https://github.com/mr-jelo/Eyedentify_ElectiveFinalProject.git)
    ```
2.  **Open in Android Studio:**
    * Launch Android Studio.
    * Select `File` > `Open` and navigate to the cloned project directory (`Eyedentify_ElectiveFinalProject`).
3.  **Firebase Project Setup:**
    * This project relies on Firebase Firestore for backend data storage. You need to create your own Firebase project in the Firebase console.
    * Follow the official Firebase documentation to [Add Firebase to your Android project](https://firebase.google.com/docs/android/setup).
    * Download your `google-services.json` file from your Firebase project settings and place it into the `app/` directory of this Android Studio project.
4.  **Hugging Face API Key:**
    * The object detection feature uses the Hugging Face Inference API. You will need a Hugging Face API token.
    * Go to [Hugging Face Settings](https://huggingface.co/settings/tokens) and generate a new token.
    * **Important Security Note:** For this student project, the API key is hardcoded in `DetrObjectDetector.kt` as `HF_API_KEY`. For any production application, it's highly recommended to secure API keys by using environment variables, build configurations, or a backend proxy.
5.  **Sync Gradle:**
    * Allow Android Studio to sync the Gradle files. If prompted, update Gradle to the latest version.
6.  **Run the Application:**
    * Connect an Android physical device via USB (ensure USB debugging is enabled) or start an Android Emulator.
    * Click the `Run` button (green play icon) in Android Studio to build, deploy, and launch the application.

## Usage Workflow
A typical user interaction flow for the Eyedentify app:

1.  **Initial Launch & Permissions:** Upon the first launch, the app will request camera permissions. Grant these permissions to proceed.
2.  **Camera View:** The main screen displays a live camera preview.
3.  **Capture Image:** Tap the "Scan" button (the primary capture button) to take a photo.
4.  **Processing & Results:** The captured image is sent to `ResultsActivity` for analysis. During this time, the app performs:
    * Text Recognition (OCR) using ML Kit.
    * Object Detection using the Hugging Face DETR model (with ML Kit image labeling as fallback).
    * The detected text and objects are displayed on the `ResultsActivity` screen.
    * The app will also speak the detected text and objects using Text-to-Speech.
5.  **Save & Return:** The analysis results are automatically saved to your Firebase Firestore database. After reviewing, you can use the back button to return to the camera view.
6.  **View History:** Tap the "History" button (typically in the top right or a drawer icon) to open the history drawer. This lists all your past scans with a summary.
7.  **Review Past Scans:** Tap on any item in the history list to view the full captured image in a dedicated dialog.
8.  **Clear History:** Within the history drawer, there's an option to clear all stored scan history from Firebase.
