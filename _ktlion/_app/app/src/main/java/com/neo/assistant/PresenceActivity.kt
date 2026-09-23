package com.neo.assistant

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Surface
import android.view.TextureView
import android.widget.*

/** Short, user-started foreground camera check. Never records or uploads images. */
class PresenceActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var preview: TextureView
    private lateinit var status: TextView
    private var camera: CameraDevice? = null
    private var capture: CameraCaptureSession? = null
    private var surface: Surface? = null
    private var foreground = false
    private var opening = false
    private var completed = false
    private var evidence = PresenceEvidence()
    private val finishCheck = Runnable { if (!completed && foreground) complete(evidence.result()) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (24 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, pad)
            setBackgroundColor(Color.rgb(247, 249, 246)); fitsSystemWindows = true
        }
        root.addView(TextView(this).apply { text = "A quick presence check"; textSize = 27f; setTextColor(Color.rgb(19, 108, 96)) })
        root.addView(TextView(this).apply {
            text = "Keep your face in view for a few seconds. Images stay on this screen and are never saved or sent to Python."
            textSize = 15f; setPadding(0, 20, 0, 20)
        })
        preview = TextureView(this)
        root.addView(preview, LinearLayout.LayoutParams(-1, (280 * resources.displayMetrics.density).toInt()))
        status = TextView(this).apply { text = "Preparing the front camera…"; textSize = 17f; setPadding(0, 24, 0, 24) }
        root.addView(status)
        root.addView(Button(this).apply { text = "Done"; isAllCaps = false; setOnClickListener { finish() } })
        setContentView(ScrollView(this).apply { addView(root) })
        preview.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) { open() }
            override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) {}
            override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean { close(); return true }
            override fun onSurfaceTextureUpdated(texture: SurfaceTexture) {}
        }
    }

    private fun open() {
        if (!foreground || completed || opening || camera != null || !preview.isAvailable) return
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            complete("Camera access is off. You can allow it in Neo Settings; presence remains uncertain."); return
        }
        opening = true
        handler.postDelayed(finishCheck, 7000)
        try {
            val manager = getSystemService(CameraManager::class.java)
            val id = manager.cameraIdList.firstOrNull {
                manager.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT
            } ?: run { complete("No front camera is available. Presence remains uncertain."); return }
            val characteristics = manager.getCameraCharacteristics(id)
            val modes = characteristics.get(CameraCharacteristics.STATISTICS_INFO_AVAILABLE_FACE_DETECT_MODES) ?: intArrayOf()
            val mode = when {
                CameraMetadata.STATISTICS_FACE_DETECT_MODE_FULL in modes -> CameraMetadata.STATISTICS_FACE_DETECT_MODE_FULL
                CameraMetadata.STATISTICS_FACE_DETECT_MODE_SIMPLE in modes -> CameraMetadata.STATISTICS_FACE_DETECT_MODE_SIMPLE
                else -> { complete("This camera does not provide face-detection metadata. Presence remains uncertain; no sleep or absence guess will be made."); return }
            }
            val sizes = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)?.getOutputSizes(SurfaceTexture::class.java)
            val size = sizes?.filter { it.width <= 1280 }?.maxByOrNull { it.width * it.height }
                ?: sizes?.minByOrNull { it.width * it.height }
                ?: run { complete("No camera preview format is available."); return }
            preview.surfaceTexture?.setDefaultBufferSize(size.width, size.height)
            surface = Surface(preview.surfaceTexture)
            manager.openCamera(id, object : CameraDevice.StateCallback() {
                override fun onOpened(device: CameraDevice) {
                    opening = false
                    if (!foreground || completed) { device.close(); return }
                    camera = device
                    try {
                        val target = surface ?: return
                        val request = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                            addTarget(target)
                            set(CaptureRequest.STATISTICS_FACE_DETECT_MODE, mode)
                        }
                        device.createCaptureSession(listOf(target), object : CameraCaptureSession.StateCallback() {
                            override fun onConfigured(session: CameraCaptureSession) {
                                if (!foreground || completed) { session.close(); return }
                                capture = session
                                try {
                                    status.text = "Checking several frames. No images are being saved."
                                    session.setRepeatingRequest(request.build(), object : CameraCaptureSession.CaptureCallback() {
                                        override fun onCaptureCompleted(session: CameraCaptureSession, request: CaptureRequest, result: TotalCaptureResult) {
                                            if (!completed && foreground) evidence.observe(result.get(CaptureResult.STATISTICS_FACES)?.size ?: 0)
                                        }
                                    }, handler)
                                    handler.removeCallbacks(finishCheck)
                                    handler.postDelayed(finishCheck, 4000)
                                } catch (_: Exception) { complete("Camera access changed or became unavailable. Presence remains uncertain.") }
                            }
                            override fun onConfigureFailed(session: CameraCaptureSession) { session.close(); complete("The camera could not start. Presence remains uncertain.") }
                        }, handler)
                    } catch (_: Exception) { complete("The camera could not start. Presence remains uncertain.") }
                }
                override fun onDisconnected(device: CameraDevice) { device.close(); complete("Camera disconnected. Presence remains uncertain.") }
                override fun onError(device: CameraDevice, error: Int) { device.close(); complete(ErrorHistory.describe(this@PresenceActivity, NeoProblems.camera(error))) }
            }, handler)
        } catch (_: Exception) { complete("Camera access is unavailable. Check its permission in Settings.") }
    }

    private fun complete(summary: String) {
        if (completed || !foreground) return
        completed = true
        status.text = summary
        getSharedPreferences("neo_demo", MODE_PRIVATE).edit()
            .putString("presence_summary", summary).putLong("presence_time", System.currentTimeMillis()).apply()
        handler.removeCallbacks(finishCheck)
        close()
    }

    private fun close() {
        capture?.close(); capture = null
        camera?.close(); camera = null
        surface?.release(); surface = null
        opening = false
    }
    override fun onResume() { super.onResume(); foreground = true; if (preview.isAvailable) open() }
    override fun onPause() {
        foreground = false
        handler.removeCallbacks(finishCheck)
        close()
        // A interrupted measurement cannot silently restart on returning to the app.
        if (!completed) { completed = true; status.text = "Check stopped when you left the screen. Open a new check from Settings." }
        super.onPause()
    }
}
