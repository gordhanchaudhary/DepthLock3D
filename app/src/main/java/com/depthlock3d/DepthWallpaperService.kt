package com.depthlock3d

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Basic layered live wallpaper:
 * 1) background photo
 * 2) live clock/date
 * 3) transparent foreground PNG (same canvas/aspect ratio as background)
 */
class DepthWallpaperService : WallpaperService() {
    override fun onCreateEngine(): Engine = DepthEngine()

    inner class DepthEngine : Engine() {
        private val handler = Handler(Looper.getMainLooper())
        private var visible = false
        private var loaded = false
        private var backgroundBitmap: Bitmap? = null
        private var foregroundBitmap: Bitmap? = null

        private val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        private val clockPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            setShadowLayer(8f, 0f, 3f, Color.BLACK)
        }

        private val tick = object : Runnable {
            override fun run() {
                drawFrame()
                if (visible) {
                    // Refresh on the next minute boundary rather than rendering continuously.
                    val now = System.currentTimeMillis()
                    val delay = 60_000L - (now % 60_000L)
                    handler.postDelayed(this, delay.coerceAtLeast(500L))
                }
            }
        }

        override fun onVisibilityChanged(isVisible: Boolean) {
            visible = isVisible
            handler.removeCallbacks(tick)
            if (visible) tick.run()
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            drawFrame()
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            visible = false
            handler.removeCallbacks(tick)
            super.onSurfaceDestroyed(holder)
        }

        override fun onDestroy() {
            visible = false
            handler.removeCallbacks(tick)
            backgroundBitmap?.recycle()
            foregroundBitmap?.recycle()
            backgroundBitmap = null
            foregroundBitmap = null
            super.onDestroy()
        }

        private fun ensureImagesLoaded() {
            if (loaded) return
            loaded = true
            backgroundBitmap = loadBitmap(KEY_BG)
            foregroundBitmap = loadBitmap(KEY_FG)
        }

        private fun loadBitmap(key: String): Bitmap? {
            val value = getSharedPreferences(PREFS, MODE_PRIVATE).getString(key, null) ?: return null
            return try {
                contentResolver.openInputStream(Uri.parse(value))?.use { input ->
                    BitmapFactory.decodeStream(input)
                }
            } catch (_: Exception) {
                null
            }
        }

        private fun drawFrame() {
            val holder = surfaceHolder
            var canvas: Canvas? = null
            try {
                canvas = holder.lockCanvas()
                if (canvas == null) return
                ensureImagesLoaded()

                val width = canvas.width.toFloat()
                val height = canvas.height.toFloat()
                canvas.drawColor(Color.rgb(35, 48, 60))

                backgroundBitmap?.let { drawCover(canvas, it, width, height) }

                val now = Date()
                val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(now)
                val date = SimpleDateFormat("EEE, d MMM", Locale.getDefault()).format(now)

                // The text is intentionally rendered between the two image layers.
                clockPaint.textSize = height * 0.105f
                canvas.drawText(time, width / 2f, height * 0.245f, clockPaint)
                clockPaint.textSize = height * 0.027f
                canvas.drawText(date, width / 2f, height * 0.105f, clockPaint)

                // The transparent cut-out overlaps the clock to create the depth illusion.
                foregroundBitmap?.let { drawCover(canvas, it, width, height) }
            } catch (_: Exception) {
                // Keep the wallpaper service alive if an image URI becomes unavailable.
            } finally {
                if (canvas != null) {
                    try { holder.unlockCanvasAndPost(canvas) } catch (_: Exception) { }
                }
            }
        }

        private fun drawCover(canvas: Canvas, bitmap: Bitmap, width: Float, height: Float) {
            val scale = maxOf(width / bitmap.width, height / bitmap.height)
            val drawWidth = bitmap.width * scale
            val drawHeight = bitmap.height * scale
            val destination = RectF(
                (width - drawWidth) / 2f,
                (height - drawHeight) / 2f,
                (width + drawWidth) / 2f,
                (height + drawHeight) / 2f
            )
            canvas.drawBitmap(bitmap, null, destination, imagePaint)
        }
    }

    companion object {
        const val PREFS = "depthlock"
        const val KEY_BG = "background_uri"
        const val KEY_FG = "foreground_uri"
    }
}
