package com.zeusinstitute.gpspic

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.location.LocationRequest
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import java.util.Locale
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.location.LocationRequestCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
    private var imageCapture: ImageCapture? = null
    private lateinit var outputDirectory: File
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var cameraCaptureButton: ImageButton
    private lateinit var flashButton: ImageButton
    private lateinit var cameraSwitchButton: ImageButton

    private lateinit var tvLocationHeader: TextView
    private lateinit var tvDateTime: TextView
    private lateinit var tvCoordinates: TextView
    private lateinit var tvFullAddress: TextView
    private lateinit var ivMapPreview: ImageView


    private lateinit var locationManager: LocationManager
    private lateinit var locationListener: LocationListener

    private var cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

    private enum class FlashMode {
        OFF, ON, AUTO
    }

    private var flashMode = FlashMode.OFF

    private val permissions = arrayOf(
        Manifest.permission.CAMERA,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )
    private var permissionIndex = 0

    private val requestPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted: Boolean ->
            if (isGranted) {
                handlePermissionGranted()
            } else {
                showPermissionRationaleDialog()
            }
        }

    private fun handlePermissionGranted() {
        permissionIndex++
        if (permissionIndex < permissions.size) {
            requestNextPermission()
        } else {
            startCamera()
            startLocationUpdates()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        flashButton = findViewById(R.id.flash_button)
        cameraSwitchButton = findViewById(R.id.camera_switch_button)
        cameraCaptureButton = findViewById(R.id.camera_capture_button)

        // Initialize location overlay views
        tvLocationHeader = findViewById(R.id.tvLocationHeader)
        tvDateTime = findViewById(R.id.tvDateTime)
        tvCoordinates = findViewById(R.id.tvCoordinates)
        tvFullAddress = findViewById(R.id.tvFullAddress)
        ivMapPreview = findViewById(R.id.ivMapPreview)

        cameraCaptureButton.setOnClickListener { takePhoto() }
        flashButton.setOnClickListener { toggleFlash() }
        cameraSwitchButton.setOnClickListener { toggleCamera() }
        outputDirectory = getOutputDirectory()
        cameraExecutor = Executors.newSingleThreadExecutor()

        requestNextPermission()

        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
    }


    private fun toggleFlash() {
        flashMode = when (flashMode) {
            FlashMode.OFF -> FlashMode.ON
            FlashMode.ON -> FlashMode.AUTO
            FlashMode.AUTO -> FlashMode.OFF
        }
        updateFlashIcon()
        startCamera() // Restart camera to apply new flash settings
    }


    private fun updateFlashIcon() {
        flashButton.setImageResource(
            when (flashMode) {
                FlashMode.ON -> R.drawable.ic_flash_on
                FlashMode.AUTO -> R.drawable.ic_flash_auto
                FlashMode.OFF -> R.drawable.ic_flash_off
            }
        )
    }

    private fun toggleCamera() {
        cameraSelector = if (cameraSelector == CameraSelector.DEFAULT_BACK_CAMERA) {
            CameraSelector.DEFAULT_FRONT_CAMERA
        } else {
            CameraSelector.DEFAULT_BACK_CAMERA
        }
        startCamera()
    }

    private fun requestNextPermission() {
        if (permissionIndex < permissions.size) {
            requestPermissionLauncher.launch(permissions[permissionIndex])
        }
    }

    private fun takePhoto() {
        val imageCapture = imageCapture ?: return
        val photoFile = File(
            outputDirectory,
            SimpleDateFormat(FILENAME_FORMAT, Locale.ENGLISH).format(System.currentTimeMillis()) + ".jpg"
        )
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    Toast.makeText(baseContext, "Photo capture failed: ${exc.message}", Toast.LENGTH_SHORT).show()
                }

                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    val savedUri = Uri.fromFile(photoFile)
                    val msg = "Photo capture succeeded: $savedUri"
                    Toast.makeText(baseContext, msg, Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    private fun startCamera() {
        val viewFinder = findViewById<PreviewView>(R.id.viewFinder)
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(viewFinder.surfaceProvider)
            }

            imageCapture = ImageCapture.Builder()
                .setFlashMode(
                    when (flashMode) {
                        FlashMode.ON -> ImageCapture.FLASH_MODE_ON
                        FlashMode.AUTO -> ImageCapture.FLASH_MODE_AUTO
                        FlashMode.OFF -> ImageCapture.FLASH_MODE_OFF
                    }
                )
                .build()

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageCapture
                )
            } catch (exc: Exception) {
                Toast.makeText(this, "Failed to start camera: ${exc.message}", Toast.LENGTH_SHORT).show()
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return // Handle permission request if needed
        }


        val isGpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        val isNetworkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)

        val locationFlow = callbackFlow {
            locationListener = LocationListener { location ->
                trySend(location)
            }

            Log.d("Location", "Requesting location updates")
            if (isGpsEnabled) {
                locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    10000, // 10 seconds
                    10f, // 10 meters
                    locationListener
                )
            }

            if (isNetworkEnabled) {
                locationManager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    10000, // 10 seconds
                    10f, // 10 meters
                    locationListener
                )
            }

            awaitClose {
                Log.d("Location", "Stopping location updates")
                locationManager.removeUpdates(locationListener)
            }
        }

        lifecycleScope.launch {
            locationFlow.collect { location ->
                Log.d("Location", "onLocationChanged: $location")
                updateLocationInfo(location)
            }
        }
    }

        private fun updateLocationInfo(location: Location) {
        val geocoder = Geocoder(this, Locale.getDefault())
        try {
            val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)
            if (addresses?.isNotEmpty() == true) {
                val address = addresses[0]
                tvLocationHeader.visibility = View.VISIBLE
                tvDateTime.visibility = View.VISIBLE
                tvCoordinates.visibility = View.VISIBLE
                tvFullAddress.visibility = View.VISIBLE
                ivMapPreview.visibility = View.VISIBLE

                tvLocationHeader.text = "${address.locality}, ${address.adminArea}, ${address.countryName}"
                Log.d("Location", "Address: ${address.getAddressLine(0)}")
                tvDateTime.text = SimpleDateFormat("EEEE, MMMM d, yyyy h:mm a", Locale.getDefault()).format(Date())
                Log.d("Location", "Date: ${tvDateTime.text}")
                tvCoordinates.text = "Lat: ${location.latitude}, Long: ${location.longitude}"
                Log.d("Location", "Coordinates: ${tvCoordinates.text}")
                tvFullAddress.text = address.getAddressLine(0)
                Log.d("Location", "Full Address: ${tvFullAddress.text}")

                // For the map preview, you would typically use a mapping service API
                // Here, we're just setting a placeholder
                ivMapPreview.setImageResource(R.drawable.map_placeholder)
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Error getting location details", Toast.LENGTH_SHORT).show()
        }
    }

    private fun getOutputDirectory(): File {
        val mediaDir = externalMediaDirs.firstOrNull()?.let {
            File(it, resources.getString(R.string.app_name)).apply { mkdirs() }
        }
        return if (mediaDir != null && mediaDir.exists())
            mediaDir else filesDir
    }

    private fun showPermissionRationaleDialog() {
        AlertDialog.Builder(this)
            .setTitle("Permission Required")
            .setMessage("This app needs the ${permissions[permissionIndex]} permission to function properly.")
            .setPositiveButton("Grant") { _, _ ->
                requestNextPermission()
            }
            .setNegativeButton("Deny") { _, _ ->
                Toast.makeText(
                    this,
                    "Permission ${permissions[permissionIndex]} denied.",
                    Toast.LENGTH_SHORT
                ).show()
                handlePermissionGranted()
            }
            .show()
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        locationManager.removeUpdates(locationListener)
    }

    companion object {
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
    }
}