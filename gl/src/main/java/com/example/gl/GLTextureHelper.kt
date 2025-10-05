package com.example.gl

import android.graphics.Bitmap
import android.opengl.GLES20
import android.opengl.GLUtils

object GLTextureHelper {
    fun loadTexture(bitmap: Bitmap?): Int {
        val textureIds = IntArray(1)
        GLES20.glGenTextures(1, textureIds, 0)
        val textureId = textureIds[0]
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        bitmap?.let {
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, it, 0)
        }
        return textureId
    }

    fun bindTexture(textureId: Int, bitmap: Bitmap?) {
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
        bitmap?.let { GLUtils.texSubImage2D(GLES20.GL_TEXTURE_2D, 0, 0, 0, it) }
    }
}
