package com.logronio.eyedentify_mobapp_finals

import android.graphics.ImageDecoder
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.speech.tts.TextToSpeech
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.logronio.eyedentify_mobapp_finals.databinding.ActivityResultsBinding
import kotlinx.coroutines.*
import java.util.*
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

class ResultsActivity : AppCompatActivity(), TextToSpeech.OnInitListener {
    private lateinit var viewBinding: ActivityResultsBinding
    private var textToSpeech: TextToSpeech? = null
    private lateinit var scanRepository: ScanRepository // Changed from database to repository
    private var resultText: String = ""
    private var processingJob: Job? = null
    private lateinit var detrDetector: DetrObjectDetector

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        overridePendingTransition(0, 0)
        viewBinding = ActivityResultsBinding.inflate(layoutInflater)
        setContentView(viewBinding.root)

        initializeComponents()
        setupUI()
        processImageFromIntent()
    }

    private fun initializeComponents() {
        scanRepository = ScanRepository() // Initialize Firebase repository
        textToSpeech = TextToSpeech(this, this)

        try {
            Log.d(TAG, "Initializing DETR object detector...")
            detrDetector = DetrObjectDetector()
            Log.d(TAG, "Successfully initialized DETR object detector")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize DETR object detector: ${e.message}", e)
            Toast.makeText(
                this,
                "Error initializing object detection. Will try to continue with ML Kit.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun setupUI() {
        viewBinding.backButton.setOnClickListener { onBackPressed() }
    }

    private fun processImageFromIntent() {
        intent.getStringExtra(EXTRA_IMAGE_URI)?.let { uri ->
            try {
                viewBinding.capturedImageView.setImageURI(uri.toUri())
                processImage(uri)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load image", e)
                showError("Failed to load image")
            }
        }
    }

    private fun processImage(imageUri: String) {
        Log.d(TAG, "Starting to process image: $imageUri")
        processingJob = lifecycleScope.launch {
            try {
                Log.d(TAG, "Beginning image processing workflow")

                withContext(Dispatchers.IO) {
                    try {
                        Log.d(TAG, "Creating InputImage from file path")
                        val image = InputImage.fromFilePath(this@ResultsActivity, imageUri.toUri())
                        Log.d(TAG, "Successfully created InputImage")

                        Log.d(TAG, "Loading bitmap from URI")
                        val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                            ImageDecoder.decodeBitmap(
                                ImageDecoder.createSource(contentResolver, imageUri.toUri())
                            )
                        } else {
                            @Suppress("DEPRECATION")
                            MediaStore.Images.Media.getBitmap(contentResolver, imageUri.toUri())
                        }
                        Log.d(TAG, "Successfully loaded bitmap: ${bitmap.width}x${bitmap.height}")

                        val (objects, text) = coroutineScope {
                            Log.d(TAG, "DETR initialized: ${::detrDetector.isInitialized}")

                            val objectsDeferred = async {
                                try {
                                    if (::detrDetector.isInitialized) {
                                        Log.d(TAG, "Attempting DETR object detection")
                                        val detections = detrDetector.detect(bitmap)
                                        Log.d(TAG, "DETR detection completed with ${detections.size} objects")

                                        if (detections.isEmpty()) {
                                            Log.d(TAG, "No objects detected with DETR, falling back to ML Kit")
                                            fallbackToMlKit(image)
                                        } else {
                                            detections.map {
                                                "${it.label} (${(it.confidence * 100).toInt()}%)"
                                            }
                                        }
                                    } else {
                                        Log.d(TAG, "DETR detector not initialized, using ML Kit")
                                        fallbackToMlKit(image)
                                    }
                                } catch (e: Exception) {
                                    Log.e(TAG, "Object detection failed with exception", e)
                                    Log.d(TAG, "Falling back to ML Kit due to error")
                                    try {
                                        fallbackToMlKit(image)
                                    } catch (mlKitEx: Exception) {
                                        Log.e(TAG, "ML Kit fallback also failed", mlKitEx)
                                        listOf("Error: Failed to detect objects")
                                    }
                                }
                            }

                            val textDeferred = async {
                                try {
                                    Log.d(TAG, "Starting text detection with ML Kit")
                                    val result = detectText(image)
                                    Log.d(TAG, "Text detection completed: ${result?.text?.take(50)}")
                                    result
                                } catch (e: Exception) {
                                    Log.e(TAG, "Text detection failed with exception", e)
                                    null
                                }
                            }

                            Pair(objectsDeferred.await(), textDeferred.await())
                        }

                        Log.d(TAG, "Processing results and updating UI")
                        val objectsText = formatObjectsText(objects)
                        val recognizedText = text?.text?.takeIf { it.isNotEmpty() } ?: "No text detected"
                        resultText = "$objectsText. $recognizedText"

                        withContext(Dispatchers.Main) {
                            Log.d(TAG, "Updating UI with detection results")
                            updateUI(objectsText, recognizedText)
                            saveToFirestore(imageUri, recognizedText, objects) // Changed method name
                            speakResults()
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Inner processing error", e)
                        throw e
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing image", e)
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    val errorMsg = "Error: ${e.javaClass.simpleName} - ${e.message}"
                    Log.e(TAG, errorMsg)
                    showError(errorMsg)

                    delay(2000)
                    onBackPressed()
                }
            }
        }
    }

    private suspend fun fallbackToMlKit(image: InputImage): List<String> {
        Log.d(TAG, "Starting ML Kit image labeling as fallback")
        return suspendCoroutine { continuation ->
            try {
                val options = com.google.mlkit.vision.label.defaults.ImageLabelerOptions.Builder()
                    .setConfidenceThreshold(0.7f)
                    .build()

                val labeler = com.google.mlkit.vision.label.ImageLabeling.getClient(options)

                Log.d(TAG, "ML Kit labeler created, starting processing")

                labeler.process(image)
                    .addOnSuccessListener { labels ->
                        Log.d(TAG, "ML Kit labeling successful, found ${labels.size} labels")
                        val result = labels.map { "${it.text} (${(it.confidence * 100).toInt()}%)" }
                        continuation.resume(result)

                        labeler.close()
                    }
                    .addOnFailureListener { exception ->
                        Log.e(TAG, "ML Kit labeling failed", exception)
                        continuation.resumeWithException(exception)

                        labeler.close()
                    }
            } catch (e: Exception) {
                Log.e(TAG, "Error creating ML Kit labeler", e)
                continuation.resumeWithException(e)
            }
        }
    }

    private fun formatObjectsText(objects: List<String>) =
        if (objects.isNotEmpty()) "Objects found: ${objects.joinToString(", ")}"
        else "No objects detected"

    private fun updateUI(objectsText: String, recognizedText: String) {
        with(viewBinding) {
            objectResultText.text = objectsText
            textResultText.text = "Text found: $recognizedText"
        }
    }

    // Updated method to save to Firebase instead of Room
    private suspend fun saveToFirestore(
        imageUri: String,
        recognizedText: String,
        objects: List<String>
    ) {
        try {
            withContext(Dispatchers.IO) {
                val scanData = ScanDatabase(
                    id = "", // Firebase will generate the ID
                    imageUri = imageUri,
                    detectedText = recognizedText,
                    detectedObjects = objects,
                    timestamp = Date()
                )

                val documentId = scanRepository.insert(scanData)
                Log.d(TAG, "Successfully saved to Firebase with ID: $documentId")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error saving to Firebase", e)
            withContext(Dispatchers.Main) {
                Toast.makeText(this@ResultsActivity, "Failed to save scan data", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun speakResults() {
        textToSpeech?.speak(resultText, TextToSpeech.QUEUE_FLUSH, null, "utteranceId")
    }

    private suspend fun detectText(image: InputImage) = suspendCoroutine { continuation ->
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            .process(image)
            .addOnSuccessListener { continuation.resume(it) }
            .addOnFailureListener { continuation.resumeWithException(it) }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = textToSpeech?.setLanguage(Locale.getDefault())
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e(TAG, "Language not supported")
            }
        } else {
            Log.e(TAG, "TTS Initialization failed")
        }
    }

    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun onBackPressed() {
        textToSpeech?.stop()
        finish()
        overridePendingTransition(0, 0)
    }

    override fun onDestroy() {
        processingJob?.cancel()
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_IMAGE_URI = "extra_image_uri"
        private const val TAG = "ResultsActivity"
    }
}