package com.drclicker.diagnostics.extensions

import android.graphics.Bitmap
import android.util.Log

object BitmapExtensions {
    private const val tag = "BitmapExt"

    fun Bitmap.toSoftwareBitmap(): Bitmap? {
        return try {
            if (this.config == Bitmap.Config.ARGB_8888) {
                this
            } else {
                this.copy(Bitmap.Config.ARGB_8888, false)
            }
        } catch (e: Exception) {
            Log.e(tag, "Error converting bitmap: ${e.message}")
            null
        }
    }

    fun Bitmap.safeRecycle() {
        try {
            if (!this.isRecycled) {
                this.recycle()
            }
        } catch (e: Exception) {
            Log.e(tag, "Error recycling bitmap: ${e.message}")
        }
    }
}
