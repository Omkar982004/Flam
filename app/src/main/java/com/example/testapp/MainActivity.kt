package com.example.testapp

import android.content.pm.PackageManager
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.opengl.GLSurfaceView
import android.util.Log
import com.example.gl.GLRenderer
import org.opencv.android.OpenCVLoader
import org.opencv.core.Mat
import android.widget.TextView
import android.widget.Button
import org.opencv.android.Utils
import android.graphics.Bitmap
import java.io.File
import java.io.FileOutputStream


class MainActivity : AppCompatActivity() {

    private lateinit var glSurfaceView: GLSurfaceView
    private lateinit var cameraHelper: CameraHelper
    private lateinit var nativeLib: NativeLib
    private lateinit var glRenderer: GLRenderer

    // FPS tracking variables
    private var lastTime = System.nanoTime()
    private var frameCount = 0
    private lateinit var fpsText: TextView
    private lateinit var toggleModeButton: Button //added button

    private var latestFrame: Mat? = null // <-- Add this lin
    private lateinit var captureButton: Button // Added capture button

    // Mode flag
    private var isEdgeMode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)


        // Initialize OpenCV
        if (!OpenCVLoader.initDebug()) {
            Log.e("OpenCV", "Unable to load OpenCV")
        } else {
            Log.d("OpenCV", "OpenCV loaded successfully")
        }

        fpsText = findViewById(R.id.fpsText)
        toggleModeButton = findViewById(R.id.toggleModeButton) // Link button from XML
        captureButton = findViewById(R.id.captureButton)

        // GLSurfaceView setup
        glSurfaceView = findViewById(R.id.glSurfaceView)
        glSurfaceView.setEGLContextClientVersion(2)
        glRenderer = GLRenderer()
        glSurfaceView.setRenderer(glRenderer)
        glSurfaceView.renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY

        cameraHelper = CameraHelper(this)
        nativeLib = NativeLib()

        // Toggle between Raw and Edge-detected output
        toggleModeButton.setOnClickListener {
            isEdgeMode = !isEdgeMode
            toggleModeButton.text = if (isEdgeMode) "Mode: Edges" else "Mode: Raw"
        }

        // Capture button – set listener **once**
        captureButton.setOnClickListener {
            latestFrame?.let { mat ->
                val timestamp = System.currentTimeMillis()
                val fileName = if (isEdgeMode) "capture_edges_$timestamp.png" else "capture_raw_$timestamp.png"
                val path = "${getExternalFilesDir(null)?.absolutePath}/$fileName"
                if (isEdgeMode) nativeLib.processFrame(mat.nativeObjAddr)
                saveMatAsPNG(mat, path)
            }
        }

        // Camera permission check
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.CAMERA), 100)
        } else {
            startCamera()
        }
    }

    fun saveMatAsPNG(mat: Mat, path: String) {
        try {
            val bmp = Bitmap.createBitmap(mat.cols(), mat.rows(), Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(mat, bmp)
            val file = File(path)
            FileOutputStream(file).use { out ->
                bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            Log.d("FrameCapture", "Saved frame to $path")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    private fun startCamera() {
        // Camera frame callback delivers a processed Mat
        cameraHelper.frameMatListener = { mat: Mat ->
            latestFrame = mat.clone()
            // Process frame via JNI / OpenCV
            // Conditionally apply OpenCV edge processing
            // Capture button saves the current frame
            latestFrame = mat.clone() // only update latestFrame
            if (isEdgeMode) nativeLib.processFrame(mat.nativeObjAddr)
            glRenderer.updateTexture(mat)

            // Send processed frame to GLRenderer
            glRenderer.updateTexture(mat)
//            glSurfaceView.requestRender()

            // --- FPS counter logic ---
            frameCount++
            val currentTime = System.nanoTime()
            val elapsed = (currentTime - lastTime) / 1_000_000_000.0 // seconds
            if (elapsed >= 1.0) {
                val fps = frameCount / elapsed
                runOnUiThread {
                    fpsText.text = "FPS: %.1f".format(fps)
                }
                Log.d("FPS_COUNTER", "FPS: %.1f".format(fps))
                frameCount = 0
                lastTime = currentTime
            }

        }

        cameraHelper.openCamera() // Should deliver Mat frames instead of SurfaceTexture
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraHelper.closeCamera()
    }
}
