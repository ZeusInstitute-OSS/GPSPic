    package com.zeusinstitute.gpspic
    
    import android.Manifest
    import android.annotation.SuppressLint
    import android.content.Context
    import android.content.Intent
    import android.content.pm.PackageManager
    import android.graphics.Bitmap
    import android.graphics.BitmapFactory
    import android.graphics.Canvas
    import android.graphics.Color
    import android.graphics.Paint
    import android.graphics.PorterDuff
    import android.graphics.PorterDuffXfermode
    import android.graphics.Rect
    import android.graphics.drawable.BitmapDrawable
    import android.location.Geocoder
    import android.location.Location
    import android.location.LocationListener
    import android.location.LocationManager
    import android.location.LocationRequest
    import android.media.MediaPlayer
    import android.net.Uri
    import android.os.Build
    import android.os.Bundle
    import android.os.Environment
    import android.os.Looper
    import android.util.Log
    import android.view.LayoutInflater
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
    import androidx.core.content.FileProvider
    import androidx.core.location.LocationRequestCompat
    import androidx.lifecycle.lifecycleScope
    import androidx.recyclerview.widget.LinearLayoutManager
    import androidx.recyclerview.widget.RecyclerView
    import kotlinx.coroutines.channels.awaitClose
    import kotlinx.coroutines.flow.callbackFlow
    import kotlinx.coroutines.launch
    import java.io.File
    import java.io.FileOutputStream
    import java.io.IOException
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
        private lateinit var galleryButton: ImageButton
    
        private lateinit var tvLocationHeader: TextView
        private lateinit var tvDateTime: TextView
        private lateinit var tvCoordinates: TextView
        private lateinit var tvFullAddress: TextView
        private lateinit var ivMapPreview: ImageView
    
        private val locationData = mutableListOf<LocationData>()
    
        // private lateinit var locationRecyclerView: RecyclerView
    
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

        private fun getOutputDirectory(): File {
            val mediaDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                ?.let { File(it, "GPSPic").apply { mkdirs() } }
            return mediaDir ?: filesDir
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
    
        @SuppressLint("MissingInflatedId")
        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            setContentView(R.layout.activity_main)
    
            flashButton = findViewById(R.id.flash_button)
            cameraSwitchButton = findViewById(R.id.camera_switch_button)
            cameraCaptureButton = findViewById(R.id.camera_capture_button)
    
            galleryButton = findViewById(R.id.gallery_button)
            outputDirectory = getOutputDirectory()

            // locationRecyclerView = findViewById(R.id.locationRecyclerView)

            // Initialize location overlay views
            tvLocationHeader = findViewById(R.id.tvLocationHeader)
            tvDateTime = findViewById(R.id.tvDateTime)
            tvCoordinates = findViewById(R.id.tvCoordinates)
            tvFullAddress = findViewById(R.id.tvFullAddress)
            ivMapPreview = findViewById(R.id.ivMapPreview)


            cameraCaptureButton.setOnClickListener { takePhoto() }
            flashButton.setOnClickListener { toggleFlash() }
            cameraSwitchButton.setOnClickListener { toggleCamera() }
            galleryButton.setOnClickListener { openLastImageInGallery() }

            // Update the gallery button preview
            updateGalleryButtonPreview()

            cameraExecutor = Executors.newSingleThreadExecutor()
    
         //   locationRecyclerView.layoutManager = LinearLayoutManager(this)

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

            val baseFileName = SimpleDateFormat(FILENAME_FORMAT, Locale.ENGLISH)
                .format(System.currentTimeMillis())
            val gpsPhotoFile = File(outputDirectory, "$baseFileName-GPSPic.jpg")

            val gpsOutputOptions = ImageCapture.OutputFileOptions.Builder(gpsPhotoFile).build()

            imageCapture.takePicture(
                gpsOutputOptions,
                ContextCompat.getMainExecutor(this),
                object : ImageCapture.OnImageSavedCallback {
                    override fun onError(exc: ImageCaptureException) {
                        Toast.makeText(baseContext, "GPS photo capture failed: ${exc.message}", Toast.LENGTH_SHORT).show()
                    }

                    override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                        val savedUri = Uri.fromFile(gpsPhotoFile)
                        Toast.makeText(baseContext, "GPS photo saved: $savedUri", Toast.LENGTH_SHORT).show()

                        val location = getCurrentLocation()
                        val originalBitmap = BitmapFactory.decodeFile(gpsPhotoFile.absolutePath)

                        val inflater = LayoutInflater.from(this@MainActivity)
                        val locationOverlayView = inflater.inflate(R.layout.location_details, null)

                        populateLocationOverlay(locationOverlayView, location)

                        // Measure and layout the overlay view
                        val spec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
                        locationOverlayView.measure(spec, spec)
                        locationOverlayView.layout(0, 0, locationOverlayView.measuredWidth, locationOverlayView.measuredHeight)

                        // Create a bitmap with the same size as the original image
                        val overlayBitmap = Bitmap.createBitmap(originalBitmap.width, originalBitmap.height, Bitmap.Config.ARGB_8888)
                        val canvas = Canvas(overlayBitmap)
                        canvas.drawBitmap(originalBitmap, 0f, 0f, null)

                        // Calculate position for bottom-center
                        val overlayX = (canvas.width - locationOverlayView.measuredWidth) / 2f
                        val overlayY = canvas.height - locationOverlayView.measuredHeight - 48f

                        // Draw the overlay
                        canvas.save()
                        canvas.translate(overlayX, overlayY)
                        locationOverlayView.draw(canvas)
                        canvas.restore()

                        // After saving the image, update the gallery button preview
                        updateGalleryButtonPreview()

                        // Save the final image
                        try {
                            FileOutputStream(gpsPhotoFile).use { out ->
                                overlayBitmap.compress(Bitmap.CompressFormat.JPEG, 100, out)
                            }
                        } catch (e: IOException) {
                            Toast.makeText(this@MainActivity, "Failed to save overlayed image", Toast.LENGTH_SHORT).show()
                        } finally {
                            originalBitmap.recycle()
                            overlayBitmap.recycle()
                        }
                    }
                }
            )
        }

        private fun populateLocationOverlay(view: View, location: Location?) {
            val tvLocationHeader = view.findViewById<TextView>(R.id.tvLocationHeader)
            val tvDateTime = view.findViewById<TextView>(R.id.tvDateTime)
            val tvCoordinates = view.findViewById<TextView>(R.id.tvCoordinates)
            val tvFullAddress = view.findViewById<TextView>(R.id.tvFullAddress)
            val ivMapPreview = view.findViewById<ImageView>(R.id.ivMapPreview)

            if (location != null) {
                val geocoder = Geocoder(this@MainActivity, Locale.getDefault())
                try {
                    val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)
                    if (addresses?.isNotEmpty() == true) {
                        val address = addresses[0]
                        tvLocationHeader.text = "${address.locality}, ${address.adminArea}, ${address.countryName}"
                        tvDateTime.text = SimpleDateFormat("EEEE, MMMM d, yyyy h:mm a", Locale.getDefault()).format(Date())
                        tvCoordinates.text = "Lat: ${location.latitude}, Long: ${location.longitude}"
                        tvFullAddress.text = address.getAddressLine(0)
                        ivMapPreview.setImageResource(R.drawable.map_placeholder)
                    }
                } catch (e: Exception) {
                    Toast.makeText(this@MainActivity, "Error getting location details", Toast.LENGTH_SHORT).show()
                }
            } else {
                tvLocationHeader.text = "Location Unavailable"
                tvDateTime.text = SimpleDateFormat("EEEE, MMMM d, yyyy h:mm a", Locale.getDefault()).format(Date())
                tvCoordinates.text = "Coordinates unavailable"
                tvFullAddress.text = "Address unavailable"
                ivMapPreview.setImageResource(R.drawable.map_placeholder)
            }
        }

        private fun getCurrentLocation(): Location? {
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return null // Handle permission request if needed
            }
            return locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
        }
    
        private fun addLocationOverlayToBitmap(bitmap: Bitmap, location: Location?) {
            val canvas = Canvas(bitmap)
            val paint = Paint().apply {
                color = Color.WHITE
                textSize = 30f // Adjust text size as needed
            }
    
            val locationText = if (location != null) {
                "Lat: ${location.latitude}, Long: ${location.longitude}"
            } else {
                "Location unavailable"
            }
    
            canvas.drawText(locationText, 10f, 50f, paint) // Adjust position as needed
        }
    
        private fun getLastImageFile(): File? {
            val files = outputDirectory.listFiles()?.filter {
                it.isFile && it.name.endsWith("-GPSPic.jpg")
            }
            return files?.maxByOrNull { it.lastModified() }
        }
    
        private fun openLastImageInGallery() {
            val lastImageFile = getLastImageFile() ?: return
            val uri = FileProvider.getUriForFile(
                this,
                "com.zeusinstitute.gpspic.fileprovider", // Match the authority
                lastImageFile
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "image/*")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(intent)
        }
    
        private fun startCamera() {
            val viewFinder = findViewById<PreviewView>(R.id.viewFinder)
            val cameraProviderFuture =ProcessCameraProvider.getInstance(this)
    
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
                    Toast.makeText(
                        this,
                        "Failed to start camera: ${exc.message}",
                        Toast.LENGTH_SHORT
                    ).show()
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
    
                    val locationItem = LocationData(
                        "${address.locality}, ${address.adminArea}, ${address.countryName}",
                        SimpleDateFormat("EEEE, MMMM d, yyyy h:mm a", Locale.getDefault()).format(Date()),
                        "Lat: ${location.latitude}, Long: ${location.longitude}",
                        address.getAddressLine(0),
                        R.drawable.map_placeholder // Replace with actual map image later
                    )
                    tvLocationHeader.text = "${address.locality}, ${address.adminArea}, ${address.countryName}"
                    tvDateTime.text = SimpleDateFormat("EEEE, MMMM d, yyyy h:mm a", Locale.getDefault()).format(Date())
                    tvCoordinates.text = "Lat: ${location.latitude}, Long: ${location.longitude}"
                    tvFullAddress.text = address.getAddressLine(0)
                    ivMapPreview.setImageResource(R.drawable.map_placeholder)

                    locationData.add(locationItem)
                }
            } catch (e: Exception) {
                Toast.makeText(this, "Error getting location details", Toast.LENGTH_SHORT).show()
            }
        }

        private fun updateGalleryButtonPreview() {
            val lastImageFile = getLastImageFile()
            if (lastImageFile != null) {
                try {
                    // Load the image as a Bitmap
                    val bitmap = BitmapFactory.decodeFile(lastImageFile.absolutePath)

                    // Create a circular bitmap
                    val circularBitmap = getCircularBitmap(bitmap)

                    // Set the circular bitmap as the background of the gallery button
                    galleryButton.background = BitmapDrawable(resources, circularBitmap)

                    // Optionally, you can add a border to make it stand out
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        galleryButton.foreground = ContextCompat.getDrawable(this, R.drawable.circular_border)
                    }
                } catch (e: Exception) {
                    Log.e("MainActivity", "Error loading last image preview", e)
                    // If there's an error, set a default background
                    galleryButton.setBackgroundResource(R.drawable.ic_gallery)
                }
            } else {
                // If no image is found, set the default gallery icon
                galleryButton.setBackgroundResource(R.drawable.ic_gallery)
            }
        }

        private fun getCircularBitmap(bitmap: Bitmap): Bitmap {
            val output = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(output)

            val paint = Paint()
            val rect = Rect(0, 0, bitmap.width, bitmap.height)

            paint.isAntiAlias = true
            canvas.drawARGB(0, 0, 0, 0)
            canvas.drawCircle(bitmap.width / 2f, bitmap.height / 2f, bitmap.width / 2f, paint)

            paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
            canvas.drawBitmap(bitmap, rect, rect, paint)

            return output
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
    
        // Define LocationData data class within MainActivity
        data class LocationData(
            val locationHeader: String,
            val dateTime: String,
            val coordinates: String,
            val fullAddress: String,
            val mapPreview: Int // Placeholder for now, you'd use a map image later
        )
    
    }