package com.ninjacam

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : ComponentActivity() {
    private lateinit var previewView: PreviewView

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            startCamera()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val title = TextView(this).apply {
            text = "NinjaCam  •  Photo"
            textSize = 20f
            setPadding(24, 24, 24, 16)
        }

        previewView = PreviewView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
            )
        }

        val zoom = Button(this).apply {
            text = "1×"
            setOnClickListener { zoomRatio = if (zoomRatio == 1f) 2f else 1f; updateZoom() }
        }

        layout.addView(title)
        layout.addView(previewView)
        layout.addView(zoom)
        setContentView(layout)

        permissionLauncher.launch(
            arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
        )
    }

    private var zoomRatio = 1f
    private var camera: androidx.camera.core.Camera? = null

    private fun updateZoom() {
        camera?.cameraControl?.setZoomRatio(zoomRatio)
    }

    private fun startCamera() {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            val provider = future.get()
            val preview = Preview.Builder().build()
            preview.setSurfaceProvider(previewView.surfaceProvider)

            camera = provider.bindToLifecycle(
                this,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview
            )
        }, mainExecutor)
    }
}
