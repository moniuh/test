package com.moniuh.scan3d

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.ar.core.ArCoreApk
import com.google.ar.core.CameraConfig
import com.google.ar.core.CameraConfigFilter
import com.google.ar.core.Config
import com.google.ar.core.Frame
import com.google.ar.core.Session
import com.google.ar.core.TrackingFailureReason
import com.google.ar.core.TrackingState
import com.google.ar.core.exceptions.CameraNotAvailableException
import com.google.ar.core.exceptions.NotYetAvailableException
import com.google.ar.core.exceptions.UnavailableApkTooOldException
import com.google.ar.core.exceptions.UnavailableArcoreNotInstalledException
import com.google.ar.core.exceptions.UnavailableDeviceNotCompatibleException
import com.google.ar.core.exceptions.UnavailableSdkTooOldException
import com.google.ar.core.exceptions.UnavailableUserDeclinedInstallationException
import com.moniuh.scan3d.core.ExportFormat
import com.moniuh.scan3d.export.ExportManager
import com.moniuh.scan3d.rendering.BackgroundRenderer
import com.moniuh.scan3d.rendering.DisplayRotationHelper
import com.moniuh.scan3d.rendering.PointCloudRenderer
import com.moniuh.scan3d.scan.FrameCapture
import com.moniuh.scan3d.scan.ScanEngine
import com.moniuh.scan3d.scan.ScanPreset
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.abs

class MainActivity : AppCompatActivity(), GLSurfaceView.Renderer {

    private lateinit var surfaceView: GLSurfaceView
    private lateinit var statusText: TextView
    private lateinit var hintText: TextView
    private lateinit var scanButton: MaterialButton
    private lateinit var resetButton: MaterialButton
    private lateinit var exportButton: MaterialButton
    private lateinit var settingsButton: MaterialButton
    private lateinit var progressOverlay: View
    private lateinit var progressText: TextView

    private lateinit var displayRotationHelper: DisplayRotationHelper
    private val backgroundRenderer = BackgroundRenderer()
    private val pointRenderer = PointCloudRenderer()
    private lateinit var engine: ScanEngine

    @Volatile
    private var session: Session? = null
    private var textureBoundToSession: Session? = null
    private var installRequested = false
    private var fatalErrorShown = false

    @Volatile
    private var scanning = false

    @Volatile
    private var useSmoothDepth = false
    private var lastDepthTimestamp = 0L
    private var lastCaptureMs = 0L
    private var lastStatusMs = 0L

    private val viewMatrix = FloatArray(16)
    private val projectionMatrix = FloatArray(16)
    private val viewProjection = FloatArray(16)

    private val prefs by lazy { getSharedPreferences("settings", MODE_PRIVATE) }

    private val cameraPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) showFatal(getString(R.string.error_camera_permission))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        surfaceView = findViewById(R.id.surfaceView)
        statusText = findViewById(R.id.statusText)
        hintText = findViewById(R.id.hintText)
        scanButton = findViewById(R.id.scanButton)
        resetButton = findViewById(R.id.resetButton)
        exportButton = findViewById(R.id.exportButton)
        settingsButton = findViewById(R.id.settingsButton)
        progressOverlay = findViewById(R.id.progressOverlay)
        progressText = findViewById(R.id.progressText)

        val topPanel = findViewById<View>(R.id.topPanel)
        val bottomPanel = findViewById<View>(R.id.bottomPanel)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.root)) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            topPanel.updatePadding(top = bars.top + dp(8))
            bottomPanel.updatePadding(bottom = bars.bottom + dp(12))
            insets
        }

        val preset = ScanPreset.entries.getOrElse(prefs.getInt(KEY_PRESET, ScanPreset.STANDARD.ordinal)) { ScanPreset.STANDARD }
        useSmoothDepth = prefs.getBoolean(KEY_SMOOTH, false)
        engine = ScanEngine(preset)

        displayRotationHelper = DisplayRotationHelper(this)
        surfaceView.preserveEGLContextOnPause = true
        surfaceView.setEGLContextClientVersion(2)
        surfaceView.setEGLConfigChooser(8, 8, 8, 8, 16, 0)
        surfaceView.setRenderer(this)
        surfaceView.renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
        surfaceView.setWillNotDraw(false)

        scanButton.setOnClickListener { setScanning(!scanning) }
        resetButton.setOnClickListener { confirmReset() }
        exportButton.setOnClickListener { showExportDialog() }
        settingsButton.setOnClickListener { showSettingsDialog() }
        setScanning(false)
    }

    override fun onResume() {
        super.onResume()
        if (fatalErrorShown) return
        if (session == null) {
            try {
                when (ArCoreApk.getInstance().requestInstall(this, !installRequested)) {
                    ArCoreApk.InstallStatus.INSTALL_REQUESTED -> {
                        installRequested = true
                        return
                    }
                    ArCoreApk.InstallStatus.INSTALLED -> Unit
                }
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                    cameraPermission.launch(Manifest.permission.CAMERA)
                    return
                }
                val newSession = Session(this)
                if (!configureSession(newSession)) {
                    newSession.close()
                    return
                }
                session = newSession
            } catch (e: UnavailableArcoreNotInstalledException) {
                showFatal(getString(R.string.error_arcore_missing))
                return
            } catch (e: UnavailableUserDeclinedInstallationException) {
                showFatal(getString(R.string.error_arcore_missing))
                return
            } catch (e: UnavailableApkTooOldException) {
                showFatal(getString(R.string.error_arcore_update))
                return
            } catch (e: UnavailableSdkTooOldException) {
                showFatal(getString(R.string.error_arcore_update))
                return
            } catch (e: UnavailableDeviceNotCompatibleException) {
                showFatal(getString(R.string.error_device))
                return
            } catch (e: Exception) {
                Log.e(TAG, "Session creation failed", e)
                showFatal(getString(R.string.error_generic, e.message ?: e.javaClass.simpleName))
                return
            }
        }
        try {
            session?.resume()
        } catch (e: CameraNotAvailableException) {
            showFatal(getString(R.string.error_camera))
            session = null
            return
        }
        surfaceView.onResume()
        displayRotationHelper.onResume()
    }

    override fun onPause() {
        super.onPause()
        if (session != null) {
            displayRotationHelper.onPause()
            surfaceView.onPause()
            session?.pause()
        }
        setScanning(false)
    }

    override fun onDestroy() {
        session?.close()
        session = null
        engine.shutdown()
        super.onDestroy()
    }

    /** Picks a camera configuration whose depth, CPU and GPU images share an aspect ratio. */
    private fun configureSession(session: Session): Boolean {
        val filter = CameraConfigFilter(session).setFacingDirection(CameraConfig.FacingDirection.BACK)
        val configs = session.getSupportedCameraConfigs(filter)
        val best = configs.maxWithOrNull(
            compareBy<CameraConfig>(
                { sameAspect(it) },
                { it.depthSensorUsage == CameraConfig.DepthSensorUsage.REQUIRE_AND_USE },
                { if (it.imageSize.width <= 1280) it.imageSize.width else 0 },
                { it.fpsRange.upper },
            ),
        )
        if (best != null) session.cameraConfig = best

        if (!session.isDepthModeSupported(Config.DepthMode.AUTOMATIC)) {
            showFatal(getString(R.string.error_no_depth))
            return false
        }
        val config = session.config
        config.depthMode = if (session.isDepthModeSupported(Config.DepthMode.RAW_DEPTH_ONLY) && !useSmoothDepth) {
            Config.DepthMode.RAW_DEPTH_ONLY
        } else {
            Config.DepthMode.AUTOMATIC
        }
        config.focusMode = Config.FocusMode.AUTO
        config.updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
        config.planeFindingMode = Config.PlaneFindingMode.DISABLED
        config.lightEstimationMode = Config.LightEstimationMode.DISABLED
        session.configure(config)
        return true
    }

    private fun sameAspect(c: CameraConfig): Boolean {
        val a = c.imageSize.width.toFloat() / c.imageSize.height
        val b = c.textureSize.width.toFloat() / c.textureSize.height
        return abs(a - b) < 0.01f
    }

    // ---------------------------------------------------------------- GL thread

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        backgroundRenderer.createOnGlThread()
        pointRenderer.createOnGlThread()
        textureBoundToSession = null
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        displayRotationHelper.onSurfaceChanged(width, height)
        GLES20.glViewport(0, 0, width, height)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        val session = session ?: return
        displayRotationHelper.updateSessionIfNeeded(session)
        try {
            if (textureBoundToSession !== session) {
                session.setCameraTextureName(backgroundRenderer.textureId)
                textureBoundToSession = session
            }
            val frame = session.update()
            val camera = frame.camera
            backgroundRenderer.draw(frame)

            val tracking = camera.trackingState == TrackingState.TRACKING
            updateStatus(camera.trackingState, camera.trackingFailureReason)
            if (!tracking) return

            if (scanning) captureDepth(frame)

            camera.getProjectionMatrix(projectionMatrix, 0, 0.05f, 30f)
            camera.getViewMatrix(viewMatrix, 0)
            Matrix.multiplyMM(viewProjection, 0, projectionMatrix, 0, viewMatrix, 0)
            val preview = engine.preview
            pointRenderer.update(preview?.cloud, preview?.version ?: 0)
            // Projection[0] * viewportWidth / 2 is the focal length in pixels.
            val focalPx = projectionMatrix[0] * surfaceView.width / 2f
            pointRenderer.draw(viewProjection, engine.preset.voxelSize * focalPx * 1.3f)
        } catch (t: Throwable) {
            Log.e(TAG, "Exception on the GL thread", t)
        }
    }

    private fun captureDepth(frame: Frame) {
        val now = SystemClock.uptimeMillis()
        if (now - lastCaptureMs < CAPTURE_INTERVAL_MS || engine.isBusy) return
        val depth = try {
            if (useSmoothDepth) frame.acquireDepthImage16Bits() else frame.acquireRawDepthImage16Bits()
        } catch (e: NotYetAvailableException) {
            return
        }
        depth.use { depthImage ->
            if (depthImage.timestamp == lastDepthTimestamp) return
            lastDepthTimestamp = depthImage.timestamp
            val confidence = if (useSmoothDepth) null else try {
                frame.acquireRawDepthConfidenceImage()
            } catch (e: NotYetAvailableException) {
                null
            }
            val color = try {
                frame.acquireCameraImage()
            } catch (e: NotYetAvailableException) {
                null
            }
            try {
                val data = FrameCapture.capture(depthImage, confidence, color, frame.camera)
                if (engine.submit(data)) lastCaptureMs = now
            } finally {
                confidence?.close()
                color?.close()
            }
        }
    }

    private fun updateStatus(state: TrackingState, reason: TrackingFailureReason) {
        val now = SystemClock.uptimeMillis()
        if (now - lastStatusMs < 250) return
        lastStatusMs = now
        val stats = engine.stats
        val hint = when {
            stats.memoryFull -> getString(R.string.hint_memory_full)
            state == TrackingState.TRACKING && scanning -> getString(R.string.hint_scanning)
            state == TrackingState.TRACKING -> getString(R.string.hint_ready)
            else -> when (reason) {
                TrackingFailureReason.INSUFFICIENT_LIGHT -> getString(R.string.hint_too_dark)
                TrackingFailureReason.EXCESSIVE_MOTION -> getString(R.string.hint_too_fast)
                TrackingFailureReason.INSUFFICIENT_FEATURES -> getString(R.string.hint_no_features)
                TrackingFailureReason.CAMERA_UNAVAILABLE -> getString(R.string.error_camera)
                else -> getString(R.string.hint_initializing)
            }
        }
        val status = getString(
            R.string.status_format,
            getString(engine.preset.label),
            stats.frames,
            stats.previewPoints,
            stats.megabytes,
        )
        runOnUiThread {
            statusText.text = status
            hintText.text = hint
            if (stats.memoryFull && scanning) setScanning(false)
        }
    }

    // ---------------------------------------------------------------- UI actions

    private fun setScanning(enabled: Boolean) {
        scanning = enabled
        scanButton.setText(if (enabled) R.string.action_pause else R.string.action_scan)
        scanButton.setIconResource(if (enabled) R.drawable.ic_pause else R.drawable.ic_record)
    }

    private fun confirmReset() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.reset_title)
            .setMessage(R.string.reset_message)
            .setPositiveButton(R.string.action_reset) { _, _ ->
                setScanning(false)
                engine.reset()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showSettingsDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_settings, null)
        val group = view.findViewById<RadioGroup>(R.id.presetGroup)
        val smooth = view.findViewById<MaterialSwitch>(R.id.smoothDepthSwitch)
        val ids = mapOf(
            ScanPreset.OBJECT to R.id.presetObject,
            ScanPreset.STANDARD to R.id.presetStandard,
            ScanPreset.ROOM to R.id.presetRoom,
        )
        group.check(ids.getValue(engine.preset))
        smooth.isChecked = useSmoothDepth
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.settings_title)
            .setView(view)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val chosen = ids.entries.first { it.value == group.checkedRadioButtonId }.key
                if (chosen != engine.preset) {
                    setScanning(false)
                    engine.reset(chosen)
                    Toast.makeText(this, R.string.settings_reset_notice, Toast.LENGTH_SHORT).show()
                }
                if (smooth.isChecked != useSmoothDepth) {
                    useSmoothDepth = smooth.isChecked
                    session?.let { s ->
                        val config = s.config
                        config.depthMode = if (useSmoothDepth || !s.isDepthModeSupported(Config.DepthMode.RAW_DEPTH_ONLY)) {
                            Config.DepthMode.AUTOMATIC
                        } else {
                            Config.DepthMode.RAW_DEPTH_ONLY
                        }
                        s.configure(config)
                    }
                }
                prefs.edit().putInt(KEY_PRESET, chosen.ordinal).putBoolean(KEY_SMOOTH, useSmoothDepth).apply()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showExportDialog() {
        if (engine.stats.frames == 0) {
            Toast.makeText(this, R.string.export_nothing, Toast.LENGTH_SHORT).show()
            return
        }
        setScanning(false)
        val formats = ExportFormat.entries
        val labels = arrayOf(
            getString(R.string.format_ply_mesh),
            getString(R.string.format_obj),
            getString(R.string.format_stl),
            getString(R.string.format_ply_points),
        )
        val checked = BooleanArray(formats.size) { i ->
            prefs.getBoolean("fmt_${formats[i].name}", formats[i] == ExportFormat.PLY_MESH || formats[i] == ExportFormat.OBJ)
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.export_title)
            .setMultiChoiceItems(labels, checked) { _, which, isChecked -> checked[which] = isChecked }
            .setPositiveButton(R.string.action_export) { _, _ ->
                val chosen = formats.filterIndexed { i, _ -> checked[i] }
                val editor = prefs.edit()
                formats.forEachIndexed { i, f -> editor.putBoolean("fmt_${f.name}", checked[i]) }
                editor.apply()
                if (chosen.isNotEmpty()) export(chosen)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun export(formats: List<ExportFormat>) {
        progressText.setText(R.string.export_progress)
        progressOverlay.visibility = View.VISIBLE
        val baseName = ExportManager.newBaseName()
        engine.withMesh { mesh, _ ->
            val result = runCatching {
                if (mesh.triangleCount == 0) throw IllegalStateException(getString(R.string.export_empty_mesh))
                formats.map { ExportManager.save(applicationContext, baseName, it, mesh) }
            }
            runOnUiThread {
                progressOverlay.visibility = View.GONE
                result.onSuccess { files -> showExportResult(files, mesh.vertexCount, mesh.triangleCount) }
                result.onFailure { e ->
                    Log.e(TAG, "Export failed", e)
                    MaterialAlertDialogBuilder(this)
                        .setTitle(R.string.export_failed)
                        .setMessage(e.message ?: e.javaClass.simpleName)
                        .setPositiveButton(android.R.string.ok, null)
                        .show()
                }
            }
        }
    }

    private fun showExportResult(files: List<ExportManager.SavedFile>, vertices: Int, triangles: Int) {
        val message = getString(
            R.string.export_done_message,
            ExportManager.FOLDER,
            files.joinToString("\n") { "• " + it.displayName },
            vertices,
            triangles,
        )
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.export_done_title)
            .setMessage(message)
            .setPositiveButton(R.string.action_share) { _, _ -> share(files.map { it.uri }) }
            .setNegativeButton(android.R.string.ok, null)
            .show()
    }

    private fun share(uris: List<Uri>) {
        val intent = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris[0])
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
        }
        intent.type = "application/octet-stream"
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        startActivity(Intent.createChooser(intent, getString(R.string.action_share)))
    }

    private fun showFatal(message: String) {
        runOnUiThread {
            if (fatalErrorShown || isFinishing) return@runOnUiThread
            fatalErrorShown = true
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.app_name)
                .setMessage(message)
                .setCancelable(false)
                .setPositiveButton(R.string.action_close) { _, _ -> finish() }
                .show()
        }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private companion object {
        const val TAG = "Skaner3D"
        const val KEY_PRESET = "preset"
        const val KEY_SMOOTH = "smooth_depth"
        const val CAPTURE_INTERVAL_MS = 100L
    }
}
