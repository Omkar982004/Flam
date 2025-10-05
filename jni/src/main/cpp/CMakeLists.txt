cmake_minimum_required(VERSION 3.22.1)
project("jni")  # matches the module name

# Include OpenCV headers
include_directories(${CMAKE_CURRENT_SOURCE_DIR}/../../../../OpenCV/native/jni/include)

# Prebuilt OpenCV library
add_library(opencv_java4 SHARED IMPORTED)
set_target_properties(opencv_java4 PROPERTIES
        IMPORTED_LOCATION ${CMAKE_CURRENT_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/libopencv_java4.so
)

# Your JNI library
add_library(jni SHARED jni.cpp)

# Link libraries
find_library(log-lib log)
find_library(android-lib android)

target_link_libraries(jni
        opencv_java4
        ${log-lib}
        ${android-lib}
)
