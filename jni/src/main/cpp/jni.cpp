#include <jni.h>
#include <opencv2/core.hpp>
#include <opencv2/imgproc.hpp>
#include <android/log.h>

#define LOG_TAG "JNI_OpenCV"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

// Global smoothed frame
cv::Mat smoothedFrame;
const float alpha = 0.25f; // smoothing factor

extern "C"
JNIEXPORT void JNICALL
Java_com_example_testapp_NativeLib_processFrame(JNIEnv *env, jobject thiz, jlong matAddr) {
    cv::Mat &mat = *(cv::Mat *) matAddr;

    if (mat.empty()) return;

    cv::Mat gray, edges;

    // Convert RGBA → Grayscale
    cv::cvtColor(mat, gray, cv::COLOR_RGBA2GRAY);

    // Apply Canny edge detection
    cv::Canny(gray, edges, 50, 150);

    // EMA smoothing
    if (smoothedFrame.empty() || smoothedFrame.size() != edges.size()) {
        edges.copyTo(smoothedFrame);
    } else {
        cv::addWeighted(edges, alpha, smoothedFrame, 1.0 - alpha, 0, smoothedFrame);
    }

    // Convert smoothed edges → RGBA
    cv::cvtColor(smoothedFrame, mat, cv::COLOR_GRAY2RGBA);

    LOGD("Processed frame (Canny + EMA): %dx%d", mat.cols, mat.rows);
}
