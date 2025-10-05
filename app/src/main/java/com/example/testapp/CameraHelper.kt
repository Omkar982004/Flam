package com.example.testapp

import org.opencv.core.Core
import android.content.Context
import android.hardware.camera2.*
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.util.Size
import org.opencv.core.Mat
import org.opencv.core.CvType
import android.graphics.YuvImage
import android.graphics.Rect
import android.graphics.BitmapFactory
import org.opencv.android.Utils
import java.io.ByteArrayOutputStream

/**
 * CameraHelper manages Camera2 preview and provides a processed frame callback
 * as Mat for OpenCV processing (C++ via JNI) and OpenGL ES rendering.
 */
class CameraHelper(private val context: Context) {

    private lateinit var cameraDevice: CameraDevice
    private lateinit var captureSession: CameraCaptureSession
    private lateinit var previewRequestBuilder: CaptureRequest.Builder

    private val cameraThread = HandlerThread("CameraBackground").apply { start() }
    private val cameraHandler = Handler(cameraThread.looper)

    /** Callback invoked whenever a new processed camera frame is available */
    var frameMatListener: ((mat: Mat) -> Unit)? = null

    /**
     * Opens the camera and starts preview.
     */
    fun openCamera() {
        val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val cameraId = manager.cameraIdList[0] // default to back camera

        if (androidx.core.app.ActivityCompat.checkSelfPermission(
                context,
                android.Manifest.permission.CAMERA
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) return

        manager.openCamera(cameraId, object : CameraDevice.StateCallback() {
            override fun onOpened(camera: CameraDevice) {
                cameraDevice = camera
                startPreview(cameraId)
            }

            override fun onDisconnected(camera: CameraDevice) {
                camera.close()
            }

            override fun onError(camera: CameraDevice, error: Int) {
                camera.close()
            }
        }, cameraHandler)
    }
    private fun rotateMat(mat: Mat, rotation: Int): Mat {
        val rotated = Mat()
        when (rotation) {
            90 -> Core.transpose(mat, rotated).also { Core.flip(rotated, rotated, 1) }  // CW
            180 -> Core.flip(mat, rotated, -1)
            270 -> Core.transpose(mat, rotated).also { Core.flip(rotated, rotated, 0) } // CCW
            else -> mat.copyTo(rotated)
        }
        return rotated
    }
    private fun startPreview(cameraId: String) {
        val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager

        // Dynamically choose camera preview size (max 1280x720)
        val characteristics = manager.getCameraCharacteristics(cameraId)
        val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        val chosenSize: Size = map?.getOutputSizes(ImageReader::class.java)
            ?.firstOrNull { it.width <= 640 && it.height <= 480 }
            ?: Size(640, 480)


        // ---- Add rotation calculation here ----
        val cameraOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0
        val deviceRotation = (context.getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager)
            .defaultDisplay.rotation
        val rotationDegrees = when (deviceRotation) {
            android.view.Surface.ROTATION_0 -> 0
            android.view.Surface.ROTATION_90 -> 90
            android.view.Surface.ROTATION_180 -> 180
            android.view.Surface.ROTATION_270 -> 270
            else -> 0
        }
        val finalRotation = (cameraOrientation - rotationDegrees + 360) % 360

        // ImageReader to get YUV frames
        val reader = ImageReader.newInstance(
            chosenSize.width, chosenSize.height,
            android.graphics.ImageFormat.YUV_420_888, 2
        )

        reader.setOnImageAvailableListener({ reader ->
            val image = reader.acquireLatestImage() ?: return@setOnImageAvailableListener

            try {
                // Convert YUV_420_888 → NV21 → JPEG → Bitmap
                val nv21 = yuv420ToNv21(image)
                val yuvImage = YuvImage(nv21, android.graphics.ImageFormat.NV21, image.width, image.height, null)
                val out = ByteArrayOutputStream()
                yuvImage.compressToJpeg(Rect(0, 0, image.width, image.height), 100, out)
                val jpegData = out.toByteArray()
                val bitmap = BitmapFactory.decodeByteArray(jpegData, 0, jpegData.size)

                // Convert Bitmap → OpenCV Mat (RGBA)
                val mat = Mat()
                Utils.bitmapToMat(bitmap, mat)

                // Rotate Mat to correct orientation
                val rotatedMat = rotateMat(mat, finalRotation)

                frameMatListener?.invoke(rotatedMat)

            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                image.close()
            }
        }, cameraHandler)

        // Create capture request for preview
        previewRequestBuilder =
            cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                addTarget(reader.surface)
            }

        // Start camera capture session
        cameraDevice.createCaptureSession(listOf(reader.surface),
            object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    captureSession = session
                    previewRequestBuilder.set(
                        CaptureRequest.CONTROL_MODE,
                        CameraMetadata.CONTROL_MODE_AUTO
                    )
                    captureSession.setRepeatingRequest(previewRequestBuilder.build(), null, cameraHandler)
                }

                override fun onConfigureFailed(session: CameraCaptureSession) {
                    // Handle configuration failure if needed
                }
            }, cameraHandler
        )
    }

    /**
     * Converts YUV_420_888 image to NV21 byte array.
     */
    private fun yuv420ToNv21(image: android.media.Image): ByteArray {
        val width = image.width
        val height = image.height
        val ySize = width * height
        val uvSize = width * height / 4

        val nv21 = ByteArray(ySize + uvSize * 2)

        val yBuffer = image.planes[0].buffer
        val uBuffer = image.planes[1].buffer
        val vBuffer = image.planes[2].buffer

        yBuffer.get(nv21, 0, ySize)

        val uvPos = ySize
        val u = ByteArray(uBuffer.remaining())
        val v = ByteArray(vBuffer.remaining())
        uBuffer.get(u)
        vBuffer.get(v)

        // Interleave V and U as NV21 expects
        for (i in 0 until uvSize) {
            nv21[uvPos + i * 2] = v[i]
            nv21[uvPos + i * 2 + 1] = u[i]
        }

        return nv21
    }

    /**
     * Stops camera preview and releases resources.
     */
    fun closeCamera() {
        if (::captureSession.isInitialized) captureSession.close()
        if (::cameraDevice.isInitialized) cameraDevice.close()
        cameraThread.quitSafely()
    }
}
