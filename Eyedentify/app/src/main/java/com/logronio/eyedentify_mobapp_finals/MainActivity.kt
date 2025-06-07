package com.logronio.eyedentify_mobapp_finals

import android.Manifest
import android.app.Dialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.graphics.ImageDecoder
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.view.GravityCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.FirebaseApp
import com.logronio.eyedentify_mobapp_finals.databinding.ActivityMainBinding
import com.logronio.eyedentify_mobapp_finals.databinding.DialogFullImageBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import com.google.firebase.firestore.FirebaseFirestore

class MainActivity : AppCompatActivity() {
    private lateinit var viewBinding: ActivityMainBinding
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var historyAdapter: ScanHistoryAdapter

    // Firebase components
    private lateinit var firebaseDb: FirebaseFirestore
    private lateinit var scanFirebaseRepository: ScanRepository

    private var imageCapture: ImageCapture? = null
    private var camera: Camera? = null
    private var preview: Preview? = null
    private var isScanning = false

    private val requiredPermissions = arrayOf(Manifest.permission.CAMERA)

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.all { it.value }) {
            startCameraPreview()
        } else {
            showPermissionDeniedDialog()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewBinding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(viewBinding.root)

        // 1. Initialize Firebase components FIRST!
        FirebaseApp.initializeApp(this) // Although often auto-initialized, explicit call here is fine
        initializeFirebase() // <-- Moved this up! Now scanFirebaseRepository is initialized.

        // 2. Initialize other components
        initializeComponents()

        // 3. Now setup the UI and start the camera
        setupUI() // <-- Called after repository is initialized. observeScanHistory will work!
        checkPermissionsAndStartCamera()

        // 4. Optional tests (can be called after everything is set up)
        testFirebaseConnection()
    }


    private fun initializeComponents() {
        cameraExecutor = Executors.newSingleThreadExecutor()
        historyAdapter = ScanHistoryAdapter { scanResult ->
            showFullImage(scanResult)
        }
    }

    private fun initializeFirebase() {
        firebaseDb = FirebaseFirestore.getInstance()
        scanFirebaseRepository = ScanRepository()
        Log.d(TAG, "Firebase initialized successfully")
    }

    private fun testFirebaseConnection() {
        testBasicFirebaseWrite()
        // Uncomment for additional tests:
        // addSampleScanData()
        // testFirebaseRead()
    }

    private fun testBasicFirebaseWrite() {
        val testData = mapOf(
            "test" to "Hello Firebase from MainActivity!",
            "timestamp" to Date(),
            "app_version" to "1.0"
        )

        firebaseDb.collection("connection_test")
            .add(testData)
            .addOnSuccessListener { documentReference ->
                Log.d(TAG, "✅ Firebase connection test successful! Document ID: ${documentReference.id}")
                runOnUiThread {
                    Toast.makeText(this, "Firebase connected successfully!", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "❌ Firebase connection test failed", e)
                runOnUiThread {
                    Toast.makeText(this, "Firebase connection failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
    }

    private fun addSampleScanData() {
        lifecycleScope.launch {
            try {
                val sampleScan = ScanDatabase(
                    imageUri = "content://sample_test_image.jpg",
                    detectedText = "This is a test scan from MainActivity",
                    detectedObjects = listOf("phone", "laptop", "cup", "book"),
                    timestamp = Date()
                )
                scanFirebaseRepository.insert(sampleScan)
                Log.d(TAG, "✅ Sample scan data added to Firebase successfully")
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Sample data added to Firebase!", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error adding sample scan data", e)
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Error adding sample data: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun testFirebaseRead() {
        lifecycleScope.launch {
            try {
                scanFirebaseRepository.getAllScans().collect { scanResults ->
                    Log.d(TAG, "📖 Firebase scan results count: ${scanResults.size}")
                    scanResults.forEach { scan ->
                        Log.d(TAG, "Scan: ${scan.detectedText} - Objects: ${scan.detectedObjects}")
                    }
                    if (scanResults.isNotEmpty()) {
                        runOnUiThread {
                            Toast.makeText(this@MainActivity, "Found ${scanResults.size} scans in Firebase", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error reading Firebase data", e)
            }
        }
    }

    private fun setupUI() {
        with(viewBinding) {
            historyRecyclerView.apply {
                layoutManager = LinearLayoutManager(this@MainActivity)
                adapter = historyAdapter
                setHasFixedSize(true)
            }

            historyButton.setOnClickListener {
                loadHistoryAndOpenDrawer()
            }

            scanButton.setOnClickListener {
                if (!isScanning) takePictureAndAnalyze()
            }

            clearHistoryButton.setOnClickListener {
                showClearHistoryConfirmation()
            }

            scanButton.setOnLongClickListener {
                showFirebaseTestDialog()
                true
            }
        }
        observeScanHistory()
    }

    private fun showFirebaseTestDialog() {
        AlertDialog.Builder(this)
            .setTitle("Firebase Testing")
            .setMessage("Choose a Firebase test to run:")
            .setPositiveButton("Add Sample Data") { _, _ -> addSampleScanData() }
            .setNeutralButton("Test Connection") { _, _ -> testBasicFirebaseWrite() }
            .setNegativeButton("Read Data") { _, _ -> testFirebaseRead() }
            .show()
    }

    private fun observeScanHistory() {
        lifecycleScope.launch {
            scanFirebaseRepository.getAllScans()
                .catch { e ->
                    Log.e(TAG, "Error loading scan history", e)
                    Toast.makeText(this@MainActivity, "Error loading history from Firebase", Toast.LENGTH_SHORT).show()
                }
                .collect { results ->
                    historyAdapter.submitList(results)
                }
        }
    }

    private fun loadHistoryAndOpenDrawer() {
        with(viewBinding) {
            historyLoadingView.visibility = View.VISIBLE
            drawerLayout.openDrawer(GravityCompat.END)
            lifecycleScope.launch {
                scanFirebaseRepository.getAllScans()
                    .catch { e ->
                        Log.e(TAG, "Error loading history", e)
                        historyLoadingView.visibility = View.GONE
                        Toast.makeText(this@MainActivity, "Error loading history from Firebase", Toast.LENGTH_SHORT).show()
                    }
                    .collect { results ->
                        historyAdapter.submitList(results)
                        historyLoadingView.visibility = View.GONE
                    }
            }
        }
    }

    private fun showClearHistoryConfirmation() {
        AlertDialog.Builder(this)
            .setTitle("Clear History")
            .setMessage("Are you sure you want to clear all scan history from Firebase?")
            .setPositiveButton("Clear") { _, _ -> clearHistory() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun clearHistory() {
        lifecycleScope.launch {
            try {
                scanFirebaseRepository.deleteAll()
                Toast.makeText(this@MainActivity, "History cleared from Firebase", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Log.e(TAG, "Error clearing history", e)
                Toast.makeText(this@MainActivity, "Error clearing history: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showFullImage(scanResult: ScanDatabase) {
        val dialog = Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        val binding = DialogFullImageBinding.inflate(layoutInflater)
        dialog.setContentView(binding.root)

        lifecycleScope.launch {
            try {
                val bitmap = withContext(Dispatchers.IO) {
                    val uri = scanResult.imageUri.toUri()
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        ImageDecoder.decodeBitmap(ImageDecoder.createSource(contentResolver, uri))
                    } else {
                        @Suppress("DEPRECATION")
                        MediaStore.Images.Media.getBitmap(contentResolver, uri)
                    }
                }
                binding.fullImageView.setImageBitmap(bitmap)
            } catch (e: Exception) {
                Log.e(TAG, "Error loading full image", e)
                Toast.makeText(this@MainActivity, "Error loading image", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
        }

        binding.closeButton.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun checkPermissionsAndStartCamera() {
        if (hasPermissions()) {
            startCameraPreview()
        } else {
            requestPermissions()
        }
    }

    private fun hasPermissions(): Boolean {
        return requiredPermissions.all { permission ->
            ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestPermissions() {
        permissionLauncher.launch(requiredPermissions)
    }

    private fun showPermissionDeniedDialog() {
        AlertDialog.Builder(this)
            .setTitle("Camera Permission Required")
            .setMessage("This app requires camera access to function. Please grant the permission.")
            .setPositiveButton("OK") { _, _ -> requestPermissions() }
            .setNegativeButton("Exit") { _, _ -> finish() }
            .setCancelable(false)
            .show()
    }

    private fun startCameraPreview() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            preview = Preview.Builder().build().also {
                it.setSurfaceProvider(viewBinding.previewView.surfaceProvider)
            }

            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                camera = cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageCapture
                )
                Log.d(TAG, "Camera preview started")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start camera preview", e)
                Toast.makeText(this, "Error starting camera: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun takePictureAndAnalyze() {
        val imageCapture = imageCapture ?: return
        isScanning = true
        viewBinding.scanButton.isEnabled = false

        val photoFile = createFile(getOutputDirectory(), FILENAME_FORMAT, PHOTO_EXTENSION)
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        imageCapture.takePicture(
            outputOptions,
            cameraExecutor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    Log.e(TAG, "Photo capture failed: ${exc.message}", exc)
                    runOnUiThread {
                        Toast.makeText(this@MainActivity, "Failed to capture image", Toast.LENGTH_SHORT).show()
                        isScanning = false
                        viewBinding.scanButton.isEnabled = true
                    }
                }

                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    val savedUri = output.savedUri ?: Uri.fromFile(photoFile)
                    Log.d(TAG, "Photo capture succeeded: $savedUri")
                    runOnUiThread {
                        Toast.makeText(this@MainActivity, "Image captured. Processing...", Toast.LENGTH_SHORT).show()
                    }
                    // Simulate image analysis - Replace with actual analysis
                    analyzeImageAndSaveResult(savedUri.toString())
                }
            }
        )
    }

    private fun analyzeImageAndSaveResult(imageUri: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Simulate detected text and objects
                val detectedText = "Sample detected text from image"
                val detectedObjects = listOf("object1", "object2", "object3")

                val scanData = ScanDatabase(
                    imageUri = imageUri,
                    detectedText = detectedText,
                    detectedObjects = detectedObjects,
                    timestamp = Date()
                )

                scanFirebaseRepository.insert(scanData)
                Log.d(TAG, "Scan saved to Firebase: $scanData")

                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Scan saved!", Toast.LENGTH_SHORT).show()
                    isScanning = false
                    viewBinding.scanButton.isEnabled = true
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save scan to Firebase", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Failed to save scan: ${e.message}", Toast.LENGTH_LONG).show()
                    isScanning = false
                    viewBinding.scanButton.isEnabled = true
                }
            }
        }
    }

    private fun getOutputDirectory(): File {
        val mediaDir = externalMediaDirs.firstOrNull()?.let {
            File(it, resources.getString(R.string.app_name)).apply { mkdirs() }
        }
        return if (mediaDir != null && mediaDir.exists()) mediaDir else filesDir
    }

    private fun createFile(baseFolder: File, format: String, extension: String) =
        File(baseFolder, SimpleDateFormat(format, Locale.US).format(System.currentTimeMillis()) + extension)

    companion object {
        private const val TAG = "MainActivity"
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
        private const val PHOTO_EXTENSION = ".jpg"
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}
