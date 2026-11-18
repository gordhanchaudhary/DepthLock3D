package com.depthlock3d

import android.app.Activity
import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.Segmentation
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import kotlin.math.max

class MainActivity : Activity() {
    private lateinit var statusText: TextView
    private lateinit var progress: ProgressBar
    private lateinit var cutoutButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 40, 28, 28)
            setBackgroundColor(0xFFF5F6F8.toInt())
        }
        layout.addView(TextView(this).apply {
            text = "DepthLock 3D"
            textSize = 28f
            setTextColor(0xFF14202B.toInt())
        })
        layout.addView(TextView(this).apply {
            text = "Choose a photo, automatically separate the person, then apply the live depth wallpaper."
            textSize = 15f
            setTextColor(0xFF526170.toInt())
            setPadding(0, 8, 0, 18)
        })
        statusText = TextView(this).apply {
            textSize = 14f
            setTextColor(0xFF243342.toInt())
            setPadding(0, 0, 0, 12)
        }
        layout.addView(statusText)
        layout.addView(makeButton("1. Choose photo") { chooseImage(REQUEST_BACKGROUND) })
        cutoutButton = makeButton("2. Automatically remove background") { createPersonCutout() }
        layout.addView(cutoutButton)
        progress = ProgressBar(this).apply { visibility = ProgressBar.GONE }
        layout.addView(progress)
        layout.addView(makeButton("3. Preview / apply live wallpaper") { openWallpaperPicker() })
        layout.addView(TextView(this).apply {
            text = "Tip: Use a clear photo with the person fully visible. Automatic cut-out works best when the person is clearly separated from the background. Processing happens on your phone."
            textSize = 13f
            setTextColor(0xFF526170.toInt())
            setPadding(0, 24, 0, 0)
        })
        setContentView(layout)
        refreshStatus()
    }

    private fun makeButton(label: String, action: () -> Unit): Button = Button(this).apply {
        text = label
        isAllCaps = false
        setOnClickListener { action() }
    }

    private fun chooseImage(requestCode: Int) {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            type = "image/*"
            addCategory(Intent.CATEGORY_OPENABLE)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        startActivityForResult(intent, requestCode)
    }

    private fun createPersonCutout() {
        val prefs = getSharedPreferences(DepthWallpaperService.PREFS, MODE_PRIVATE)
        val uriText = prefs.getString(DepthWallpaperService.KEY_BG, null)
        if (uriText == null) {
            Toast.makeText(this, "पहले अपनी photo चुनें।", Toast.LENGTH_LONG).show()
            return
        }
        cutoutButton.isEnabled = false
        progress.visibility = ProgressBar.VISIBLE
        statusText.text = "Person detect हो रहा है… कृपया प्रतीक्षा करें।"

        Thread {
            try {
                val source = contentResolver.openInputStream(Uri.parse(uriText))!!.use { input ->
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    contentResolver.openInputStream(Uri.parse(uriText))!!.use { BitmapFactory.decodeStream(it, null, bounds) }
                    var sample = 1
                    while (max(bounds.outWidth / sample, bounds.outHeight / sample) > MAX_IMAGE_SIDE) sample *= 2
                    val options = BitmapFactory.Options().apply { inSampleSize = sample; inPreferredConfig = Bitmap.Config.ARGB_8888 }
                    BitmapFactory.decodeStream(input, null, options) ?: error("Photo open नहीं हुई")
                }
                runOnUiThread { runSegmentation(source) }
            } catch (e: Exception) {
                runOnUiThread {
                    finishProcessing("Photo पढ़ने में समस्या: ${e.localizedMessage ?: "unknown error"}")
                }
            }
        }.start()
    }

    private fun runSegmentation(source: Bitmap) {
        val options = SelfieSegmenterOptions.Builder()
            .setDetectorMode(SelfieSegmenterOptions.SINGLE_IMAGE_MODE)
            .build()
        val segmenter = Segmentation.getClient(options)
        val input = InputImage.fromBitmap(source, 0)
        segmenter.process(input)
            .addOnSuccessListener { mask ->
                try {
                    val output = makeTransparentCutout(source, mask.buffer, mask.width, mask.height)
                    val file = File(filesDir, "depthlock_person_cutout.png")
                    FileOutputStream(file).use { output.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    getSharedPreferences(DepthWallpaperService.PREFS, MODE_PRIVATE).edit()
                        .putString(DepthWallpaperService.KEY_FG, Uri.fromFile(file).toString())
                        .apply()
                    finishProcessing("Background हट गया! अब wallpaper preview/apply करें।")
                } catch (e: Exception) {
                    finishProcessing("Cut-out बनाने में समस्या: ${e.localizedMessage ?: "unknown error"}")
                } finally {
                    segmenter.close()
                    source.recycle()
                }
            }
            .addOnFailureListener { e ->
                segmenter.close()
                source.recycle()
                finishProcessing("Person detect नहीं हो पाया: ${e.localizedMessage ?: "कृपया दूसरी photo आज़माएँ।"}")
            }
    }

    private fun makeTransparentCutout(source: Bitmap, maskBuffer: ByteBuffer, maskWidth: Int, maskHeight: Int): Bitmap {
        val buffer = maskBuffer.duplicate().apply { rewind() }
        val result = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(source.width * source.height)
        val maskValues = FloatArray(maskWidth * maskHeight)
        for (i in maskValues.indices) maskValues[i] = buffer.getFloat()
        for (y in 0 until source.height) {
            val my = (y.toFloat() * maskHeight / source.height).toInt().coerceIn(0, maskHeight - 1)
            for (x in 0 until source.width) {
                val mx = (x.toFloat() * maskWidth / source.width).toInt().coerceIn(0, maskWidth - 1)
                val confidence = maskValues[my * maskWidth + mx]
                // Smooth the soft mask while suppressing low-confidence background pixels.
                val alpha = (((confidence - 0.32f) / 0.48f) * 255f).toInt().coerceIn(0, 255)
                val color = source.getPixel(x, y)
                pixels[y * source.width + x] = Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))
            }
        }
        result.setPixels(pixels, 0, source.width, 0, 0, source.width, source.height)
        return result
    }

    private fun finishProcessing(message: String) {
        progress.visibility = ProgressBar.GONE
        cutoutButton.isEnabled = true
        statusText.text = message
        refreshStatus(message)
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun openWallpaperPicker() {
        val prefs = getSharedPreferences(DepthWallpaperService.PREFS, MODE_PRIVATE)
        if (prefs.getString(DepthWallpaperService.KEY_BG, null) == null ||
            prefs.getString(DepthWallpaperService.KEY_FG, null) == null) {
            Toast.makeText(this, "पहले photo चुनें और background remove करें।", Toast.LENGTH_LONG).show()
            return
        }
        try {
            val intent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
                putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                    ComponentName(this@MainActivity, DepthWallpaperService::class.java))
            }
            startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(this, "इस फोन में live wallpaper picker नहीं खुल पाया।", Toast.LENGTH_LONG).show()
        }
    }

    @Deprecated("Legacy callback retained for broad Android compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK || data?.data == null || requestCode != REQUEST_BACKGROUND) return
        val uri = data.data!!
        try { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) { }
        getSharedPreferences(DepthWallpaperService.PREFS, MODE_PRIVATE).edit()
            .putString(DepthWallpaperService.KEY_BG, uri.toString())
            .remove(DepthWallpaperService.KEY_FG)
            .apply()
        Toast.makeText(this, "Photo selected. अब automatic background removal करें।", Toast.LENGTH_SHORT).show()
        refreshStatus()
    }

    private fun refreshStatus(extra: String? = null) {
        val prefs = getSharedPreferences(DepthWallpaperService.PREFS, MODE_PRIVATE)
        val bgReady = prefs.getString(DepthWallpaperService.KEY_BG, null) != null
        val fgReady = prefs.getString(DepthWallpaperService.KEY_FG, null) != null
        statusText.text = (extra?.plus("\n") ?: "") +
            "Photo: ${if (bgReady) "Selected ✓" else "Not selected"}\n" +
            "Person cut-out: ${if (fgReady) "Ready ✓" else "Not created yet"}"
    }

    companion object {
        private const val REQUEST_BACKGROUND = 101
        private const val MAX_IMAGE_SIDE = 1600
    }
}
