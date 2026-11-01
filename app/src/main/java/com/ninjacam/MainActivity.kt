package com.ninjacam

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.Gravity
import android.view.View
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
import androidx.camera.camera2.interop.Camera2Interop
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
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var statusText: TextView
    private lateinit var shutterButton: Button
    private lateinit var switchButton: ImageButton
    private lateinit var galleryButton: Button

    private lateinit var flashButton: Button
    private lateinit var hdrButton: Button
    private lateinit var timerButton: Button
    private lateinit var settingsButton: Button

    private lateinit var zoomSlider: SeekBar
    private lateinit var zoomText: TextView
    private lateinit var zoomButtonsLayout: LinearLayout
    private lateinit var bottomPanel: LinearLayout

    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null

    private var imageCapture: ImageCapture? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null

    private var isFrontCamera = false
    private var isVideoMode = false
    private var isAstroMode = false
    private var isCameraReady = false
    private var isRecording = false
    private var isStartingCamera = false

    private var flashMode = ImageCapture.FLASH_MODE_AUTO
    private var hdrEnabled = false
    private var timerSeconds = 0

    private val quickZoomButtons = mutableListOf<Button>()

    private val cameraPermission =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (granted) {
                startCamera()
            } else {
                showMessage("Izin kamera diperlukan")
            }
        }

    private val audioPermission =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (granted) {
                startVideoRecording(true)
            } else {
                showMessage(
                    "Mikrofon ditolak — video tanpa audio"
                )
                startVideoRecording(false)
            }
        }

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(savedInstanceState)

        createCameraUi()

        if (
            hasPermission(
                Manifest.permission.CAMERA
            )
        ) {
            startCamera()
        } else {
            cameraPermission.launch(
                Manifest.permission.CAMERA
            )
        }
    }

    private fun hasPermission(
        permission: String
    ): Boolean {

        return ContextCompat.checkSelfPermission(
            this,
            permission
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun createCameraUi() {

        val root = FrameLayout(this)

        previewView = PreviewView(this).apply {
            scaleType =
                PreviewView.ScaleType.FILL_CENTER

            implementationMode =
                PreviewView.ImplementationMode.PERFORMANCE
        }

        root.addView(
            previewView,
            FrameLayout.LayoutParams(
                -1,
                -1
            )
        )

        /*
         * TOP BAR
         */

        val topBar =
            LinearLayout(this).apply {

                orientation =
                    LinearLayout.HORIZONTAL

                gravity =
                    Gravity.CENTER_VERTICAL

                setPadding(
                    16,
                    18,
                    16,
                    8
                )
            }

        statusText =
            TextView(this).apply {

                text =
                    "NINJACAM • PHOTO"

                textSize = 16f

                setTextColor(
                    Color.WHITE
                )

                setShadowLayer(
                    6f,
                    0f,
                    2f,
                    Color.BLACK
                )
            }

        topBar.addView(
            statusText,
            LinearLayout.LayoutParams(
                0,
                52,
                1f
            )
        )

        flashButton =
            topButton("⚡ AUTO") {
                cycleFlash()
            }

        hdrButton =
            topButton("HDR") {
                hdrEnabled =
                    !hdrEnabled

                hdrButton.text =
                    if (hdrEnabled) {
                        "HDR ON"
                    } else {
                        "HDR"
                    }

                showMessage(
                    if (hdrEnabled) {
                        "HDR diaktifkan"
                    } else {
                        "HDR dimatikan"
                    }
                )
            }

        timerButton =
            topButton("TIMER") {
                cycleTimer()
            }

        settingsButton =
            topButton("⚙") {
                showMessage(
                    "Settings akan ditambahkan"
                )
            }

        topBar.addView(
            flashButton,
            topSmallParams()
        )

        topBar.addView(
            hdrButton,
            topSmallParams()
        )

        topBar.addView(
            timerButton,
            topSmallParams()
        )

        topBar.addView(
            settingsButton,
            LinearLayout.LayoutParams(
                48,
                44
            )
        )

        root.addView(
            topBar,
            FrameLayout.LayoutParams(
                -1,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP
            )
        )

        /*
         * ZOOM TEXT
         */

        zoomText =
            TextView(this).apply {

                text = "1.0×"

                textSize = 16f

                gravity =
                    Gravity.CENTER

                setTextColor(
                    Color.WHITE
                )

                background =
                    roundBackground(
                        Color.argb(
                            100,
                            0,
                            0,
                            0
                        ),
                        999f
                    )

                setPadding(
                    16,
                    4,
                    16,
                    4
                )
            }

        root.addView(
            zoomText,
            FrameLayout.LayoutParams(
                82,
                42,
                Gravity.CENTER_HORIZONTAL or
                    Gravity.CENTER_VERTICAL
            ).apply {
                topMargin = 65
            }
        )

        /*
         * BOTTOM PANEL
         */

        bottomPanel =
            LinearLayout(this).apply {

                orientation =
                    LinearLayout.VERTICAL

                gravity =
                    Gravity.CENTER

                setPadding(
                    14,
                    10,
                    14,
                    18
                )

                background =
                    roundBackground(
                        Color.argb(
                            145,
                            0,
                            0,
                            0
                        ),
                        32f
                    )
            }

        /*
         * ZOOM BUTTONS
         */

        zoomButtonsLayout =
            LinearLayout(this).apply {

                orientation =
                    LinearLayout.HORIZONTAL

                gravity =
                    Gravity.CENTER
            }

        bottomPanel.addView(
            zoomButtonsLayout,
            LinearLayout.LayoutParams(
                -1,
                46
            )
        )

        /*
         * ZOOM SLIDER
         */

        zoomSlider =
            SeekBar(this).apply {

                max = 1000
                progress = 0

                setOnSeekBarChangeListener(
                    object :
                        SeekBar.OnSeekBarChangeListener {

                        override fun onProgressChanged(
                            seekBar: SeekBar?,
                            progress: Int,
                            fromUser: Boolean
                        ) {

                            if (fromUser) {
                                applySliderZoom(
                                    progress
                                )
                            }
                        }

                        override fun onStartTrackingTouch(
                            seekBar: SeekBar?
                        ) {
                        }

                        override fun onStopTrackingTouch(
                            seekBar: SeekBar?
                        ) {
                        }
                    }
                )
            }

        bottomPanel.addView(
            zoomSlider,
            LinearLayout.LayoutParams(
                -1,
                36
            )
        )

        /*
         * MODE ROW
         */

        val modeRow =
            LinearLayout(this).apply {

                orientation =
                    LinearLayout.HORIZONTAL

                gravity =
                    Gravity.CENTER
            }

        val photoMode =
            modeChip("PHOTO") {
                setPhotoMode()
            }

        val videoMode =
            modeChip("VIDEO") {
                setVideoMode()
            }

        val nightMode =
            modeChip("NIGHT") {
                showMessage(
                    "Night Mode sedang disiapkan"
                )
            }

        val astroMode =
            modeChip("ASTRO") {
                setAstroMode()
            }

        val proMode =
            modeChip("PRO") {
                showMessage(
                    "Pro Controls sedang disiapkan"
                )
            }

        val liveMode =
            modeChip("LIVE") {
                showMessage(
                    "Live Photo sedang disiapkan"
                )
            }

        modeRow.addView(photoMode)
        modeRow.addView(videoMode)
        modeRow.addView(nightMode)
        modeRow.addView(astroMode)
        modeRow.addView(proMode)
        modeRow.addView(liveMode)

        bottomPanel.addView(
            modeRow,
            LinearLayout.LayoutParams(
                -1,
                48
            )
        )

        /*
         * SHUTTER ROW
         */

        val shutterRow =
            FrameLayout(this)

        galleryButton =
            Button(this).apply {

                text = "▣"

                textSize = 22f

                setTextColor(
                    Color.WHITE
                )

                background =
                    roundBackground(
                        Color.argb(
                            110,
                            255,
                            255,
                            255
                        ),
                        999f
                    )

                setOnClickListener {
                    showMessage(
                        "Gallery NinjaCam"
                    )
                }
            }

        shutterRow.addView(
            galleryButton,
            FrameLayout.LayoutParams(
                58,
                58,
                Gravity.START or
                    Gravity.CENTER_VERTICAL
            )
        )

        shutterButton =
            Button(this).apply {

                text = "●"

                textSize = 27f

                setTextColor(
                    Color.BLACK
                )

                background =
                    shutterBackground()

                setOnClickListener {
                    onShutterPressed()
                }
            }

        shutterRow.addView(
            shutterButton,
            FrameLayout.LayoutParams(
                86,
                86,
                Gravity.CENTER
            )
        )

        /*
         * SWITCH CAMERA
         * DI SEBELAH KANAN SHUTTER
         */

        switchButton =
            ImageButton(this).apply {

                setImageResource(
                    android.R.drawable
                        .ic_menu_camera
                )

                setColorFilter(
                    Color.WHITE
                )

                background =
                    roundBackground(
                        Color.argb(
                            110,
                            255,
                            255,
                            255
                        ),
                        999f
                    )

                contentDescription =
                    "Ganti kamera"

                setPadding(
                    14,
                    14,
                    14,
                    14
                )

                setOnClickListener {
                    switchCamera()
                }
            }

        shutterRow.addView(
            switchButton,
            FrameLayout.LayoutParams(
                58,
                58,
                Gravity.END or
                    Gravity.CENTER_VERTICAL
            )
        )

        bottomPanel.addView(
            shutterRow,
            LinearLayout.LayoutParams(
                -1,
                94
            )
        )

        root.addView(
            bottomPanel,
            FrameLayout.LayoutParams(
                -1,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM
            ).apply {

                setMargins(
                    8,
                    0,
                    8,
                    8
                )
            }
        )

        setContentView(root)

        createQuickZoomButtons()
    }

    private fun topButton(
        textValue: String,
        action: () -> Unit
    ): Button {

        return Button(this).apply {

            text = textValue

            textSize = 10f

            setTextColor(
                Color.WHITE
            )

            background =
                roundBackground(
                    Color.argb(
                        105,
                        0,
                        0,
                        0
                    ),
                    999f
                )

            setPadding(
                8,
                0,
                8,
                0
            )

            setOnClickListener {
                action()
            }
        }
    }

    private fun topSmallParams():
        LinearLayout.LayoutParams {

        return LinearLayout.LayoutParams(
            58,
            44
        ).apply {

            setMargins(
                2,
                0,
                2,
                0
            )
        }
    }

    private fun modeChip(
        textValue: String,
        action: () -> Unit
    ): Button {

        return Button(this).apply {

            text = textValue

            textSize = 9f

            setTextColor(
                Color.WHITE
            )

            background =
                roundBackground(
                    Color.argb(
                        65,
                        255,
                        255,
                        255
                    ),
                    999f
                )

            setPadding(
                8,
                0,
                8,
                0
            )

            setOnClickListener {
                action()
            }

            layoutParams =
                LinearLayout.LayoutParams(
                    0,
                    42,
                    1f
                ).apply {

                    setMargins(
                        2,
                        0,
                        2,
                        0
                    )
                }
        }
    }

    private fun roundBackground(
        color: Int,
        radius: Float
    ): GradientDrawable {

        return GradientDrawable().apply {

            setColor(color)

            cornerRadius = radius
        }
    }

    private fun shutterBackground():
        GradientDrawable {

        return GradientDrawable().apply {

            shape =
                GradientDrawable.OVAL

            setColor(
                Color.WHITE
            )

            setStroke(
                5,
                Color.argb(
                    210,
                    255,
                    255,
                    255
                )
            )
        }
    }

    private fun createQuickZoomButtons() {

        zoomButtonsLayout
            .removeAllViews()

        quickZoomButtons.clear()

        val levels =
            listOf(
                0.5f,
                1.0f,
                2.0f,
                5.0f
            )

        for (level in levels) {

            val button =
                Button(this).apply {

                    text =
                        if (level == 0.5f) {
                            "0.5×"
                        } else {
                            String.format(
                                Locale.US,
                                "%.0f×",
                                level
                            )
                        }

                    textSize = 11f

                    setTextColor(
                        Color.WHITE
                    )

                    background =
                        roundBackground(
                            Color.argb(
                                75,
                                255,
                                255,
                                255
                            ),
                            999f
                        )

                    setPadding(
                        5,
                        0,
                        5,
                        0
                    )

                    setOnClickListener {
                        applyQuickZoom(
                            level
                        )
                    }
                }

            val params =
                LinearLayout.LayoutParams(
                    58,
                    42
                )

            params.setMargins(
                3,
                2,
                3,
                2
            )

            zoomButtonsLayout.addView(
                button,
                params
            )

            quickZoomButtons.add(
                button
            )
        }
    }

    /*
     * CAMERA START
     */

    private fun startCamera() {

        if (isStartingCamera) {
            return
        }

        if (
            !hasPermission(
                Manifest.permission.CAMERA
            )
        ) {
            return
        }

        isStartingCamera = true
        isCameraReady = false
        shutterButton.isEnabled = false

        val future =
            ProcessCameraProvider
                .getInstance(this)

        future.addListener({

            try {

                val provider =
                    future.get()

                cameraProvider =
                    provider

                val selector =
                    if (isFrontCamera) {
                        CameraSelector
                            .DEFAULT_FRONT_CAMERA
                    } else {
                        CameraSelector
                            .DEFAULT_BACK_CAMERA
                    }

                if (
                    !provider.hasCamera(
                        selector
                    )
                ) {

                    showMessage(
                        "Kamera tidak tersedia"
                    )

                    isFrontCamera = false

                    return@addListener
                }

                val preview =
                    Preview.Builder()
                        .build()

                preview.surfaceProvider =
                    previewView
                        .surfaceProvider

                val captureBuilder =
                    ImageCapture.Builder()
                        .setCaptureMode(
                            ImageCapture
                                .CAPTURE_MODE_MINIMIZE_LATENCY
                        )
                        .setFlashMode(
                            flashMode
                        )

                /*
                 * ASTRO
                 *
                 * Hanya aktif untuk kamera belakang.
                 */

                if (
                    isAstroMode &&
                    !isFrontCamera
                ) {

                    try {

                        val extender =
                            Camera2Interop
                                .Extender(
                                    captureBuilder
                                )

                        extender
                            .setCaptureRequestOption(
                                android.hardware
                                    .camera2
                                    .CaptureRequest
                                    .CONTROL_AE_MODE,

                                android.hardware
                                    .camera2
                                    .CaptureRequest
                                    .CONTROL_AE_MODE_OFF
                            )

                        extender
                            .setCaptureRequestOption(
                                android.hardware
                                    .camera2
                                    .CaptureRequest
                                    .SENSOR_EXPOSURE_TIME,

                                4_000_000_000L
                            )

                        extender
                            .setCaptureRequestOption(
                                android.hardware
                                    .camera2
                                    .CaptureRequest
                                    .SENSOR_SENSITIVITY,

                                800
                            )

                    } catch (
                        ignored: Exception
                    ) {
                        /*
                         * Kalau Astro Camera2
                         * tidak tersedia, kamera
                         * tetap dibuka normal.
                         */
                    }
                }

                val capture =
                    captureBuilder.build()

                val recorder =
                    Recorder.Builder()
                        .setQualitySelector(
                            QualitySelector.from(
                                Quality.HD
                            )
                        )
                        .build()

                val video =
                    VideoCapture
                        .withOutput(
                            recorder
                        )

                /*
                 * Unbind hanya setelah semua
                 * use case berhasil dibuat.
                 */

                provider.unbindAll()

                camera =
                    provider.bindToLifecycle(
                        this,
                        selector,
                        preview,
                        capture,
                        video
                    )

                imageCapture =
                    capture

                videoCapture =
                    video

                isCameraReady = true
                shutterButton.isEnabled =
                    true

                updateZoomControls()

            } catch (e: Exception) {

                isCameraReady = false

                shutterButton.isEnabled =
                    false

                showMessage(
                    "Gagal membuka kamera"
                )

            } finally {

                isStartingCamera = false
            }

        }, ContextCompat.getMainExecutor(this))
    }

    /*
     * MODE SWITCHING
     */

    private fun setPhotoMode() {

        if (isRecording) {
            return
        }

        isVideoMode = false
        isAstroMode = false

        statusText.text =
            "NINJACAM • PHOTO"

        restartCameraSafely()
    }

    private fun setVideoMode() {

        if (isRecording) {
            return
        }

        isVideoMode = true
        isAstroMode = false

        statusText.text =
            "NINJACAM • VIDEO"

        restartCameraSafely()
    }

    private fun setAstroMode() {

        if (isRecording) {
            return
        }

        if (isFrontCamera) {
            isFrontCamera = false
        }

        isVideoMode = false
        isAstroMode = true

        statusText.text =
            "NINJACAM • ASTRO"

        restartCameraSafely()
    }

    private fun restartCameraSafely() {

        isCameraReady = false
        shutterButton.isEnabled = false

        /*
         * Jangan langsung memanggil
         * startCamera berkali-kali.
         */

        Handler(
            Looper.getMainLooper()
        ).postDelayed({

            if (
                !isFinishing &&
                !isDestroyed
            ) {
                startCamera()
            }

        }, 120)
    }

    /*
     * CAMERA SWITCH
     */

    private fun switchCamera() {

        if (
            !isCameraReady ||
            isRecording ||
            isStartingCamera
        ) {
            return
        }

        if (isAstroMode) {

            showMessage(
                "Astro memakai kamera belakang"
            )

            return
        }

        val next =
            !isFrontCamera

        val selector =
            if (next) {
                CameraSelector
                    .DEFAULT_FRONT_CAMERA
            } else {
                CameraSelector
                    .DEFAULT_BACK_CAMERA
            }

        val provider =
            cameraProvider
                ?: return

        if (
            !provider.hasCamera(
                selector
            )
        ) {

            showMessage(
                "Kamera tidak tersedia"
            )

            return
        }

        isFrontCamera = next

        restartCameraSafely()
    }

    /*
     * FLASH
     */

    private fun cycleFlash() {

        flashMode =
            when (flashMode) {

                ImageCapture.FLASH_MODE_AUTO ->
                    ImageCapture.FLASH_MODE_ON

                ImageCapture.FLASH_MODE_ON ->
                    ImageCapture.FLASH_MODE_OFF

                else ->
                    ImageCapture.FLASH_MODE_AUTO
            }

        flashButton.text =
            when (flashMode) {

                ImageCapture.FLASH_MODE_AUTO ->
                    "⚡ AUTO"

                ImageCapture.FLASH_MODE_ON ->
                    "⚡ ON"

                else ->
                    "⚡ OFF"
            }

        if (isCameraReady) {
            restartCameraSafely()
        }
    }

    /*
     * TIMER
     */

    private fun cycleTimer() {

        timerSeconds =
            when (timerSeconds) {

                0 -> 3

                3 -> 5

                else -> 0
            }

        timerButton.text =
            when (timerSeconds) {

                0 ->
                    "TIMER"

                else ->
                    "⏱ ${timerSeconds}s"
            }
    }

    /*
     * SHUTTER
     */

    private fun onShutterPressed() {

        if (!isCameraReady) {
            return
        }

        if (isVideoMode) {

            if (isRecording) {
                stopVideoRecording()
            } else {
                requestAudioAndRecord()
            }

            return
        }

        if (timerSeconds > 0) {

            shutterButton.isEnabled =
                false

            showMessage(
                "Foto dalam ${timerSeconds} detik…"
            )

            Handler(
                Looper.getMainLooper()
            ).postDelayed({

                if (!isFinishing) {
                    takePhoto()
                }

            }, timerSeconds * 1000L)

        } else {

            takePhoto()
        }
    }

    /*
     * PHOTO
     */

    private fun takePhoto() {

        val capture =
            imageCapture
                ?: return

        shutterButton.isEnabled =
            false

        if (isAstroMode) {

            showMessage(
                "Astro exposure 4 detik…"
            )
        }

        val values =
            ContentValues().apply {

                put(
                    MediaStore.Images.Media
                        .DISPLAY_NAME,

                    if (isAstroMode) {
                        "NinjaCam_Astro_${
                            System.currentTimeMillis()
                        }.jpg"
                    } else {
                        "NinjaCam_${
                            System.currentTimeMillis()
                        }.jpg"
                    }
                )

                put(
                    MediaStore.Images.Media
                        .MIME_TYPE,

                    "image/jpeg"
                )

                if (Build.VERSION.SDK_INT >= 29) {

                    put(
                        MediaStore.Images.Media
                            .RELATIVE_PATH,

                        "Pictures/NinjaCam"
                    )
                }
            }

        val options =
            ImageCapture
                .OutputFileOptions
                .Builder(
                    contentResolver,
                    MediaStore.Images.Media
                        .EXTERNAL_CONTENT_URI,
                    values
                )
                .build()

        capture.takePicture(

            options,

            ContextCompat
                .getMainExecutor(this),

            object :
                ImageCapture
                    .OnImageSavedCallback {

                override fun onImageSaved(
                    result:
                    ImageCapture
                        .OutputFileResults
                ) {

                    shutterButton.isEnabled =
                        true

                    showMessage(
                        if (isAstroMode) {
                            "Foto Astro tersimpan ✓"
                        } else {
                            "Foto tersimpan ✓"
                        }
                    )
                }

                override fun onError(
                    exception:
                    ImageCaptureException
                ) {

                    shutterButton.isEnabled =
                        true

                    showMessage(
                        "Gagal mengambil foto"
                    )
                }
            }
        )
    }

    /*
     * AUDIO PERMISSION
     */

    private fun requestAudioAndRecord() {

        if (
            hasPermission(
                Manifest.permission.RECORD_AUDIO
            )
        ) {

            startVideoRecording(true)

        } else {

            audioPermission.launch(
                Manifest.permission.RECORD_AUDIO
            )
        }
    }

    /*
     * VIDEO
     */

    private fun startVideoRecording(
        withAudio: Boolean
    ) {

        val video =
            videoCapture
                ?: return

        val values =
            ContentValues().apply {

                put(
                    MediaStore.Video.Media
                        .DISPLAY_NAME,

                    "NinjaCam_${
                        System.currentTimeMillis()
                    }.mp4"
                )

                put(
                    MediaStore.Video.Media
                        .MIME_TYPE,

                    "video/mp4"
                )

                if (Build.VERSION.SDK_INT >= 29) {

                    put(
                        MediaStore.Video.Media
                            .RELATIVE_PATH,

                        "Movies/NinjaCam"
                    )
                }
            }

        val outputOptions =
            MediaStoreOutputOptions
                .Builder(
                    contentResolver,
                    MediaStore.Video.Media
                        .EXTERNAL_CONTENT_URI
                )
                .setContentValues(values)
                .build()

        try {

            var pending =
                video.output
                    .prepareRecording(
                        this,
                        outputOptions
                    )

            if (withAudio) {

                pending =
                    pending.withAudioEnabled()
            }

            recording =
                pending.start(

                    ContextCompat
                        .getMainExecutor(this)

                ) { event ->

                    when (event) {

                        is VideoRecordEvent.Start -> {

                            isRecording = true

                            shutterButton.text =
                                "■"

                            statusText.text =
                                if (withAudio) {
                                    "● REC • AUDIO"
                                } else {
                                    "● REC • SILENT"
                                }
                        }

                        is VideoRecordEvent.Finalize -> {

                            isRecording = false

                            recording = null

                            shutterButton.text =
                                "●"

                            statusText.text =
                                "NINJACAM • VIDEO"

                            if (
                                !event.hasError()
                            ) {

                                showMessage(
                                    if (withAudio) {
                                        "Video + audio tersimpan ✓"
                                    } else {
                                        "Video tersimpan ✓"
                                    }
                                )

                            } else {

                                showMessage(
                                    "Gagal menyimpan video"
                                )
                            }
                        }
                    }
                }

        } catch (e: Exception) {

            isRecording = false
            recording = null

            showMessage(
                "Tidak dapat memulai rekaman"
            )
        }
    }

    private fun stopVideoRecording() {
        recording?.stop()
    }

    /*
     * ZOOM
     */

    private fun updateZoomControls() {

        val state =
            camera
                ?.cameraInfo
                ?.zoomState
                ?.value
                ?: return

        val minZoom =
            state.minZoomRatio

        val maxZoom =
            state.maxZoomRatio

        zoomSlider.progress =
            calculateZoomProgress(
                minZoom
            )

        zoomSlider.isEnabled =
            maxZoom > minZoom

        zoomText.text =
            String.format(
                Locale.US,
                "%.1f×",
                minZoom
            )

        for (
            button in quickZoomButtons
        ) {

            val level =
                when (
                    button.text.toString()
                ) {

                    "0.5×" -> 0.5f
                    "1×" -> 1.0f
                    "2×" -> 2.0f
                    "5×" -> 5.0f

                    else -> 1.0f
                }

            val available =
                level >= minZoom &&
                    level <= maxZoom

            button.isEnabled =
                available

            button.alpha =
                if (available) {
                    1f
                } else {
                    0.3f
                }
        }
    }

    private fun applySliderZoom(
        progress: Int
    ) {

        val state =
            camera
                ?.cameraInfo
                ?.zoomState
                ?.value
                ?: return

        val ratio =
            state.minZoomRatio +
                (
                    state.maxZoomRatio -
                        state.minZoomRatio
                    ) *
                progress /
                1000f

        applyZoom(ratio)
    }

    private fun applyQuickZoom(
        level: Float
    ) {

        val state =
            camera
                ?.cameraInfo
                ?.zoomState
                ?.value
                ?: return

        if (
            level < state.minZoomRatio ||
            level > state.maxZoomRatio
        ) {

            showMessage(
                String.format(
                    Locale.US,
                    "Zoom %.1f× tidak tersedia",
                    level
                )
            )

            return
        }

        applyZoom(level)

        zoomSlider.progress =
            calculateZoomProgress(
                level
            )
    }

    private fun calculateZoomProgress(
        zoom: Float
    ): Int {

        val state =
            camera
                ?.cameraInfo
                ?.zoomState
                ?.value
                ?: return 0

        val range =
            state.maxZoomRatio -
                state.minZoomRatio

        if (range <= 0f) {
            return 0
        }

        return (
            (
                (
                    zoom -
                        state.minZoomRatio
                ) / range
            ) * 1000f
        )
            .roundToInt()
            .coerceIn(
                0,
                1000
            )
    }

    private fun applyZoom(
        ratio: Float
    ) {

        val state =
            camera
                ?.cameraInfo
                ?.zoomState
                ?.value
                ?: return

        val safeRatio =
            ratio.coerceIn(
                state.minZoomRatio,
                state.maxZoomRatio
            )

        camera
            ?.cameraControl
            ?.setZoomRatio(
                safeRatio
            )

        zoomText.text =
            String.format(
                Locale.US,
                "%.1f×",
                safeRatio
            )
    }

    private fun showMessage(
        message: String
    ) {

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

    /*
     * GRID CLASS
     *
     * Disiapkan untuk grid kamera
     * pada tahap UI berikutnya.
     */

    private class GridOverlay(
        context: android.content.Context
    ) : View(context) {

        private val paint =
            android.graphics.Paint().apply {

                color =
                    Color.argb(
                        120,
                        255,
                        255,
                        255
                    )

                strokeWidth = 1.5f

                style =
                    android.graphics.Paint.Style.STROKE
            }

        override fun onDraw(
            canvas:
            android.graphics.Canvas
        ) {

            super.onDraw(canvas)

            val width =
                width.toFloat()

            val height =
                height.toFloat()

            canvas.drawLine(
                width / 3f,
                0f,
                width / 3f,
                height,
                paint
            )

            canvas.drawLine(
                width * 2f / 3f,
                0f,
                width * 2f / 3f,
                height,
                paint
            )

            canvas.drawLine(
                0f,
                height / 3f,
                width,
                height / 3f,
                paint
            )

            canvas.drawLine(
                0f,
                height * 2f / 3f,
                width,
                height * 2f / 3f,
                paint
            )
        }
    }
}
