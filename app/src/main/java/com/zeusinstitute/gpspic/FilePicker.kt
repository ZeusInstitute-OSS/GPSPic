package com.zeusinstitute.gpspic

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FilePicker(private val activity: AppCompatActivity) {

    private var filePickerCallback: ((Uri?) -> Unit)? = null

    private val filePickerLauncher = activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.data
            filePickerCallback?.invoke(uri)
        } else {
            filePickerCallback?.invoke(null)
        }
    }

    fun pickFile(callback: (Uri?) -> Unit) {
        filePickerCallback = callback

        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/jpeg"
            putExtra(Intent.EXTRA_TITLE, generateFileName())
            putExtra(MediaStore.EXTRA_OUTPUT, generateOutputUri())
        }

        filePickerLauncher.launch(intent)
    }

    private fun generateFileName(): String {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        return "GPSPic_${timeStamp}_ZeusPic.jpg"
    }

    private fun generateOutputUri(): Uri {
        val dcimDirectory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)
        val cameraDirectory = File(dcimDirectory, "Camera")
        if (!cameraDirectory.exists()) {
            cameraDirectory.mkdirs()
        }
        val file = File(cameraDirectory, generateFileName())
        return Uri.fromFile(file)
    }
}