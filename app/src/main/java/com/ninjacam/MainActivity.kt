package com.ninjacam

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.MediaStore
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {

    private lateinit var previewView: PreviewView
    private var imageCapture: ImageCapture? = null

    private val cameraPermission =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (granted) {
                startCamera()
            } else {
                Toast.makeText(
                    this,
                    "Izin kamera diperlukan",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        createCameraUi()

        if (
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            startCamera()
        } else {
            cameraPermission.launch(Manifest.permission.CAMERA)
        }
    }

    private fun createCameraUi() {
        val root = FrameLayout(this)

        previewView = PreviewView(this).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }

        root.addView(
            previewView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        val shutterButton = ImageButton(this).apply {

            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.WHITE)
                setStroke(5, Color.DKGRAY)
            }

            setColorFilter(
                android.graphics.PorterDuffColorFilter(
                    Color.WHITE,
                    android.graphics.PorterDuff.Mode.SRC_IN
                )
            )

            contentDescription = "Ambil foto"

            setOnClickListener {
                takePhoto()
            }
        }

        val buttonSize = 92

        val buttonParams = FrameLayout.LayoutParams(
            buttonSize,
            buttonSize
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            bottomMargin = 55
        }

        root.addView(shutterButton, buttonParams)

        setContentView(root)
    }

    private fun startCamera() {

        val cameraProviderFuture =
            ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({

            val cameraProvider =
                cameraProviderFuture.get()

            val preview = Preview.Builder()
                .build()
                .also {
                    it.surfaceProvider =
                        previewView.surfaceProvider
                }

            imageCapture = ImageCapture.Builder()
                .setCaptureMode(
                    ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY
                )
                .build()

            val cameraSelector =
                CameraSelector.DEFAULT_BACK_CAMERA

            try {

                cameraProvider.unbindAll()

                cameraProvider.bindToLifecycle(
                    this,
                    cameraSelector,
                    preview,
                    imageCapture
                )

            } catch (e: Exception) {

                Toast.makeText(
                    this,
                    "Kamera gagal dibuka",
                    Toast.LENGTH_SHORT
                ).show()
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun takePhoto() {

        val capture = imageCapture ?: return

        val name =
            "NinjaCam_${System.currentTimeMillis()}.jpg"

        val contentValues = ContentValues().apply {

            put(
                MediaStore.Images.Media.DISPLAY_NAME,
                name
            )

            put(
                MediaStore.Images.Media.MIME_TYPE,
                "image/jpeg"
            )

            put(
                MediaStore.Images.Media.RELATIVE_PATH,
                "Pictures/NinjaCam"
            )
        }

        val outputOptions =
            ImageCapture.OutputFileOptions.Builder(
                contentResolver,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                contentValues
            ).build()

        capture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),

            object : ImageCapture.OnImageSavedCallback {

                override fun onImageSaved(
                    outputFileResults:
                    ImageCapture.OutputFileResults
                ) {

                    Toast.makeText(
                        this@MainActivity,
                        "Foto tersimpan ✓",
                        Toast.LENGTH_SHORT
                    ).show()
                }

                override fun onError(
                    exception: ImageCaptureException
                ) {

                    Toast.makeText(
                        this@MainActivity,
                        "Gagal mengambil foto",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        )
    }
}
