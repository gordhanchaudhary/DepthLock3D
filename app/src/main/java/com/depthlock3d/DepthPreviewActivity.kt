package com.depthlock3d

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** In-app composition preview: background -> clock/date -> transparent person layer. */
class DepthPreviewActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences(DepthWallpaperService.PREFS, MODE_PRIVATE)
        val bgUri = prefs.getString(DepthWallpaperService.KEY_BG, null)
        val fgUri = prefs.getString(DepthWallpaperService.KEY_FG, null)
        if (bgUri == null || fgUri == null) {
            finish()
            return
        }
        val bg = loadBitmap(Uri.parse(bgUri))
        val fg = loadBitmap(Uri.parse(fgUri))
        if (bg == null || fg == null) {
            finish()
            return
        }
        setContentView(DepthPreviewView(bg, fg))
    }

    private fun loadBitmap(uri: Uri): Bitmap? = try {
        contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
    } catch (_: Exception) {
        null
    }

    private inner class DepthPreviewView(
        private val background: Bitmap,
        private val foreground: Bitmap
    ) : View(this@DepthPreviewActivity) {
        private val handler = Handler(Looper.getMainLooper())
        private val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        private val clockPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
            setShadowLayer(8f, 0f, 4f, Color.BLACK)
        }
        private val datePaint = Paint(clockPaint)
        private val tick = object : Runnable {
            override fun run() {
                invalidate()
                handler.postDelayed(this, 1000L)
            }
        }

        override fun onAttachedToWindow() {
            super.onAttachedToWindow()
            handler.post(tick)
        }

        override fun onDetachedFromWindow() {
            handler.removeCallbacks(tick)
            super.onDetachedFromWindow()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val w = width.toFloat()
            val h = height.toFloat()
            canvas.drawColor(Color.BLACK)
            drawCover(canvas, background, w, h)

            val now = Date()
            val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(now)
            val date = SimpleDateFormat("EEE, d MMM", Locale.getDefault()).format(now)
            clockPaint.textSize = h * 0.105f
            canvas.drawText(time, w / 2f, h * 0.265f, clockPaint)
            datePaint.textSize = h * 0.028f
            canvas.drawText(date, w / 2f, h * 0.115f, datePaint)

            // The cut-out is composited last, so the person appears in front of the clock.
            drawCover(canvas, foreground, w, h)
        }

        private fun drawCover(canvas: Canvas, bitmap: Bitmap, w: Float, h: Float) {
            val scale = maxOf(w / bitmap.width, h / bitmap.height)
            val dw = bitmap.width * scale
            val dh = bitmap.height * scale
            val dst = RectF((w - dw) / 2f, (h - dh) / 2f, (w + dw) / 2f, (h + dh) / 2f)
            canvas.drawBitmap(bitmap, null, dst, imagePaint)
        }
    }
}
