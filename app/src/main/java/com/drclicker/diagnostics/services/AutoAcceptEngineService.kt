package com.drclicker.diagnostics.services

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityService.GestureResultCallback
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Path
import android.media.ToneGenerator
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.ViewConfiguration
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.*
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import org.opencv.android.Utils
import kotlin.random.Random

class AutoAcceptEngineService : AccessibilityService() {
    private val tag = "DrClicker"
    private var templateMat: Mat? = null
    private var isEngineActive = false
    private lateinit var prefs: SharedPreferences
    private val handler = Handler(Looper.getMainLooper())
    private val job = Job()
    private val scope = CoroutineScope(Dispatchers.Default + job)
    private var lastMatchTime = 0L
    private var idleCounter = 0
    private val toneGenerator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100)

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        prefs = getSharedPreferences("dr_clicker_prefs", Context.MODE_PRIVATE)
        isEngineActive = true
        Log.d(tag, "AutoAcceptEngineService connected")
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        isEngineActive = false
        scope.cancel()
        templateMat?.release()
        toneGenerator.release()
        Log.d(tag, "AutoAcceptEngineService destroyed")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            if (event.packageName?.toString()?.contains("rapido", ignoreCase = true) == true) {
                scope.launch {
                    processRapidoWindow()
                }
            }
        }
    }

    override fun onInterrupt() {
        Log.d(tag, "Accessibility service interrupted")
    }

    private suspend fun processRapidoWindow() {
        if (!isEngineActive) return

        try {
            val screenshot = takeScreenshot() ?: return
            val screenMat = Mat()

            // Convert hardware bitmap to software bitmap (ARGB_8888)
            val hardwareBitmap = screenshot
            val softwareBitmap = hardwareBitmap.copy(Bitmap.Config.ARGB_8888, false)

            // Convert bitmap to Mat
            Utils.bitmapToMat(softwareBitmap, screenMat)

            // Grayscale conversion
            val grayMat = Mat()
            Imgproc.cvtColor(screenMat, grayMat, Imgproc.COLOR_RGBA2GRAY)

            // Load or create template
            if (templateMat == null) {
                loadTemplateFromPrefs()
            }

            if (templateMat != null) {
                val resultMat = Mat()
                Imgproc.matchTemplate(grayMat, templateMat!!, resultMat, Imgproc.TM_CCOEFF_NORMED)

                val minMaxLocResult = Core.minMaxLoc(resultMat)
                val maxVal = minMaxLocResult.maxVal
                val maxLoc = minMaxLocResult.maxLoc

                Log.d(tag, "Match score: $maxVal at ${maxLoc.x}, ${maxLoc.y}")

                if (maxVal >= 0.85) {
                    idleCounter = 0
                    lastMatchTime = System.currentTimeMillis()
                    performHumanizedClick(maxLoc, templateMat!!)
                } else {
                    idleCounter++
                    if (idleCounter >= 3) {
                        resetStuckTouchQueue()
                        idleCounter = 0
                    }
                }

                resultMat.release()
            }

            // Cleanup
            screenMat.release()
            grayMat.release()
            softwareBitmap.recycle()
            hardwareBitmap.recycle()

        } catch (e: Exception) {
            Log.e(tag, "Error processing window: ${e.message}", e)
        }
    }

    private fun loadTemplateFromPrefs() {
        try {
            val templateUri = prefs.getString("template_uri", null) ?: return
            val bitmap = android.graphics.BitmapFactory.decodeStream(
                contentResolver.openInputStream(android.net.Uri.parse(templateUri))
            ) ?: return

            templateMat = Mat()
            Utils.bitmapToMat(bitmap, templateMat)
            Imgproc.cvtColor(templateMat!!, templateMat!!, Imgproc.COLOR_BGR2GRAY)
            bitmap.recycle()
            Log.d(tag, "Template loaded: ${templateMat?.cols()} x ${templateMat?.rows()}")
        } catch (e: Exception) {
            Log.e(tag, "Failed to load template: ${e.message}", e)
        }
    }

    private fun performHumanizedClick(matchLoc: org.opencv.core.Point, template: Mat) {
        val template_cols = template.cols()
        val template_rows = template.rows()

        // Safe zone: 25% to 75% of template dimensions
        val safeWidthMin = (template_cols * 0.25).toInt()
        val safeWidthMax = (template_cols * 0.75).toInt()
        val safeHeightMin = (template_rows * 0.25).toInt()
        val safeHeightMax = (template_rows * 0.75).toInt()

        val offsetX = Random.nextInt(safeWidthMin, safeWidthMax + 1)
        val offsetY = Random.nextInt(safeHeightMin, safeHeightMax + 1)

        val clickX = (matchLoc.x + offsetX).toFloat()
        val clickY = (matchLoc.y + offsetY).toFloat()

        val reflex = Random.nextLong(10, 100)
        val tapDuration = ViewConfiguration.getTapTimeout().toLong()

        Log.d(tag, "Clicking at ($clickX, $clickY) with reflex: ${reflex}ms")

        handler.postDelayed({
            dispatchGestureClick(clickX, clickY, tapDuration)
            playDualBeep()
        }, reflex)
    }

    private fun dispatchGestureClick(x: Float, y: Float, duration: Long) {
        try {
            val path = Path()
            path.moveTo(x, y)
            path.lineTo(x, y + 1f)  // 1-pixel line to create valid vector

            val stroke = GestureDescription.StrokeDescription(
                path,
                0L,
                duration
            )
            val gesture = GestureDescription.Builder()
                .addStroke(stroke)
                .build()

            dispatchGesture(gesture, object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    Log.d(tag, "Gesture dispatched successfully")
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    Log.w(tag, "Gesture cancelled - attempting reset")
                    resetStuckTouchQueue()
                }
            }, null)
        } catch (e: Exception) {
            Log.e(tag, "Gesture dispatch failed: ${e.message}", e)
        }
    }

    private fun resetStuckTouchQueue() {
        Log.d(tag, "Resetting stuck touch queue with multi-touch reset")
        handler.post {
            try {
                val path1 = Path()
                path1.moveTo(100f, 100f)
                path1.lineTo(100f, 101f)

                val path2 = Path()
                path2.moveTo(400f, 400f)
                path2.lineTo(400f, 401f)

                val stroke1 = GestureDescription.StrokeDescription(path1, 0L, 40)
                val stroke2 = GestureDescription.StrokeDescription(path2, 0L, 40)

                val gesture = GestureDescription.Builder()
                    .addStroke(stroke1)
                    .addStroke(stroke2)
                    .build()

                dispatchGesture(gesture, null, null)
            } catch (e: Exception) {
                Log.e(tag, "Reset failed: ${e.message}", e)
            }
        }
    }

    private fun playDualBeep() {
        try {
            toneGenerator.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 100)
            handler.postDelayed({
                toneGenerator.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 100)
            }, 100)
        } catch (e: Exception) {
            Log.e(tag, "Beep failed: ${e.message}")
        }
    }

    companion object {
        var instance: AutoAcceptEngineService? = null
            private set
    }
}
