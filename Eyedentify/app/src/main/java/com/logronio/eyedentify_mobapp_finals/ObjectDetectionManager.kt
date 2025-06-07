package com.logronio.eyedentify_mobapp_finals

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

/**
 * A unified object detection manager that can use multiple detection strategies
 * and gracefully handle failures.
 */
class ObjectDetectionManager(private val context: Context) {

    private var detrDetector: DetrObjectDetector? = null
    private var isDetrAvailable = false

    init {
        try {
            detrDetector = DetrObjectDetector()
            isDetrAvailable = true
            Log.d(TAG, "DETR detector initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize DETR detector: ${e.message}", e)
            isDetrAvailable = false
        }
    }

    data class DetectionResult(
        val label: String,
        val confidence: Float,
        val boundingBox: RectF? = null
    )

    suspend fun detectObjects(bitmap: Bitmap, image: InputImage): List<String> {
        return try {
            // First try DETR if available
            if (isDetrAvailable && detrDetector != null) {
                Log.d(TAG, "Attempting detection with DETR")
                val results = detrDetector!!.detect(bitmap)

                if (results.isNotEmpty()) {
                    Log.d(TAG, "DETR detection successful with ${results.size} results")
                    results.map { "${it.label} (${(it.confidence * 100).toInt()}%)" }
                } else {
                    Log.d(TAG, "DETR returned no results, falling back to ML Kit")
                    fallbackToMlKit(image)
                }
            } else {
                Log.d(TAG, "DETR not available, using ML Kit")
                fallbackToMlKit(image)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in primary detection method: ${e.message}", e)
            fallbackToMlKit(image)
        }
    }

    private suspend fun fallbackToMlKit(image: InputImage): List<String> {
        Log.d(TAG, "Using ML Kit image labeling")
        return try {
            withContext(Dispatchers.IO) {
                suspendCoroutine { continuation ->
                    ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS)
                        .process(image)
                        .addOnSuccessListener { labels ->
                            continuation.resume(
                                labels.map { "${it.text} (${(it.confidence * 100).toInt()}%)" }
                            )
                        }
                        .addOnFailureListener { e ->
                            Log.e(TAG, "ML Kit labeling failed: ${e.message}", e)
                            continuation.resume(emptyList())
                        }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in ML Kit fallback: ${e.message}", e)
            emptyList()
        }
    }

    companion object {
        private const val TAG = "ObjectDetectionManager"
    }
}