package com.example.testapp

import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.*
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.util.Size
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Core
import org.opencv.imgproc.Imgproc
import java.nio.ByteBuffer

class CameraHelper(private val context: Context) {

    private lateinit var cameraDevice: CameraDevice
    private lateinit var captureSession: CameraCaptureSession
    private lateinit var previewRequestBuilder: CaptureRequest.Builder

    private val cameraThread = HandlerThread("CameraBackground").apply { start() }
    private val cameraHandler = Handler(cameraThread.looper)

    /** Callback invoked whenever a new processed camera frame is available */
    var frameMatListener: ((mat: Mat) -> Unit)? = null

    // Reusable Mats to avoid per-frame allocations
    private val yuvMat = Mat()
    private val rgbaMat = Mat()

    fun openCamera() {
        val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val cameraId = manager.cameraIdList[0]

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

            override fun onDisconnected(camera: CameraDevice) { camera.close() }
            override fun onError(camera: CameraDevice, error: Int) { camera.close() }
        }, cameraHandler)
    }

    private fun startPreview(cameraId: String) {
        val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val characteristics = manager.getCameraCharacteristics(cameraId)
        val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        val chosenSize: Size = map?.getOutputSizes(ImageReader::class.java)
            ?.firstOrNull { it.width <= 640 && it.height <= 480 } ?: Size(640, 480)

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
        val reader = ImageReader.newInstance(chosenSize.width, chosenSize.height,
            ImageFormat.YUV_420_888, 2)

        reader.setOnImageAvailableListener({ r ->
            val image = r.acquireLatestImage() ?: return@setOnImageAvailableListener
            try {
                // Direct YUV → NV21 byte array
                val nv21 = yuv420ToNv21(image)

                // Reuse yuvMat
                yuvMat.create(image.height + image.height / 2, image.width, CvType.CV_8UC1)
                yuvMat.put(0, 0, nv21)

                // Convert YUV → RGBA
                Imgproc.cvtColor(yuvMat, rgbaMat, Imgproc.COLOR_YUV2RGBA_NV21)

                // Rotate Mat if needed (can also rotate in shader for speed)
                val rotatedMat = rotateMat(rgbaMat, finalRotation)

                frameMatListener?.invoke(rotatedMat)

            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                image.close()
            }
        }, cameraHandler)

        // Preview request
        previewRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
            addTarget(reader.surface)
        }

        cameraDevice.createCaptureSession(listOf(reader.surface),
            object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    captureSession = session
                    previewRequestBuilder.set(CaptureRequest.CONTROL_MODE,
                        CameraMetadata.CONTROL_MODE_AUTO)
                    captureSession.setRepeatingRequest(previewRequestBuilder.build(), null, cameraHandler)
                }

                override fun onConfigureFailed(session: CameraCaptureSession) { }
            }, cameraHandler
        )
    }

    private fun rotateMat(mat: Mat, rotation: Int): Mat {
        val rotated = Mat()
        when (rotation) {
            90 -> Core.transpose(mat, rotated).also { Core.flip(rotated, rotated, 1) }
            180 -> Core.flip(mat, rotated, -1)
            270 -> Core.transpose(mat, rotated).also { Core.flip(rotated, rotated, 0) }
            else -> mat.copyTo(rotated)
        }
        return rotated
    }

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

        for (i in 0 until uvSize) {
            nv21[uvPos + i * 2] = v[i]
            nv21[uvPos + i * 2 + 1] = u[i]
        }
        return nv21
    }

    fun closeCamera() {
        if (::captureSession.isInitialized) captureSession.close()
        if (::cameraDevice.isInitialized) cameraDevice.close()
        cameraThread.quitSafely()
    }
}
