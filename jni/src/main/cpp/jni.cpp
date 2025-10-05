#include <jni.h>
#include <opencv2/core.hpp>
#include <opencv2/imgproc.hpp>
#include <android/log.h>

#define LOG_TAG "JNI_OpenCV"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

extern "C"
JNIEXPORT void JNICALL
Java_com_example_testapp_NativeLib_processFrame(JNIEnv *env, jobject thiz, jlong matAddr) {
    cv::Mat &mat = *(cv::Mat *) matAddr;

    if (mat.empty()) {
        LOGD("Mat is empty, skipping processing");
        return;
    }

    cv::Mat gray, edges;

    // Convert from RGBA to GRAY
    cv::cvtColor(mat, gray, cv::COLOR_RGBA2GRAY);

    // Apply Canny edge detection
    cv::Canny(gray, edges, 50, 150);

    // Convert single-channel edges to 4-channel RGBA for OpenGL
    cv::cvtColor(edges, mat, cv::COLOR_GRAY2RGBA);

    LOGD("Processed frame (Canny): %dx%d", mat.cols, mat.rows);
}
