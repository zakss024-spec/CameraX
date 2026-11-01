package com.ninjacam

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.MediaStore
import android.view.Gravity
import android.view.View
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
    private var cameraProvider: ProcessCameraProvider? = null

    private var isFrontCamera = false
    private var isCameraReady = false

    private lateinit var shutterButton: ImageButton
    private lateinit var switchButton: ImageButton

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

        if (hasCameraPermission()) {
            startCamera()
        } else {
            cameraPermission.launch(Manifest.permission.CAMERA)
        }
    }

    private fun hasCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
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

        shutterButton = ImageButton(this).apply {

            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.WHITE)
                setStroke(5, Color.LTGRAY)
            }

            contentDescription = "Ambil foto"

            setOnClickListener {
                takePhoto()
            }
        }

        val shutterParams = FrameLayout.LayoutParams(
            92,
            92
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            bottomMargin = 55
        }

        root.addView(shutterButton, shutterParams)

        switchButton = ImageButton(this).apply {

            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.DKGRAY)
            }

            setImageResource(
                android.R.drawable.ic_menu_camera
            )

            setColorFilter(Color.WHITE)
            contentDescription = "Ganti kamera"

            setOnClickListener {
                switchCamera()
            }
        }

        val switchParams = FrameLayout.LayoutParams(
            56,
            56
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.END
            bottomMargin = 73
            marginEnd = 35
        }

        root.addView(switchButton, switchParams)

        setContentView(root)
    }

    private fun startCamera() {

        isCameraReady = false
        shutterButton.isEnabled = false

        val future = ProcessCameraProvider.getInstance(this)

        future.addListener({

            try {

                val provider = future.get()
                cameraProvider = provider

                val selector = if (isFrontCamera) {
                    CameraSelector.DEFAULT_FRONT_CAMERA
                } else {
                    CameraSelector.DEFAULT_BACK_CAMERA
                }

                if (!provider.hasCamera(selector)) {

                    Toast.makeText(
                        this,
                        "Kamera tidak tersedia",
                        Toast.LENGTH_SHORT
                    ).show()

                    isFrontCamera = false
                    shutterButton.isEnabled = true
                    return@addListener
                }

                val preview = Preview.Builder()
                    .build()
                    .also {
                        it.surfaceProvider =
                            previewView.surfaceProvider
                    }

                val capture = ImageCapture.Builder()
                    .setCaptureMode(
                        ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY
                    )
                    .build()

                provider.unbindAll()

                provider.bindToLifecycle(
                    this,
                    selector,
                    preview,
                    capture
                )

                imageCapture = capture
                isCameraReady = true
                shutterButton.isEnabled = true

            } catch (e: Exception) {

                isCameraReady = false
                shutterButton.isEnabled = false

                Toast.makeText(
                    this,
                    "Gagal membuka kamera",
                    Toast.LENGTH_SHORT
                ).show()
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun switchCamera() {

        if (!isCameraReady) return

        val provider = cameraProvider ?: return

        val nextCamera = !isFrontCamera

        val nextSelector = if (nextCamera) {
            CameraSelector.DEFAULT_FRONT_CAMERA
        } else {
            CameraSelector.DEFAULT_BACK_CAMERA
        }

        if (!provider.hasCamera(nextSelector)) {

            Toast.makeText(
                this,
                "Kamera tidak tersedia",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        isFrontCamera = nextCamera
        startCamera()
    }

    private fun takePhoto() {

        if (!isCameraReady) return

        val capture = imageCapture ?: return

        shutterButton.isEnabled = false

        val filename =
            "NinjaCam_${System.currentTimeMillis()}.jpg"

        val values = ContentValues().apply {

            put(
                MediaStore.Images.Media.DISPLAY_NAME,
                filename
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
                values
            ).build()

        capture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),

            object : ImageCapture.OnImageSavedCallback {

                override fun onImageSaved(
                    result: ImageCapture.OutputFileResults
                ) {

                    shutterButton.isEnabled = true

                    Toast.makeText(
                        this@MainActivity,
                        "Foto berhasil disimpan!",
                        Toast.LENGTH_SHORT
                    ).show()
                }

                override fun onError(
                    exception: ImageCaptureException
                ) {

                    shutterButton.isEnabled = true

                    Toast.makeText(
                        this@MainActivity,
                        "Gagal mengambil foto",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        )
    }

    override fun onDestroy() {
        cameraProvider?.unbindAll()
        super.onDestroy()
    }
}
