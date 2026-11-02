package com.ninjacam

import android.Manifest
import android.animation.Animator
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.Gravity
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import android.widget.VideoView

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

    private lateinit var rootContainer: FrameLayout
    private lateinit var previewView: PreviewView
    private lateinit var focusRing: View

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
    private lateinit var modeRow: LinearLayout

    private val modeButtons = mutableMapOf<String, Button>()
    private val quickZoomButtons = mutableMapOf<Float, Button>()

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
    private var currentZoom = 1f

    private lateinit var scaleDetector: ScaleGestureDetector

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
                showMessage("Mikrofon ditolak — video tanpa audio")
                startVideoRecording(false)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        createCameraUi()
        showLoadingScreen()
    }

    private fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            permission
        ) == PackageManager.PERMISSION_GRANTED
    }

    // =========================================================
    // VIDEO INTRO
    // =========================================================

    private fun showLoadingScreen() {

        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE

        val intro = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            alpha = 1f
        }

        rootContainer.addView(
            intro,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        // Cari MP4 langsung di app/src/main/assets/
        val videoFile = try {
            assets.list("")
                ?.firstOrNull {
                    it.lowercase(Locale.US).endsWith(".mp4")
                }
        } catch (e: Exception) {
            null
        }

        val videoView = VideoView(this).apply {
            setBackgroundColor(Color.BLACK)
        }

        intro.addView(
            videoView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        // Kalau MP4 tidak ditemukan, JANGAN masuk kamera.
        if (videoFile == null) {

            val errorText = TextView(this).apply {
                text = "INTRO VIDEO NOT FOUND"
                textSize = 15f
                gravity = Gravity.CENTER
                setTextColor(Color.WHITE)
            }

            intro.addView(
                errorText,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER
                )
            )

            return
        }

        videoView.setVideoURI(
            Uri.parse(
                "file:///android_asset/$videoFile"
            )
        )

        // Lapisan gelap tipis
        val bottomShade = View(this).apply {

            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(
                    Color.TRANSPARENT,
                    Color.argb(45, 0, 0, 0),
                    Color.argb(175, 0, 0, 0)
                )
            )
        }

        intro.addView(
            bottomShade,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                350,
                Gravity.BOTTOM
            )
        )

        // =====================================================
        // TEKS INTRO
        // Posisi sekitar 60% tinggi layar
        // =====================================================

        val textContainer =
            LinearLayout(this).apply {

                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL

                setPadding(
                    24,
                    0,
                    24,
                    0
                )

                alpha = 0f
            }

        val title =
            TextView(this).apply {

                text = "NinjaCamera"
                textSize = 30f
                gravity = Gravity.CENTER

                setTextColor(Color.WHITE)

                typeface =
                    android.graphics.Typeface.create(
                        "sans-serif",
                        android.graphics.Typeface.BOLD
                    )

                setShadowLayer(
                    14f,
                    0f,
                    2f,
                    Color.BLACK
                )
            }

        val developer =
            TextView(this).apply {

                text = "Developer:Zackss"
                textSize = 15f
                gravity = Gravity.CENTER

                setTextColor(Color.WHITE)

                setShadowLayer(
                    8f,
                    0f,
                    1f,
                    Color.BLACK
                )
            }

        val love =
            TextView(this).apply {

                text = "Fahiraa My Loveee"
                textSize = 13f
                gravity = Gravity.CENTER

                setTextColor(
                    Color.argb(
                        215,
                        255,
                        255,
                        255
                    )
                )

                setShadowLayer(
                    7f,
                    0f,
                    1f,
                    Color.BLACK
                )
            }

        textContainer.addView(
            title,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                48
            )
        )

        textContainer.addView(
            developer,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                32
            )
        )

        textContainer.addView(
            love,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                30
            )
        )

        val textParams =
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                115,
                Gravity.TOP or Gravity.CENTER_HORIZONTAL
            ).apply {

                topMargin =
                    (
                        resources.displayMetrics.heightPixels *
                            0.60f
                        ).roundToInt()
            }

        intro.addView(
            textContainer,
            textParams
        )

        // Animasi teks masuk
        textContainer.animate()
            .alpha(1f)
            .setDuration(800L)
            .setStartDelay(250L)
            .start()

        // =====================================================
        // PERCENTAGE
        // =====================================================

        val progressText =
            TextView(this).apply {

                text = "0%"
                textSize = 12f
                gravity = Gravity.CENTER

                setTextColor(Color.WHITE)

                setShadowLayer(
                    7f,
                    0f,
                    1f,
                    Color.BLACK
                )
            }

        intro.addView(
            progressText,
            FrameLayout.LayoutParams(
                80,
                32,
                Gravity.BOTTOM or
                    Gravity.CENTER_HORIZONTAL
            ).apply {
                bottomMargin = 75
            }
        )

        // =====================================================
        // PROGRESS BAR
        // =====================================================

        val progressBackground =
            View(this).apply {

                background =
                    roundBackground(
                        Color.argb(
                            80,
                            255,
                            255,
                            255
                        ),
                        999f
                    )
            }

        intro.addView(
            progressBackground,
            FrameLayout.LayoutParams(
                210,
                4,
                Gravity.BOTTOM or
                    Gravity.CENTER_HORIZONTAL
            ).apply {
                bottomMargin = 60
            }
        )

        val progressBar =
            View(this).apply {

                background =
                    roundBackground(
                        Color.WHITE,
                        999f
                    )

                scaleX = 0f
                pivotX = 0f
            }

        intro.addView(
            progressBar,
            FrameLayout.LayoutParams(
                210,
                4,
                Gravity.BOTTOM or
                    Gravity.CENTER_HORIZONTAL
            ).apply {
                bottomMargin = 60
            }
        )

        // =====================================================
        // VIDEO PREPARED
        // =====================================================

        videoView.setOnPreparedListener { mediaPlayer ->

            mediaPlayer.isLooping = false

            // Pastikan suara video intro aktif
            mediaPlayer.setVolume(
                1.0f,
                1.0f
            )

            val duration =
                mediaPlayer.duration.toLong()

            if (duration <= 0L) {

                progressText.text =
                    "VIDEO ERROR"

                return@setOnPreparedListener
            }

            // Mulai video
            videoView.start()

            val handler =
                Handler(
                    Looper.getMainLooper()
                )

            val updateProgress =
                object : Runnable {

                    override fun run() {

                        if (
                            isFinishing ||
                            isDestroyed
                        ) {
                            return
                        }

                        val position =
                            videoView.currentPosition
                                .toLong()

                        val percentage =
                            (
                                position.toDouble() /
                                    duration.toDouble() *
                                    100.0
                            )
                                .roundToInt()
                                .coerceIn(
                                    0,
                                    100
                                )

                        progressText.text =
                            "$percentage%"

                        progressBar.scaleX =
                            percentage / 100f

                        if (
                            videoView.isPlaying &&
                            percentage < 100
                        ) {

                            handler.postDelayed(
                                this,
                                50L
                            )
                        }
                    }
                }

            handler.post(updateProgress)
        }

        // =====================================================
        // VIDEO ERROR
        // =====================================================

        videoView.setOnErrorListener {
                _, _, _ ->

            // PENTING:
            // Jangan panggil continueToCamera().
            // Jadi intro tidak akan diam-diam ke-skip.

            progressText.text =
                "INTRO VIDEO ERROR"

            progressBar.scaleX = 0f

            true
        }

        // =====================================================
        // VIDEO SELESAI
        // =====================================================

        videoView.setOnCompletionListener {

            progressText.text =
                "100%"

            progressBar.animate()
                .scaleX(1f)
                .setDuration(180L)
                .start()

            Handler(
                Looper.getMainLooper()
            ).postDelayed({

                if (
                    isFinishing ||
                    isDestroyed
                ) {
                    return@postDelayed
                }

                val fade =
                    ObjectAnimator.ofFloat(
                        intro,
                        View.ALPHA,
                        1f,
                        0f
                    ).apply {

                        duration = 650L

                        interpolator =
                            DecelerateInterpolator()
                    }

                fade.addListener(
                    object :
                        Animator.AnimatorListener {

                        override fun onAnimationStart(
                            animation: Animator
                        ) {
                        }

                        override fun onAnimationEnd(
                            animation: Animator
                        ) {

                            videoView.stopPlayback()

                            rootContainer.removeView(
                                intro
                            )

                            continueToCamera()
                        }

                        override fun onAnimationCancel(
                            animation: Animator
                        ) {
                        }

                        override fun onAnimationRepeat(
                            animation: Animator
                        ) {
                        }
                    }
                )

                fade.start()

            }, 350L)
        }
    }

    private fun continueToCamera() {

        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE

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

    // =========================================================
    // CAMERA UI
    // =========================================================

    private fun createCameraUi() {

        rootContainer =
            FrameLayout(this)

        rootContainer.setBackgroundColor(
            Color.BLACK
        )

        previewView =
            PreviewView(this).apply {

                scaleType =
                    PreviewView.ScaleType.FILL_CENTER

                implementationMode =
                    PreviewView.ImplementationMode.PERFORMANCE

                setBackgroundColor(
                    Color.BLACK
                )
            }

        rootContainer.addView(
            previewView,
            FrameLayout.LayoutParams(
                -1,
                -1
            )
        )

        focusRing =
            View(this).apply {

                background =
                    GradientDrawable().apply {

                        shape =
                            GradientDrawable.OVAL

                        setColor(
                            Color.TRANSPARENT
                        )

                        setStroke(
                            2,
                            Color.WHITE
                        )
                    }

                alpha = 0f
                visibility =
                    View.INVISIBLE
            }

        rootContainer.addView(
            focusRing,
            FrameLayout.LayoutParams(
                76,
                76
            )
        )

        setupPreviewGestures()
        createTopBar()
        createZoomUi()
        createBottomUi()

        setContentView(rootContainer)
    }

    private fun createTopBar() {

        val topBar =
            LinearLayout(this).apply {

                orientation =
                    LinearLayout.HORIZONTAL

                gravity =
                    Gravity.CENTER_VERTICAL

                setPadding(
                    14,
                    18,
                    14,
                    8
                )
            }

        statusText =
            TextView(this).apply {

                text =
                    "NINJACAM • PHOTO"

                textSize = 14f

                setTextColor(
                    Color.WHITE
                )

                typeface =
                    android.graphics.Typeface.DEFAULT_BOLD

                setShadowLayer(
                    8f,
                    0f,
                    2f,
                    Color.BLACK
                )
            }

        topBar.addView(
            statusText,
            LinearLayout.LayoutParams(
                0,
                48,
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

                animateButton(
                    hdrButton
                )

                showMessage(
                    if (hdrEnabled) {
                        "HDR diaktifkan"
                    } else {
                        "HDR dimatikan"
                    }
                )
            }

        timerButton =
            topButton("◷") {
                cycleTimer()
            }

        settingsButton =
            topButton("⚙") {
                showMessage(
                    "Settings NinjaCam"
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
            LinearLayout.LayoutParams(
                48,
                44
            ).apply {
                setMargins(
                    2,
                    0,
                    2,
                    0
                )
            }
        )

        topBar.addView(
            settingsButton,
            LinearLayout.LayoutParams(
                48,
                44
            )
        )

        rootContainer.addView(
            topBar,
            FrameLayout.LayoutParams(
                -1,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP
            )
        )
    }

    private fun createZoomUi() {

        zoomText =
            TextView(this).apply {

                text = "1.0×"
                textSize = 13f
                gravity = Gravity.CENTER

                setTextColor(
                    Color.WHITE
                )

                background =
                    roundBackground(
                        Color.argb(
                            95,
                            0,
                            0,
                            0
                        ),
                        999f
                    )

                setPadding(
                    12,
                    3,
                    12,
                    3
                )
            }

        rootContainer.addView(
            zoomText,
            FrameLayout.LayoutParams(
                70,
                38,
                Gravity.TOP or
                    Gravity.CENTER_HORIZONTAL
            ).apply {
                topMargin = 100
            }
        )

        zoomButtonsLayout =
            LinearLayout(this).apply {

                orientation =
                    LinearLayout.HORIZONTAL

                gravity =
                    Gravity.CENTER

                setPadding(
                    5,
                    3,
                    5,
                    3
                )

                background =
                    roundBackground(
                        Color.argb(
                            75,
                            0,
                            0,
                            0
                        ),
                        999f
                    )
            }

        rootContainer.addView(
            zoomButtonsLayout,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                43,
                Gravity.CENTER_HORIZONTAL or
                    Gravity.BOTTOM
            ).apply {
                bottomMargin = 365
            }
        )

        createQuickZoomButtons()

        zoomSlider =
            SeekBar(this).apply {

                max = 1000
                progress = 0
                alpha = 0.65f

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

        rootContainer.addView(
            zoomSlider,
            FrameLayout.LayoutParams(
                180,
                35,
                Gravity.CENTER_HORIZONTAL or
                    Gravity.BOTTOM
            ).apply {
                bottomMargin = 325
            }
        )
    }

    private fun createBottomUi() {

        bottomPanel =
            LinearLayout(this).apply {

                orientation =
                    LinearLayout.VERTICAL

                gravity =
                    Gravity.CENTER

                setPadding(
                    8,
                    6,
                    8,
                    12
                )

                background =
                    GradientDrawable().apply {

                        setColor(
                            Color.argb(
                                105,
                                0,
                                0,
                                0
                            )
                        )

                        setCornerRadii(
                            floatArrayOf(
                                34f, 34f,
                                34f, 34f,
                                0f, 0f,
                                0f, 0f
                            )
                        )
                    }
            }

        createModeRow()

        val shutterRow =
            FrameLayout(this)

        galleryButton =
            Button(this).apply {

                text = "▣"
                textSize = 20f

                setTextColor(
                    Color.WHITE
                )

                background =
                    roundBackground(
                        Color.argb(
                            90,
                            255,
                            255,
                            255
                        ),
                        999f
                    )

                setOnClickListener {

                    animateButton(this)

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
            ).apply {
                leftMargin = 8
            }
        )

        shutterButton =
            Button(this).apply {

                text = "●"
                textSize = 28f

                setTextColor(
                    Color.BLACK
                )

                background =
                    shutterBackground()

                setOnClickListener {

                    animateShutter()
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

        switchButton =
            ImageButton(this).apply {

                setImageResource(
                    android.R.drawable.ic_menu_camera
                )

                setColorFilter(
                    Color.WHITE
                )

                background =
                    roundBackground(
                        Color.argb(
                            90,
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

                    animateButton(this)
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
            ).apply {
                rightMargin = 8
            }
        )

        bottomPanel.addView(
            shutterRow,
            LinearLayout.LayoutParams(
                -1,
                94
            )
        )

        rootContainer.addView(
            bottomPanel,
            FrameLayout.LayoutParams(
                -1,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM
            )
        )
    }

    private fun createModeRow() {

        modeRow =
            LinearLayout(this).apply {

                orientation =
                    LinearLayout.HORIZONTAL

                gravity =
                    Gravity.CENTER

                setPadding(
                    4,
                    4,
                    4,
                    5
                )
            }

        val modes =
            listOf(
                "NIGHT",
                "PHOTO",
                "VIDEO",
                "ASTRO",
                "PRO",
                "LIVE"
            )

        for (mode in modes) {

            val button =
                modeChip(mode) {

                    when (mode) {

                        "PHOTO" ->
                            setPhotoMode()

                        "VIDEO" ->
                            setVideoMode()

                        "ASTRO" ->
                            setAstroMode()

                        "NIGHT" ->
                            showMessage(
                                "Night Mode sedang disiapkan"
                            )

                        "PRO" ->
                            showMessage(
                                "Pro Controls sedang disiapkan"
                            )

                        "LIVE" ->
                            showMessage(
                                "Live Photo sedang disiapkan"
                            )
                    }
                }

            modeButtons[mode] =
                button

            modeRow.addView(
                button
            )
        }

        bottomPanel.addView(
            modeRow,
            LinearLayout.LayoutParams(
                -1,
                48
            )
        )

        updateModeUi("PHOTO")
    }

    private fun setupPreviewGestures() {

        scaleDetector =
            ScaleGestureDetector(
                this,
                object :
                    ScaleGestureDetector.SimpleOnScaleGestureListener() {

                    override fun onScale(
                        detector: ScaleGestureDetector
                    ): Boolean {

                        val state =
                            camera
                                ?.cameraInfo
                                ?.zoomState
                                ?.value
                                ?: return true

                        val newZoom =
                            currentZoom *
                                detector.scaleFactor

                        applyZoom(
                            newZoom.coerceIn(
                                state.minZoomRatio,
                                state.maxZoomRatio
                            )
                        )

                        return true
                    }

                    override fun onScaleBegin(
                        detector: ScaleGestureDetector
                    ): Boolean {

                        currentZoom =
                            camera
                                ?.cameraInfo
                                ?.zoomState
                                ?.value
                                ?.zoomRatio
                                ?: 1f

                        return true
                    }
                }
            )

        previewView.setOnTouchListener { _, event ->

            scaleDetector.onTouchEvent(event)

            if (
                event.action ==
                MotionEvent.ACTION_UP
            ) {

                showFocus(
                    event.x,
                    event.y
                )
            }

            true
        }
    }

    private fun showFocus(
        x: Float,
        y: Float
    ) {

        val size = 76

        focusRing.layoutParams =
            FrameLayout.LayoutParams(
                size,
                size
            ).apply {

                leftMargin =
                    (x - size / 2).roundToInt()

                topMargin =
                    (y - size / 2).roundToInt()
            }

        focusRing.visibility =
            View.VISIBLE

        focusRing.alpha = 1f
        focusRing.scaleX = 1.2f
        focusRing.scaleY = 1.2f

        AnimatorSet().apply {

            playTogether(
                ObjectAnimator.ofFloat(
                    focusRing,
                    View.SCALE_X,
                    1.2f,
                    1f,
                    1.08f
                ),
                ObjectAnimator.ofFloat(
                    focusRing,
                    View.SCALE_Y,
                    1.2f,
                    1f,
                    1.08f
                ),
                ObjectAnimator.ofFloat(
                    focusRing,
                    View.ALPHA,
                    1f,
                    1f,
                    0f
                )
            )

            duration = 750L
            interpolator =
                DecelerateInterpolator()

            start()
        }
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
                        75,
                        0,
                        0,
                        0
                    ),
                    999f
                )

            setPadding(
                6,
                0,
                6,
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
                Color.argb(
                    185,
                    255,
                    255,
                    255
                )
            )

            background =
                ColorDrawableCompat(
                    Color.TRANSPARENT
                )

            setPadding(
                5,
                0,
                5,
                0
            )

            layoutParams =
                LinearLayout.LayoutParams(
                    0,
                    42,
                    1f
                ).apply {

                    setMargins(
                        1,
                        0,
                        1,
                        0
                    )
                }

            setOnClickListener {
                action()
            }
        }
    }

    private fun ColorDrawableCompat(
        color: Int
    ): GradientDrawable {

        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = 999f
        }
    }

    private fun updateModeUi(
        selected: String
    ) {

        for (
            (name, button) in modeButtons
        ) {

            if (name == selected) {

                button.setTextColor(
                    Color.WHITE
                )

                button.background =
                    roundBackground(
                        Color.argb(
                            125,
                            255,
                            255,
                            255
                        ),
                        999f
                    )

                button.animate()
                    .scaleX(1.06f)
                    .scaleY(1.06f)
                    .setDuration(180L)
                    .start()

            } else {

                button.setTextColor(
                    Color.argb(
                        165,
                        255,
                        255,
                        255
                    )
                )

                button.background =
                    ColorDrawableCompat(
                        Color.TRANSPARENT
                    )

                button.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(180L)
                    .start()
            }
        }
    }

    private fun animateButton(
        view: View
    ) {

        view.animate()
            .scaleX(0.88f)
            .scaleY(0.88f)
            .setDuration(80L)
            .withEndAction {

                view.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(130L)
                    .start()
            }
            .start()
    }

    private fun animateShutter() {

        shutterButton.animate()
            .scaleX(0.86f)
            .scaleY(0.86f)
            .setDuration(80L)
            .withEndAction {

                shutterButton.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(150L)
                    .start()
            }
            .start()
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
                4,
                Color.argb(
                    220,
                    255,
                    255,
                    255
                )
            )
        }
    }

    // =========================================================
    // QUICK ZOOM
    // =========================================================

    private fun createQuickZoomButtons() {

        zoomButtonsLayout.removeAllViews()
        quickZoomButtons.clear()

        val levels =
            listOf(
                0.5f,
                1f,
                2f,
                5f
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

                    textSize = 10f

                    setTextColor(
                        Color.WHITE
                    )

                    background =
                        ColorDrawableCompat(
                            Color.TRANSPARENT
                        )

                    setPadding(
                        7,
                        0,
                        7,
                        0
                    )

                    setOnClickListener {

                        animateButton(this)

                        applyQuickZoom(
                            level
                        )
                    }
                }

            zoomButtonsLayout.addView(
                button,
                LinearLayout.LayoutParams(
                    52,
                    37
                )
            )

            quickZoomButtons[level] =
                button
        }
    }

    // =========================================================
    // CAMERA START
    // =========================================================

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
            ProcessCameraProvider.getInstance(this)

        future.addListener({

            try {

                val provider =
                    future.get()

                cameraProvider =
                    provider

                val selector =
                    if (isFrontCamera) {
                        CameraSelector.DEFAULT_FRONT_CAMERA
                    } else {
                        CameraSelector.DEFAULT_BACK_CAMERA
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
                    previewView.surfaceProvider

                val captureBuilder =
                    ImageCapture.Builder()
                        .setCaptureMode(
                            ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY
                        )
                        .setFlashMode(
                            flashMode
                        )

                if (
                    isAstroMode &&
                    !isFrontCamera
                ) {

                    try {

                        val extender =
                            Camera2Interop.Extender(
                                captureBuilder
                            )

                        extender.setCaptureRequestOption(
                            android.hardware.camera2.CaptureRequest.CONTROL_AE_MODE,
                            android.hardware.camera2.CaptureRequest.CONTROL_AE_MODE_OFF
                        )

                        extender.setCaptureRequestOption(
                            android.hardware.camera2.CaptureRequest.SENSOR_EXPOSURE_TIME,
                            4_000_000_000L
                        )

                        extender.setCaptureRequestOption(
                            android.hardware.camera2.CaptureRequest.SENSOR_SENSITIVITY,
                            800
                        )

                    } catch (
                        ignored: Exception
                    ) {
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
                    VideoCapture.withOutput(
                        recorder
                    )

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
                shutterButton.isEnabled = true

                updateZoomControls()

                camera?.cameraInfo
                    ?.zoomState
                    ?.observe(this) {
                        updateZoomControls()
                    }

            } catch (e: Exception) {

                isCameraReady = false
                shutterButton.isEnabled = false

                showMessage(
                    "Gagal membuka kamera"
                )

            } finally {

                isStartingCamera = false
            }

        }, ContextCompat.getMainExecutor(this))
    }

    // =========================================================
    // MODES
    // =========================================================

    private fun setPhotoMode() {

        if (isRecording) return

        isVideoMode = false
        isAstroMode = false

        statusText.text =
            "NINJACAM • PHOTO"

        updateModeUi("PHOTO")

        restartCameraSafely()
    }

    private fun setVideoMode() {

        if (isRecording) return

        isVideoMode = true
        isAstroMode = false

        statusText.text =
            "NINJACAM • VIDEO"

        updateModeUi("VIDEO")

        restartCameraSafely()
    }

    private fun setAstroMode() {

        if (isRecording) return

        if (isFrontCamera) {
            isFrontCamera = false
        }

        isVideoMode = false
        isAstroMode = true

        statusText.text =
            "NINJACAM • ASTRO"

        updateModeUi("ASTRO")

        restartCameraSafely()
    }

    private fun restartCameraSafely() {

        isCameraReady = false
        shutterButton.isEnabled = false

        Handler(
            Looper.getMainLooper()
        ).postDelayed({

            if (
                !isFinishing &&
                !isDestroyed
            ) {
                startCamera()
            }

        }, 160L)
    }

    // =========================================================
    // SWITCH CAMERA
    // =========================================================

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
                CameraSelector.DEFAULT_FRONT_CAMERA
            } else {
                CameraSelector.DEFAULT_BACK_CAMERA
            }

        val provider =
            cameraProvider ?: return

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

        previewView.animate()
            .alpha(0.35f)
            .setDuration(100L)
            .withEndAction {

                previewView.animate()
                    .alpha(1f)
                    .setDuration(250L)
                    .start()

                restartCameraSafely()
            }
            .start()
    }

    // =========================================================
    // FLASH
    // =========================================================

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

        animateButton(
            flashButton
        )

        if (isCameraReady) {
            restartCameraSafely()
        }
    }

    // =========================================================
    // TIMER
    // =========================================================

    private fun cycleTimer() {

        timerSeconds =
            when (timerSeconds) {
                0 -> 3
                3 -> 5
                else -> 0
            }

        timerButton.text =
            when (timerSeconds) {

                0 -> "◷"

                else ->
                    "⏱ ${timerSeconds}s"
            }

        animateButton(
            timerButton
        )
    }

    // =========================================================
    // SHUTTER
    // =========================================================

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

            shutterButton.isEnabled = false

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

    // =========================================================
    // PHOTO
    // =========================================================

    private fun takePhoto() {

        val capture =
            imageCapture ?: return

        shutterButton.isEnabled = false

        if (isAstroMode) {

            showMessage(
                "Astro exposure 4 detik…"
            )
        }

        val flashOverlay =
            View(this).apply {

                setBackgroundColor(
                    Color.WHITE
                )

                alpha = 0f
            }

        rootContainer.addView(
            flashOverlay,
            FrameLayout.LayoutParams(
                -1,
                -1
            )
        )

        ObjectAnimator.ofFloat(
            flashOverlay,
            View.ALPHA,
            0f,
            0.75f,
            0f
        ).apply {

            duration = 220L

            addListener(
                object :
                    Animator.AnimatorListener {

                    override fun onAnimationStart(
                        animation: Animator
                    ) {
                    }

                    override fun onAnimationEnd(
                        animation: Animator
                    ) {
                        rootContainer.removeView(
                            flashOverlay
                        )
                    }

                    override fun onAnimationCancel(
                        animation: Animator
                    ) {
                        rootContainer.removeView(
                            flashOverlay
                        )
                    }

                    override fun onAnimationRepeat(
                        animation: Animator
                    ) {
                    }
                }
            )

            start()
        }

        val values =
            ContentValues().apply {

                put(
                    MediaStore.Images.Media.DISPLAY_NAME,
                    if (isAstroMode) {
                        "NinjaCam_Astro_${System.currentTimeMillis()}.jpg"
                    } else {
                        "NinjaCam_${System.currentTimeMillis()}.jpg"
                    }
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
            object :
                ImageCapture.OnImageSavedCallback {

                override fun onImageSaved(
                    result: ImageCapture.OutputFileResults
                ) {

                    shutterButton.isEnabled = true

                    showMessage(
                        if (isAstroMode) {
                            "Foto Astro tersimpan ✓"
                        } else {
                            "Foto tersimpan ✓"
                        }
                    )
                }

                override fun onError(
                    exception: ImageCaptureException
                ) {

                    shutterButton.isEnabled = true

                    showMessage(
                        "Gagal mengambil foto"
                    )
                }
            }
        )
    }

    // =========================================================
    // AUDIO
    // =========================================================

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

    // =========================================================
    // VIDEO
    // =========================================================

    private fun startVideoRecording(
        withAudio: Boolean
    ) {

        val video =
            videoCapture ?: return

        val values =
            ContentValues().apply {

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

            if (withAudio) {
                pending =
                    pending.withAudioEnabled()
            }

            recording =
                pending.start(
                    ContextCompat.getMainExecutor(this)
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

                            if (!event.hasError()) {

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

    // =========================================================
    // ZOOM
    // =========================================================

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

        currentZoom =
            state.zoomRatio

        zoomSlider.progress =
            calculateZoomProgress(
                currentZoom
            )

        zoomSlider.isEnabled =
            maxZoom > minZoom

        zoomText.text =
            String.format(
                Locale.US,
                "%.1f×",
                currentZoom
            )

        for (
            (level, button) in quickZoomButtons
        ) {

            val available =
                level >= minZoom &&
                    level <= maxZoom

            button.visibility =
                if (available) {
                    View.VISIBLE
                } else {
                    View.GONE
                }

            if (
                available &&
                kotlin.math.abs(
                    currentZoom - level
                ) < 0.08f
            ) {

                button.background =
                    roundBackground(
                        Color.argb(
                            130,
                            255,
                            255,
                            255
                        ),
                        999f
                    )

            } else {

                button.background =
                    ColorDrawableCompat(
                        Color.TRANSPARENT
                    )
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
            return
        }

        applyZoom(level)

        zoomSlider.progress =
            calculateZoomProgress(level)
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

        currentZoom =
            safeRatio

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

        updateZoomControls()
    }

    // =========================================================
    // MESSAGE
    // =========================================================

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
}
