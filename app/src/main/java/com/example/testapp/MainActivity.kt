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


class MainActivity : AppCompatActivity() {

    private lateinit var glSurfaceView: GLSurfaceView
    private lateinit var cameraHelper: CameraHelper
    private lateinit var nativeLib: NativeLib
    private lateinit var glRenderer: GLRenderer

    // FPS tracking variables
    private var lastTime = System.nanoTime()
    private var frameCount = 0
    private lateinit var fpsText: TextView


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
        // GLSurfaceView setup
        glSurfaceView = findViewById(R.id.glSurfaceView)
        glSurfaceView.setEGLContextClientVersion(2)
        glRenderer = GLRenderer()
        glSurfaceView.setRenderer(glRenderer)
        glSurfaceView.renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY

        cameraHelper = CameraHelper(this)
        nativeLib = NativeLib()

        // Camera permission check
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.CAMERA), 100)
        } else {
            startCamera()
        }
    }

    private fun startCamera() {
        // Camera frame callback delivers a processed Mat
        cameraHelper.frameMatListener = { mat: Mat ->
            // Process frame via JNI / OpenCV
            nativeLib.processFrame(mat.nativeObjAddr)

            // Send processed frame to GLRenderer
            glRenderer.updateTexture(mat)
            glSurfaceView.requestRender()

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
