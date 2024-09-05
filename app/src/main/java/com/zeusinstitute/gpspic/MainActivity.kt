package com.zeusinstitute.gpspic

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.Manifest
import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@Suppress("DEPRECATION")
class MainActivity : AppCompatActivity() {
    private var imageCapture: ImageCapture? = null
    private lateinit var outputDirectory: File
    private lateinit var cameraExecutor: ExecutorService

    private lateinit var viewFinder: androidx.camera.view.PreviewView
    private lateinit var captureButton: ImageButton
    private lateinit var flashButton: ImageButton
    private lateinit var switchCameraButton: ImageButton
    private lateinit var galleryButton: ImageButton
    private lateinit var assistiveGridButton: ImageButton
    private lateinit var gridTypeSpinner: Spinner
    private lateinit var gridOverlay: ImageView

    private lateinit var locationOverlay: View
    private lateinit var locationTypeSpinner: Spinner
    private lateinit var locationHeader: TextView
    private lateinit var dateTimeText: TextView
    private lateinit var coordinatesText: TextView
    private lateinit var fullAddressText: TextView

    private lateinit var filePicker: FilePicker

    private var flashMode = ImageCapture.FLASH_MODE_OFF
    private var lensFacing = androidx.camera.core.CameraSelector.LENS_FACING_BACK
    private var isGridVisible = false

    private lateinit var locationManager: LocationManager
    private lateinit var locationListener: LocationListener
    private var currentLocation: Location? = null
    private var locationAccuracy = "fine"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        viewFinder = findViewById(R.id.viewFinder)
        captureButton = findViewById(R.id.camera_capture_button)
        flashButton = findViewById(R.id.flash_button)
        switchCameraButton = findViewById(R.id.camera_switch_button)
        galleryButton = findViewById(R.id.gallery_button)
        assistiveGridButton = findViewById(R.id.assistive_grid_button)
        gridTypeSpinner = findViewById(R.id.grid_type_spinner)
        gridOverlay = findViewById(R.id.gridOverlay)

        locationOverlay = findViewById(R.id.location_overlay)
        locationTypeSpinner = findViewById(R.id.location_type_spinner)
        locationHeader = findViewById(R.id.location_heading)
        dateTimeText = findViewById(R.id.date_time)
        coordinatesText = findViewById(R.id.coordinates)
        fullAddressText = findViewById(R.id.full_address)

        outputDirectory = getOutputDirectory()
        cameraExecutor = Executors.newSingleThreadExecutor()

        filePicker = FilePicker(this)
        captureButton.setOnClickListener { takePhoto() }
        flashButton.setOnClickListener { toggleFlash() }
        switchCameraButton.setOnClickListener { toggleCamera() }
        galleryButton.setOnClickListener { openGallery() }
        assistiveGridButton.setOnClickListener { toggleAssistiveGrid() }

        setupGridTypeSpinner()
        setupLocationTypeSpinner()
        setupLocationServices()

        // Check for camera permission
        if (hasCameraPermission()) {
            startCamera()
        } else {
            requestCameraPermission()
        }
    }

    private fun hasCameraPermission() = ContextCompat.checkSelfPermission(
        this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    private fun requestCameraPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.CAMERA),
            CAMERA_PERMISSION_REQUEST_CODE
        )
    }

    private fun setupGridTypeSpinner() {
        val gridTypes = arrayOf("Rule of Thirds", "Golden Ratio", "Square", "4x4 Grid")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, gridTypes)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        gridTypeSpinner.adapter = adapter

        gridTypeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View, position: Int, id: Long) {
                updateGridOverlay(gridTypes[position])
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
    }

    private fun toggleAssistiveGrid() {
        isGridVisible = !isGridVisible
        gridOverlay.visibility = if (isGridVisible) View.VISIBLE else View.GONE
        gridTypeSpinner.visibility = if (isGridVisible) View.VISIBLE else View.GONE
        if (isGridVisible) {
            gridOverlay.post {
                updateGridOverlay(gridTypeSpinner.selectedItem.toString())
            }
        }
    }

    private fun updateGridOverlay(gridType: String) {
        val width = gridOverlay.width
        val height = gridOverlay.height

        if (width <= 0 || height <= 0) {
            // View not measured yet, postpone drawing
            gridOverlay.post {
                updateGridOverlay(gridType)
            }
            return
        }

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint().apply {
            color = Color.argb(128, 255, 165, 0) // Semi-transparent orange
            strokeWidth = 2f
            style = Paint.Style.STROKE
        }

        when (gridType) {
            "Rule of Thirds" -> drawRuleOfThirds(canvas, paint)
            "Golden Ratio" -> drawGoldenRatio(canvas, paint)
            "Square" -> drawSquare(canvas, paint)
            "4x4 Grid" -> draw4x4Grid(canvas, paint)
        }

        gridOverlay.setImageBitmap(bitmap)
    }

    private fun drawRuleOfThirds(canvas: Canvas, paint: Paint) {
        val width = canvas.width.toFloat()
        val height = canvas.height.toFloat()

        // Vertical lines
        canvas.drawLine(width / 3, 0f, width / 3, height, paint)
        canvas.drawLine(2 * width / 3, 0f, 2 * width / 3, height, paint)

        // Horizontal lines
        canvas.drawLine(0f, height / 3, width, height / 3, paint)
        canvas.drawLine(0f, 2 * height / 3, width, 2 * height / 3, paint)
    }

    private fun drawGoldenRatio(canvas: Canvas, paint: Paint) {
        val width = canvas.width.toFloat()
        val height = canvas.height.toFloat()
        val ratio = 0.618f

        // Vertical lines
        canvas.drawLine(width * ratio, 0f, width * ratio, height, paint)
        canvas.drawLine(width * (1 - ratio), 0f, width * (1 - ratio), height, paint)

        // Horizontal lines
        canvas.drawLine(0f, height * ratio, width, height * ratio, paint)
        canvas.drawLine(0f, height * (1 - ratio), width, height * (1 - ratio), paint)
    }

    private fun drawSquare(canvas: Canvas, paint: Paint) {
        val size = minOf(canvas.width, canvas.height).toFloat()
        val left = (canvas.width - size) / 2
        val top = (canvas.height - size) / 2

        canvas.drawRect(left, top, left + size, top + size, paint)
    }

    private fun draw4x4Grid(canvas: Canvas, paint: Paint) {
        val width = canvas.width.toFloat()
        val height = canvas.height.toFloat()

        for (i in 1..3) {
            canvas.drawLine(width * i / 4, 0f, width * i / 4, height, paint)
            canvas.drawLine(0f, height * i / 4, width, height * i / 4, paint)
        }
    }

    private fun toggleFlash() {
        flashMode = when (flashMode) {
            ImageCapture.FLASH_MODE_OFF -> ImageCapture.FLASH_MODE_ON
            ImageCapture.FLASH_MODE_ON -> ImageCapture.FLASH_MODE_AUTO
            else -> ImageCapture.FLASH_MODE_OFF
        }
        updateFlashIcon()
        startCamera()
    }

    private fun updateFlashIcon() {
        val iconResource = when (flashMode) {
            ImageCapture.FLASH_MODE_ON -> R.drawable.ic_flash_on
            ImageCapture.FLASH_MODE_AUTO -> R.drawable.ic_flash_auto
            else -> R.drawable.ic_flash_off
        }
        flashButton.setImageResource(iconResource)
    }

    private fun toggleCamera() {
        lensFacing = if (lensFacing == androidx.camera.core.CameraSelector.LENS_FACING_BACK) {
            androidx.camera.core.CameraSelector.LENS_FACING_FRONT
        } else {
            androidx.camera.core.CameraSelector.LENS_FACING_BACK
        }
        startCamera()
    }

    private fun openGallery() {
        // Implement gallery opening logic
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(viewFinder.surfaceProvider)
            }

            imageCapture = ImageCapture.Builder()
                .setFlashMode(flashMode)
                .build()

            val cameraSelector = androidx.camera.core.CameraSelector.Builder()
                .requireLensFacing(lensFacing)
                .build()

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture)
            } catch (exc: Exception) {
                // Handle error
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun getOutputDirectory(): File {
        val mediaDir = externalMediaDirs.firstOrNull()?.let {
            File(it, resources.getString(R.string.app_name)).apply { mkdirs() }
        }
        return if (mediaDir != null && mediaDir.exists()) mediaDir else filesDir
    }

    private fun setupLocationTypeSpinner() {
        val locationTypes = arrayOf("Accurate", "Battery Saving")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, locationTypes)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        locationTypeSpinner.adapter = adapter

        locationTypeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View, position: Int, id: Long) {
                locationAccuracy = if (position == 0) {
                    LocationManager.GPS_PROVIDER
                } else {
                    LocationManager.NETWORK_PROVIDER
                }
                checkLocationPermission()
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
    }

    private fun setupLocationServices() {
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        locationListener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                currentLocation = location
                updateLocationOverlay()
            }

            @Deprecated("Deprecated in Java")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
        }
    }

    private fun checkLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                LOCATION_PERMISSION_REQUEST_CODE
            )
        } else {
            startLocationUpdates()
        }
    }

    private fun startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        locationManager.requestLocationUpdates(locationAccuracy, 10000, 5f, locationListener)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            CAMERA_PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    startCamera()
                } else {
                    // Handle permission denied
                    // You might want to show a message to the user explaining why camera permission is necessary
                }
            }
            LOCATION_PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && (grantResults[0] == PackageManager.PERMISSION_GRANTED || grantResults[1] == PackageManager.PERMISSION_GRANTED)) {
                    startLocationUpdates()
                } else {
                    // Handle permission denied
                    // You might want to show a message to the user explaining why location is important
                }
            }
        }
    }

    private fun takePhoto() {
        val imageCapture = imageCapture ?: return
        filePicker.pickFile { uri ->
        uri?.let {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, "My Image")
                put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            }
            val outputOptions = ImageCapture.OutputFileOptions.Builder(contentResolver, it, contentValues).build()

            imageCapture.takePicture(
                outputOptions,
                ContextCompat.getMainExecutor(this),
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                        val savedUri = output.savedUri ?: uri
                        // Handle successful image capture
                        updateGallery(savedUri)
                    }

                        override fun onError(exc: ImageCaptureException) {
                            // Handle error
                        }
                    }
                )
            }
        }
    }

    private fun updateGallery(uri: Uri) {
        val intent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
        intent.data = uri
        sendBroadcast(intent)
    }

    private fun updateLocationUI(currentDateTime: String, location: Location?, address: android.location.Address?) {
        val locationName = address?.let {
            listOfNotNull(it.locality, it.adminArea, it.countryName).joinToString(", ")
        } ?: "Current Location"

        locationHeader.text = locationName
        dateTimeText.text = "Date/Time: $currentDateTime"
        coordinatesText.text = if (location != null) {
            "Lat: ${location.latitude}, Long: ${location.longitude}"
        } else {
            "Coordinates not available"
        }
        fullAddressText.text = "Address: ${address?.getAddressLine(0) ?: "[Not available]"}"
    }

    private fun updateLocationOverlay() {
        runOnUiThread {
            if (currentLocation == null) {
                updateLocationUI("No data available", null, null)
            } else {
                val location = currentLocation!!
                val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                val currentDateTime = formatter.format(Date())

                // Perform reverse geocoding
                val geocoder = Geocoder(this, Locale.getDefault())
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        geocoder.getFromLocation(location.latitude, location.longitude, 1) { addresses ->
                            val address = addresses.firstOrNull()
                            runOnUiThread {
                                updateLocationUI(currentDateTime, location, address)
                            }
                        }
                    } else {
                        @Suppress("DEPRECATION")
                        val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)
                        val address = addresses?.firstOrNull()
                        updateLocationUI(currentDateTime, location, address)
                    }
                } catch (e: IOException) {
                    updateLocationUI("Geocoding error", location, null)
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        locationManager.removeUpdates(locationListener)
        cameraExecutor.shutdown()
    }

    companion object {
        private const val CAMERA_PERMISSION_REQUEST_CODE = 100
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1001
    }
}