package com.logronio.eyedentify_mobapp_finals

import android.graphics.Bitmap
import android.graphics.RectF
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

class DetrObjectDetector {
    private val apiService: HuggingFaceApiService

    init {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl("https://api-inference.huggingface.co/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        apiService = retrofit.create(HuggingFaceApiService::class.java)
    }

    data class DetectionResult(
        val label: String,
        val confidence: Float,
        val boundingBox: RectF
    )

    suspend fun detect(bitmap: Bitmap): List<DetectionResult> {
        return try {
            Log.d(TAG, "Starting DETR detection, converting bitmap to base64")
            val base64Image = bitmapToBase64(bitmap)
            Log.d(TAG, "Base64 conversion complete, image size: ${base64Image.length} characters")

            Log.d(TAG, "Making API call to Hugging Face")
            val response = withContext(Dispatchers.IO) {
                try {
                    apiService.detectObjects(
                        apiKey = "Bearer " + HF_API_KEY,
                        request = ImageRequest(base64Image)
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "API call failed", e)
                    throw e
                }
            }
            Log.d(TAG, "API call successful, received ${response.size} detections")

            // Process the response
            response.map { detection ->
                Log.d(TAG, "Detected: ${detection.label} with confidence ${detection.score}")
                DetectionResult(
                    label = detection.label,
                    confidence = detection.score,
                    boundingBox = RectF(
                        detection.box.xmin.toFloat(),
                        detection.box.ymin.toFloat(),
                        detection.box.xmax.toFloat(),
                        detection.box.ymax.toFloat()
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during DETR detection: ${e.message}", e)
            emptyList()
        }
    }

    private fun bitmapToBase64(bitmap: Bitmap): String {
        val byteArrayOutputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, byteArrayOutputStream)
        val byteArray = byteArrayOutputStream.toByteArray()
        return Base64.encodeToString(byteArray, Base64.DEFAULT)
    }

    companion object {
        private const val TAG = "DetrObjectDetector"
        private const val HF_API_KEY = "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"
    }
}

interface HuggingFaceApiService {
    @POST("models/facebook/detr-resnet-50")
    suspend fun detectObjects(
        @Header("Authorization") apiKey: String,
        @Body request: ImageRequest
    ): List<DetectionResponse>
}

data class ImageRequest(
    val data: String
)

data class DetectionResponse(
    val score: Float,
    val label: String,
    val box: BoundingBox
)

data class BoundingBox(
    val xmin: Double,
    val ymin: Double,
    val xmax: Double,
    val ymax: Double
)