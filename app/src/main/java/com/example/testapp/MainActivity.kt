package com.example.testapp

import android.content.pm.PackageManager
import android.graphics.SurfaceTexture
import android.os.Bundle
import android.view.TextureView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.util.Log
import org.opencv.android.OpenCVLoader
import org.opencv.core.Mat
import org.opencv.android.Utils

class MainActivity : AppCompatActivity() {

    private lateinit var textureView: TextureView
    private lateinit var cameraHelper: CameraHelper
    private lateinit var nativeLib: NativeLib

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        textureView = findViewById(R.id.textureView)
        cameraHelper = CameraHelper(this)

        // Inside onCreate(), after cameraHelper = CameraHelper(this)
        if (!OpenCVLoader.initDebug()) {
            Log.e("OpenCV", "Unable to load OpenCV")
        } else {
            Log.d("OpenCV", "OpenCV loaded successfully")
        }

        // Runtime permission check
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.CAMERA), 100)
        } else {
            startCameraWhenReady()
        }
        // Inside onCreate()
        nativeLib = NativeLib()
    }

    private fun startCameraWhenReady() {
        textureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
                cameraHelper.openCamera(textureView)
            }

            override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {}
            override fun onSurfaceTextureDestroyed(surface: SurfaceTexture) = true
            override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {
                val bitmap = textureView.bitmap ?: return
                val mat = Mat()
                Utils.bitmapToMat(bitmap, mat)
                nativeLib.processFrame(mat.nativeObjAddr)
                Utils.matToBitmap(mat, bitmap)
                // Display back on TextureView if needed via ImageView
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startCameraWhenReady()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraHelper.closeCamera()
    }
}
