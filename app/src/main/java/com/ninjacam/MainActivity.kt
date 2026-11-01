package com.ninjacam

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import java.util.Locale

class MainActivity : ComponentActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var statusText: TextView
    private lateinit var shutterButton: Button
    private lateinit var modeButton: Button
    private lateinit var switchButton: ImageButton
    private lateinit var zoomSlider: SeekBar
    private lateinit var zoomText: TextView

    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null

    private var imageCapture: ImageCapture? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null

    private var isFrontCamera = false
    private var isVideoMode = false
    private var isCameraReady = false
    private var isRecording = false

    private val cameraPermission =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (granted) startCamera()
            else showMessage("Izin kamera diperlukan")
        }

    private val audioPermission =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (granted) startVideoRecording()
            else showMessage("Izin mikrofon diperlukan untuk audio")
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        createCameraUi()

        if (hasPermission(Manifest.permission.CAMERA)) {
            startCamera()
        } else {
            cameraPermission.launch(Manifest.permission.CAMERA)
        }
    }

    private fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            permission
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun createCameraUi() {

        val root = FrameLayout(this)

        previewView = PreviewView(this).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }

        root.addView(
            previewView,
            FrameLayout.LayoutParams(-1, -1)
        )

        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(16, 18, 16, 10)
        }

        statusText = TextView(this).apply {
            text = "NINJACAM"
            textSize = 17f
            setTextColor(Color.WHITE)
        }

        topBar.addView(
            statusText,
            LinearLayout.LayoutParams(0, 55, 1f)
        )

        switchButton = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_menu_camera)
            setColorFilter(Color.WHITE)
            background = circleBackground(Color.DKGRAY)
            contentDescription = "Ganti kamera"
            setOnClickListener { switchCamera() }
        }

        topBar.addView(
            switchButton,
            LinearLayout.LayoutParams(55, 55)
        )

        root.addView(
            topBar,
            FrameLayout.LayoutParams(
                -1,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP
            )
        )

        val bottomPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(20, 14, 20, 28)
            setBackgroundColor(Color.argb(175, 0, 0, 0))
        }

        zoomText = TextView(this).apply {
            text = "1.0×"
            textSize = 17f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
        }

        zoomSlider = SeekBar(this).apply {
            max = 1000
            progress = 0

            setOnSeekBarChangeListener(
                object : SeekBar.OnSeekBarChangeListener {

                    override fun onProgressChanged(
                        seekBar: SeekBar?,
                        progress: Int,
                        fromUser: Boolean
                    ) {
                        if (fromUser) applySliderZoom(progress)
                    }

                    override fun onStartTrackingTouch(
                        seekBar: SeekBar?
                    ) {}

                    override fun onStopTrackingTouch(
                        seekBar: SeekBar?
                    ) {}
                }
            )
        }

        modeButton = Button(this).apply {
            text = "PHOTO"
            setOnClickListener { toggleMode() }
        }

        shutterButton = Button(this).apply {
            text = "●"
            textSize = 25f
            setOnClickListener { onShutterPressed() }
        }

        bottomPanel.addView(zoomText)
        bottomPanel.addView(
            zoomSlider,
            LinearLayout.LayoutParams(-1, 45)
        )
        bottomPanel.addView(modeButton)
        bottomPanel.addView(
            shutterButton,
            LinearLayout.LayoutParams(150, 75)
        )

        root.addView(
            bottomPanel,
            FrameLayout.LayoutParams(
                -1,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM
            )
        )

        setContentView(root)
    }

    private fun circleBackground(color: Int) =
        GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
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
                    showMessage("Kamera tidak tersedia")
                    isFrontCamera = false
                    shutterButton.isEnabled = true
                    return@addListener
                }

                val preview = Preview.Builder()
                    .build()
                    .also {
                        it.surfaceProvider = previewView.surfaceProvider
                    }

                val capture = ImageCapture.Builder()
                    .setCaptureMode(
                        ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY
                    )
                    .build()

                val recorder = Recorder.Builder()
                    .setQualitySelector(
                        QualitySelector.from(Quality.HD)
                    )
                    .build()

                val video = VideoCapture.withOutput(recorder)

                provider.unbindAll()

                camera = provider.bindToLifecycle(
                    this,
                    selector,
                    preview,
                    capture,
                    video
                )

                imageCapture = capture
                videoCapture = video

                isCameraReady = true
                shutterButton.isEnabled = true

                updateZoomControls()

            } catch (e: Exception) {
                showMessage("Gagal membuka kamera")
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun switchCamera() {

        if (!isCameraReady || isRecording) return

        val next = !isFrontCamera

        val selector = if (next) {
            CameraSelector.DEFAULT_FRONT_CAMERA
        } else {
            CameraSelector.DEFAULT_BACK_CAMERA
        }

        val provider = cameraProvider ?: return

        if (!provider.hasCamera(selector)) {
            showMessage("Kamera tidak tersedia")
            return
        }

        isFrontCamera = next
        startCamera()
    }

    private fun toggleMode() {

        if (isRecording) return

        isVideoMode = !isVideoMode

        modeButton.text = if (isVideoMode) {
            "VIDEO"
        } else {
            "PHOTO"
        }

        shutterButton.text = if (isVideoMode) {
            "● REC"
        } else {
            "●"
        }
    }

    private fun onShutterPressed() {

        if (!isCameraReady) return

        if (isVideoMode) {

            if (isRecording) {
                stopVideoRecording()
            } else {
                requestAudioAndRecord()
            }

        } else {
            takePhoto()
        }
    }

    private fun takePhoto() {

        val capture = imageCapture ?: return

        shutterButton.isEnabled = false

        val values = ContentValues().apply {
            put(
                MediaStore.Images.Media.DISPLAY_NAME,
                "NinjaCam_${System.currentTimeMillis()}.jpg"
            )
            put(
                MediaStore.Images.Media.MIME_TYPE,
                "image/jpeg"
            )
            if (Build.VERSION.SDK_INT >= 29) {
                put(
                    MediaStore.Images.Media.RELATIVE_PATH,
                    "Pictures/NinjaCam"
                )
            }
        }

        val options =
            ImageCapture.OutputFileOptions.Builder(
                contentResolver,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                values
            ).build()

        capture.takePicture(
            options,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {

                override fun onImageSaved(
                    result: ImageCapture.OutputFileResults
                ) {
                    shutterButton.isEnabled = true
                    showMessage("Foto tersimpan ✓")
                }

                override fun onError(
                    exception: ImageCaptureException
                ) {
                    shutterButton.isEnabled = true
                    showMessage("Gagal mengambil foto")
                }
            }
        )
    }

    private fun requestAudioAndRecord() {

        if (hasPermission(Manifest.permission.RECORD_AUDIO)) {
            startVideoRecording()
        } else {
            audioPermission.launch(
                Manifest.permission.RECORD_AUDIO
            )
        }
    }

    private fun startVideoRecording() {

        val video = videoCapture ?: return

        val values = ContentValues().apply {
            put(
                MediaStore.Video.Media.DISPLAY_NAME,
                "NinjaCam_${System.currentTimeMillis()}.mp4"
            )
            put(
                MediaStore.Video.Media.MIME_TYPE,
                "video/mp4"
            )
            if (Build.VERSION.SDK_INT >= 29) {
                put(
                    MediaStore.Video.Media.RELATIVE_PATH,
                    "Movies/NinjaCam"
                )
            }
        }

        val outputOptions =
            MediaStoreOutputOptions.Builder(
                contentResolver,
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            )
                .setContentValues(values)
                .build()

        try {

            var pending =
                video.output.prepareRecording(
                    this,
                    outputOptions
                )

            pending = pending.withAudioEnabled()

            recording = pending.start(
                ContextCompat.getMainExecutor(this)
            ) { event ->

                when (event) {

                    is VideoRecordEvent.Start -> {
                        isRecording = true
                        shutterButton.text = "■ STOP"
                        statusText.text = "● RECORDING"
                    }

                    is VideoRecordEvent.Finalize -> {

                        isRecording = false
                        recording = null

                        shutterButton.text = "● REC"
                        statusText.text = "NINJACAM"

                        if (!event.hasError()) {
                            showMessage("Video tersimpan ✓")
                        } else {
                            showMessage("Gagal menyimpan video")
                        }
                    }
                }
            }

        } catch (e: Exception) {
            showMessage("Tidak dapat memulai rekaman")
        }
    }

    private fun stopVideoRecording() {
        recording?.stop()
    }
    
private fun updateZoomControls() {

    val state = camera?.cameraInfo?.zoomState?.value
        ?: return

    val minZoom = state.minZoomRatio
    val maxZoom = state.maxZoomRatio

    zoomSlider.progress = 0

    zoomText.text = String.format(
        Locale.US,
        "%.1f×",
        minZoom
    )

    zoomSlider.max = 1000

    zoomSlider.isEnabled = maxZoom > minZoom
}
    
    private fun applySliderZoom(progress: Int) {

        val state = camera?.cameraInfo?.zoomState?.value
            ?: return

        val ratio = state.minZoomRatio +
            (state.maxZoomRatio - state.minZoomRatio) *
            progress / 1000f

        applyZoom(ratio)
    }

    private fun applyZoom(ratio: Float) {

        val state = camera?.cameraInfo?.zoomState?.value
            ?: return

        val safeRatio = ratio.coerceIn(
            state.minZoomRatio,
            state.maxZoomRatio
        )

        camera?.cameraControl?.setZoomRatio(safeRatio)

        zoomText.text = String.format(
            Locale.US,
            "%.1f×",
            safeRatio
        )
    }

    private fun showMessage(message: String) {
        Toast.makeText(
            this,
            message,
            Toast.LENGTH_SHORT
        ).show()
    }

    override fun onDestroy() {
        recording?.stop()
        cameraProvider?.unbindAll()
        super.onDestroy()
    }
}
package com.ninjacam

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import java.util.Locale

class MainActivity : ComponentActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var statusText: TextView
    private lateinit var shutterButton: Button
    private lateinit var modeButton: Button
    private lateinit var switchButton: ImageButton
    private lateinit var zoomSlider: SeekBar
    private lateinit var zoomText: TextView

    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null

    private var imageCapture: ImageCapture? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null

    private var isFrontCamera = false
    private var isVideoMode = false
    private var isCameraReady = false
    private var isRecording = false

    private val cameraPermission =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (granted) startCamera()
            else showMessage("Izin kamera diperlukan")
        }

    private val audioPermission =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (granted) startVideoRecording()
            else showMessage("Izin mikrofon diperlukan untuk audio")
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        createCameraUi()

        if (hasPermission(Manifest.permission.CAMERA)) {
            startCamera()
        } else {
            cameraPermission.launch(Manifest.permission.CAMERA)
        }
    }

    private fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            permission
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun createCameraUi() {

        val root = FrameLayout(this)

        previewView = PreviewView(this).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }

        root.addView(
            previewView,
            FrameLayout.LayoutParams(-1, -1)
        )

        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(16, 18, 16, 10)
        }

        statusText = TextView(this).apply {
            text = "NINJACAM"
            textSize = 17f
            setTextColor(Color.WHITE)
        }

        topBar.addView(
            statusText,
            LinearLayout.LayoutParams(0, 55, 1f)
        )

        switchButton = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_menu_camera)
            setColorFilter(Color.WHITE)
            background = circleBackground(Color.DKGRAY)
            contentDescription = "Ganti kamera"
            setOnClickListener { switchCamera() }
        }

        topBar.addView(
            switchButton,
            LinearLayout.LayoutParams(55, 55)
        )

        root.addView(
            topBar,
            FrameLayout.LayoutParams(
                -1,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP
            )
        )

        val bottomPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(20, 14, 20, 28)
            setBackgroundColor(Color.argb(175, 0, 0, 0))
        }

        zoomText = TextView(this).apply {
            text = "1.0×"
            textSize = 17f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
        }

        zoomSlider = SeekBar(this).apply {
            max = 1000
            progress = 0

            setOnSeekBarChangeListener(
                object : SeekBar.OnSeekBarChangeListener {

                    override fun onProgressChanged(
                        seekBar: SeekBar?,
                        progress: Int,
                        fromUser: Boolean
                    ) {
                        if (fromUser) applySliderZoom(progress)
                    }

                    override fun onStartTrackingTouch(
                        seekBar: SeekBar?
                    ) {}

                    override fun onStopTrackingTouch(
                        seekBar: SeekBar?
                    ) {}
                }
            )
        }

        modeButton = Button(this).apply {
            text = "PHOTO"
            setOnClickListener { toggleMode() }
        }

        shutterButton = Button(this).apply {
            text = "●"
            textSize = 25f
            setOnClickListener { onShutterPressed() }
        }

        bottomPanel.addView(zoomText)
        bottomPanel.addView(
            zoomSlider,
            LinearLayout.LayoutParams(-1, 45)
        )
        bottomPanel.addView(modeButton)
        bottomPanel.addView(
            shutterButton,
            LinearLayout.LayoutParams(150, 75)
        )

        root.addView(
            bottomPanel,
            FrameLayout.LayoutParams(
                -1,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM
            )
        )

        setContentView(root)
    }

    private fun circleBackground(color: Int) =
        GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
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
                    showMessage("Kamera tidak tersedia")
                    isFrontCamera = false
                    shutterButton.isEnabled = true
                    return@addListener
                }

                val preview = Preview.Builder()
                    .build()
                    .also {
                        it.surfaceProvider = previewView.surfaceProvider
                    }

                val capture = ImageCapture.Builder()
                    .setCaptureMode(
                        ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY
                    )
                    .build()

                val recorder = Recorder.Builder()
                    .setQualitySelector(
                        QualitySelector.from(Quality.HD)
                    )
                    .build()

                val video = VideoCapture.withOutput(recorder)

                provider.unbindAll()

                camera = provider.bindToLifecycle(
                    this,
                    selector,
                    preview,
                    capture,
                    video
                )

                imageCapture = capture
                videoCapture = video

                isCameraReady = true
                shutterButton.isEnabled = true

                updateZoomControls()

            } catch (e: Exception) {
                showMessage("Gagal membuka kamera")
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun switchCamera() {

        if (!isCameraReady || isRecording) return

        val next = !isFrontCamera

        val selector = if (next) {
            CameraSelector.DEFAULT_FRONT_CAMERA
        } else {
            CameraSelector.DEFAULT_BACK_CAMERA
        }

        val provider = cameraProvider ?: return

        if (!provider.hasCamera(selector)) {
            showMessage("Kamera tidak tersedia")
            return
        }

        isFrontCamera = next
        startCamera()
    }

    private fun toggleMode() {

        if (isRecording) return

        isVideoMode = !isVideoMode

        modeButton.text = if (isVideoMode) {
            "VIDEO"
        } else {
            "PHOTO"
        }

        shutterButton.text = if (isVideoMode) {
            "● REC"
        } else {
            "●"
        }
    }

    private fun onShutterPressed() {

        if (!isCameraReady) return

        if (isVideoMode) {

            if (isRecording) {
                stopVideoRecording()
            } else {
                requestAudioAndRecord()
            }

        } else {
            takePhoto()
        }
    }

    private fun takePhoto() {

        val capture = imageCapture ?: return

        shutterButton.isEnabled = false

        val values = ContentValues().apply {
            put(
                MediaStore.Images.Media.DISPLAY_NAME,
                "NinjaCam_${System.currentTimeMillis()}.jpg"
            )
            put(
                MediaStore.Images.Media.MIME_TYPE,
                "image/jpeg"
            )
            if (Build.VERSION.SDK_INT >= 29) {
                put(
                    MediaStore.Images.Media.RELATIVE_PATH,
                    "Pictures/NinjaCam"
                )
            }
        }

        val options =
            ImageCapture.OutputFileOptions.Builder(
                contentResolver,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                values
            ).build()

        capture.takePicture(
            options,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {

                override fun onImageSaved(
                    result: ImageCapture.OutputFileResults
                ) {
                    shutterButton.isEnabled = true
                    showMessage("Foto tersimpan ✓")
                }

                override fun onError(
                    exception: ImageCaptureException
                ) {
                    shutterButton.isEnabled = true
                    showMessage("Gagal mengambil foto")
                }
            }
        )
    }

    private fun requestAudioAndRecord() {

        if (hasPermission(Manifest.permission.RECORD_AUDIO)) {
            startVideoRecording()
        } else {
            audioPermission.launch(
                Manifest.permission.RECORD_AUDIO
            )
        }
    }

    private fun startVideoRecording() {

        val video = videoCapture ?: return

        val values = ContentValues().apply {
            put(
                MediaStore.Video.Media.DISPLAY_NAME,
                "NinjaCam_${System.currentTimeMillis()}.mp4"
            )
            put(
                MediaStore.Video.Media.MIME_TYPE,
                "video/mp4"
            )
            if (Build.VERSION.SDK_INT >= 29) {
                put(
                    MediaStore.Video.Media.RELATIVE_PATH,
                    "Movies/NinjaCam"
                )
            }
        }

        val outputOptions =
            MediaStoreOutputOptions.Builder(
                contentResolver,
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            )
                .setContentValues(values)
                .build()

        try {

            var pending =
                video.output.prepareRecording(
                    this,
                    outputOptions
                )

            pending = pending.withAudioEnabled()

            recording = pending.start(
                ContextCompat.getMainExecutor(this)
            ) { event ->

                when (event) {

                    is VideoRecordEvent.Start -> {
                        isRecording = true
                        shutterButton.text = "■ STOP"
                        statusText.text = "● RECORDING"
                    }

                    is VideoRecordEvent.Finalize -> {

                        isRecording = false
                        recording = null

                        shutterButton.text = "● REC"
                        statusText.text = "NINJACAM"

                        if (!event.hasError()) {
                            showMessage("Video tersimpan ✓")
                        } else {
                            showMessage("Gagal menyimpan video")
                        }
                    }
                }
            }

        } catch (e: Exception) {
            showMessage("Tidak dapat memulai rekaman")
        }
    }

    private fun stopVideoRecording() {
        recording?.stop()
    }
    
private fun updateZoomControls() {

    val state = camera?.cameraInfo?.zoomState?.value
        ?: return

    val minZoom = state.minZoomRatio
    val maxZoom = state.maxZoomRatio

    zoomSlider.progress = 0

    zoomText.text = String.format(
        Locale.US,
        "%.1f×",
        minZoom
    )

    zoomSlider.max = 1000

    zoomSlider.isEnabled = maxZoom > minZoom
}
    
    private fun applySliderZoom(progress: Int) {

        val state = camera?.cameraInfo?.zoomState?.value
            ?: return

        val ratio = state.minZoomRatio +
            (state.maxZoomRatio - state.minZoomRatio) *
            progress / 1000f

        applyZoom(ratio)
    }

    private fun applyZoom(ratio: Float) {

        val state = camera?.cameraInfo?.zoomState?.value
            ?: return

        val safeRatio = ratio.coerceIn(
            state.minZoomRatio,
            state.maxZoomRatio
        )

        camera?.cameraControl?.setZoomRatio(safeRatio)

        zoomText.text = String.format(
            Locale.US,
            "%.1f×",
            safeRatio
        )
    }

    private fun showMessage(message: String) {
        Toast.makeText(
            this,
            message,
            Toast.LENGTH_SHORT
        ).show()
    }

    override fun onDestroy() {
        recording?.stop()
        cameraProvider?.unbindAll()
        super.onDestroy()
    }
}
