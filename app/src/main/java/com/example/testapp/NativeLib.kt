package com.example.testapp

class NativeLib {

    companion object {
        init {
            // Load native JNI lib
            System.loadLibrary("jni")
        }
    }

    /**
     * Sends a Mat pointer (native address) to C++ for in-place processing.
     * @param matAddr Native address of cv::Mat (from Mat.getNativeObjAddr()).
     */
    external fun processFrame(matAddr: Long)
}
