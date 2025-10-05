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

    cv::Mat edges;
    cv::Canny(mat, edges, 50, 150);

    // Overwrite input Mat with result
    edges.copyTo(mat);

    LOGD("Processed frame: %dx%d", mat.cols, mat.rows);
}
