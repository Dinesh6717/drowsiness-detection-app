package com.example.drowsinessapp

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.*
import android.location.Location
import android.media.MediaPlayer
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.drowsinessapp.model.DrowsinessResponse
import com.example.drowsinessapp.model.ImageRequest
import com.example.drowsinessapp.network.RetrofitClient
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.ByteArrayOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {

    private lateinit var cameraExecutor: ExecutorService
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var mediaPlayer: MediaPlayer
    private var alertPlaying = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        mediaPlayer = MediaPlayer.create(this, R.raw.alert_sound)
        cameraExecutor = Executors.newSingleThreadExecutor()

        val requestPermissionsLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions -> }

        requestPermissionsLauncher.launch(
            arrayOf(
                Manifest.permission.CAMERA,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        )

        setContent {
            DrowsinessApp()
        }
    }

    @Composable
    fun DrowsinessApp() {
        val context = LocalContext.current
        val lifecycleOwner = LocalLifecycleOwner.current

        var name by remember { mutableStateOf("") }
        var phone by remember { mutableStateOf("") }
        var location by remember { mutableStateOf("") }
        var showCamera by remember { mutableStateOf(false) }
        var status by remember { mutableStateOf("Status: Awake") }

        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            if (!showCamera) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { if (it.matches(Regex("^[a-zA-Z\\s]*$"))) name = it },
                    label = { Text("Name") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { if (it.matches(Regex("^[0-9]*$"))) phone = it },
                    label = { Text("Phone Number") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = location,
                    onValueChange = {
                        if (it.matches(Regex("^[a-zA-Z0-9,\\s]*$"))) location = it
                    },
                    label = { Text("Location") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                Button(onClick = {
                    if (ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.ACCESS_FINE_LOCATION
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        Toast.makeText(context, "Location permission not granted", Toast.LENGTH_SHORT).show()
                        return@Button
                    }

                    fusedLocationClient.lastLocation.addOnSuccessListener { loc: Location? ->
                        loc?.let {
                            location = "${it.latitude}, ${it.longitude}"
                        } ?: run {
                            Toast.makeText(context, "Failed to retrieve location", Toast.LENGTH_SHORT).show()
                        }
                    }
                }) {
                    Text("Autofill Live Location")
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(onClick = {
                    if (name.isBlank() || phone.isBlank() || location.isBlank()) {
                        Toast.makeText(context, "All fields are required", Toast.LENGTH_SHORT).show()
                    } else {
                        showCamera = true
                    }
                }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text("Start Streaming")
                }
            } else {
                CameraPreview { bitmap ->
                    analyzeImage(bitmap) { isDrowsy ->
                        status = if (isDrowsy) {
                            if (!alertPlaying) {
                                alertPlaying = true
                                mediaPlayer.start()
                            }
                            "Status: Drowsy"
                        } else {
                            if (alertPlaying) {
                                mediaPlayer.pause()
                                mediaPlayer.seekTo(0)
                                alertPlaying = false
                            }
                            "Status: Awake"
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = status,
                    color = if (status.contains("Drowsy")) Color.Red else Color.Green,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
        }
    }

    @Composable
    fun CameraPreview(onFrame: (Bitmap) -> Unit) {
        val context = LocalContext.current
        val lifecycleOwner = LocalLifecycleOwner.current
        Row {
            AndroidView(
                factory = { ctx ->
                    val previewView = PreviewView(ctx)
                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)

                    cameraProviderFuture.addListener({
                        val cameraProvider = cameraProviderFuture.get()

                        val preview = Preview.Builder().build().also {
                            it.setSurfaceProvider(previewView.surfaceProvider)
                        }

                        val imageAnalyzer = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()
                            .also {
                                it.setAnalyzer(cameraExecutor) { imageProxy ->
                                    val bitmap = imageProxy.toBitmap()
                                    bitmap?.let { onFrame(it) }
                                    imageProxy.close()
                                }
                            }

                        val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            cameraSelector,
                            preview,
                            imageAnalyzer
                        )
                    }, ContextCompat.getMainExecutor(ctx))

                    previewView
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )
        }
    }

    private fun analyzeImage(bitmap: Bitmap, onResult: (Boolean) -> Unit) {
        try {
            val outputStream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
            val base64Image = Base64.encodeToString(outputStream.toByteArray(), Base64.DEFAULT)

            RetrofitClient.api.detectDrowsinessFromImage(ImageRequest(base64Image))
                .enqueue(object : Callback<DrowsinessResponse> {
                    override fun onResponse(call: Call<DrowsinessResponse>, response: Response<DrowsinessResponse>) {
                        if (response.isSuccessful) {
                            val isDrowsy = response.body()?.drowsy == true
                            onResult(isDrowsy)
                        } else {
                            onResult(false)
                        }
                    }

                    override fun onFailure(call: Call<DrowsinessResponse>, t: Throwable) {
                        Log.e("API", "Error: ${t.message}")
                        onResult(false)
                    }
                })
        } catch (e: Exception) {
            Log.e("API", "Exception: ${e.message}")
            onResult(false)
        }
    }

    private fun ImageProxy.toBitmap(): Bitmap? {
        val yBuffer = planes[0].buffer
        val uBuffer = planes[1].buffer
        val vBuffer = planes[2].buffer

        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()

        val nv21 = ByteArray(ySize + uSize + vSize)
        yBuffer.get(nv21, 0, ySize)
        vBuffer.get(nv21, ySize, vSize)
        uBuffer.get(nv21, ySize + vSize, uSize)

        val yuvImage = YuvImage(nv21, ImageFormat.NV21, width, height, null)
        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, width, height), 90, out)
        val imageBytes = out.toByteArray()
        var bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)

        // Rotate and mirror for front camera
        bitmap = rotateBitmap(bitmap, imageInfo.rotationDegrees)
        bitmap = flipBitmapHorizontally(bitmap)

        return bitmap
    }

    private fun rotateBitmap(bitmap: Bitmap, rotationDegrees: Int): Bitmap {
        val matrix = Matrix()
        matrix.postRotate(rotationDegrees.toFloat())
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun flipBitmapHorizontally(bitmap: Bitmap): Bitmap {
        val matrix = Matrix()
        matrix.preScale(-1f, 1f)
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        mediaPlayer.release()
    }
}
