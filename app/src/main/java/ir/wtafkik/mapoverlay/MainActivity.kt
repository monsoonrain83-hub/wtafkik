package ir.wtafkik.mapoverlay

import android.Manifest
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import ir.wtafkik.mapoverlay.databinding.ActivityMainBinding
import ir.wtafkik.mapoverlay.databinding.DialogInfoBinding
import ir.wtafkik.mapoverlay.databinding.DialogShareBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var selectedZoom: ZoomItem? = null
    private var currentGroup: SiteGroup? = null
    private var lastResult: OverlayEngine.Result? = null
    private var pendingSaveFile: File? = null

    private val pickImageLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            if (uri != null) startProcessing(uri)
        }

    private val pickFileLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            if (uri != null) startProcessing(uri)
        }

    private val requestStoragePermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            val file = pendingSaveFile
            pendingSaveFile = null
            if (granted && file != null) {
                saveLegacy(file)
            } else {
                Toast.makeText(this, getString(R.string.permission_needed), Toast.LENGTH_LONG).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        buildSiteMenu()

        binding.splashOverlay.setOnClickListener {
            binding.splashOverlay.animate()
                .alpha(0f)
                .setDuration(300)
                .withEndAction { binding.splashOverlay.visibility = View.GONE }
                .start()
        }

        binding.infoButton.setOnClickListener { showInfoDialog() }

        binding.pickImageButton.setOnClickListener { pickImageLauncher.launch("image/*") }
        binding.pickFileButton.setOnClickListener { pickFileLauncher.launch(arrayOf("image/*")) }
        binding.backFromZoomButton.setOnClickListener { showZoomMenu() }
        binding.backFromResultButton.setOnClickListener { showZoomMenu() }
        binding.newImageButton.setOnClickListener { showProcessingScreen() }
        binding.saveShareButton.setOnClickListener { openShareOptions() }
    }

    // ---------------- مرحله ۱: انتخاب سایت ----------------
    private fun buildSiteMenu() {
        binding.siteContainer.removeAllViews()
        binding.siteContainer.addView(makeMenuButton(getString(R.string.site_wb)) {
            openGroup(MenuData.weatherbell)
        })
        binding.siteContainer.addView(makeMenuButton(getString(R.string.site_wu)) {
            openGroup(MenuData.weatherus)
        })
        showOnly(binding.siteContainer)
    }

    // ---------------- مرحله ۲: انتخاب زوم ----------------
    private fun openGroup(group: SiteGroup) {
        currentGroup = group
        buildZoomMenu(group)
        showOnly(binding.zoomContainer)
    }

    private fun showZoomMenu() {
        val group = currentGroup ?: MenuData.weatherbell
        buildZoomMenu(group)
        showOnly(binding.zoomContainer)
    }

    private fun buildZoomMenu(group: SiteGroup) {
        binding.zoomContainer.removeAllViews()
        binding.zoomContainer.addView(makeSectionTitle(group.title))
        for (item in group.items) {
            binding.zoomContainer.addView(makeMenuButton(item.label) {
                selectedZoom = item
                showProcessingScreen()
            })
        }
        binding.zoomContainer.addView(makeMenuButton(getString(R.string.back)) { buildSiteMenu() })
    }

    // ---------------- مرحله ۳: انتخاب تصویر / پردازش ----------------
    private fun showProcessingScreen() {
        binding.selectedZoomText.text = selectedZoom?.label ?: ""
        binding.progressBar.visibility = View.GONE
        binding.processingText.visibility = View.GONE
        binding.pickImageButton.visibility = View.VISIBLE
        binding.pickFileButton.visibility = View.VISIBLE
        binding.backFromZoomButton.visibility = View.VISIBLE
        showOnly(binding.processingContainer)
    }

    private fun startProcessing(uri: Uri) {
        val zoom = selectedZoom ?: return
        binding.pickImageButton.visibility = View.GONE
        binding.pickFileButton.visibility = View.GONE
        binding.backFromZoomButton.visibility = View.GONE
        binding.progressBar.visibility = View.VISIBLE
        binding.processingText.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
                val (result, preview) = withContext(Dispatchers.IO) {
                    val outDir = File(getExternalFilesDir(null), "outputs").apply { mkdirs() }
                    val stamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                    val outFile = File(outDir, "map_${stamp}.jpg")
                    val r = OverlayEngine.process(this@MainActivity, uri, zoom.assetFile, outFile)
                    val thumb = decodeThumbnail(r.outputFile, 1024)
                    r to thumb
                }
                lastResult = result
                showResult(result, preview)
            } catch (e: Throwable) {
                val details = android.util.Log.getStackTraceString(e)
                AlertDialog.Builder(this@MainActivity)
                    .setTitle("جزئیات خطا (اسکرین‌شات بگیرید و بفرستید)")
                    .setMessage(details)
                    .setPositiveButton("باشه", null)
                    .show()
                showProcessingScreen()
            }
        }
    }

    // ---------------- مرحله ۴: نتیجه ----------------
    private fun showResult(result: OverlayEngine.Result, preview: Bitmap) {
        binding.previewImage.setImageBitmap(preview)
        binding.resultDimsText.text = getString(R.string.result_dims, result.finalWidth, result.finalHeight)
        binding.scaledDownNotice.visibility = if (result.wasScaledDown) View.VISIBLE else View.GONE
        showOnly(binding.resultContainer)
    }

    /** یک نسخه‌ی کوچک‌شده برای نمایش پیش‌نمایش می‌سازد؛ هیچ‌وقت تصویر کامل را دیکد نمی‌کند تا از OutOfMemory جلوگیری شود */
    private fun decodeThumbnail(file: File, maxDim: Int): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= maxDim && bounds.outHeight / (sample * 2) >= maxDim) {
            sample *= 2
        }
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeFile(file.absolutePath, opts)
            ?: throw IllegalStateException("ساخت پیش‌نمایش تصویر ممکن نشد.")
    }

    // ---------------- ذخیره / اشتراک‌گذاری ----------------
    private fun openShareOptions() {
        val result = lastResult ?: return
        val dialogBinding = DialogShareBinding.inflate(layoutInflater)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        dialogBinding.optionSaveGallery.setOnClickListener {
            dialog.dismiss()
            saveToGallery(result.outputFile)
        }
        dialogBinding.optionShareFile.setOnClickListener {
            dialog.dismiss()
            shareFile(result.outputFile, asGenericFile = true)
        }
        dialogBinding.optionSharePhoto.setOnClickListener {
            dialog.dismiss()
            shareFile(result.outputFile, asGenericFile = false)
        }
        dialog.show()
    }

    private fun shareFile(file: File, asGenericFile: Boolean) {
        val uri = FileProvider.getUriForFile(this, "ir.wtafkik.mapoverlay.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = if (asGenericFile) "application/octet-stream" else "image/jpeg"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, getString(R.string.save_share)))
    }

    private fun saveToGallery(file: File) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveViaMediaStore(file)
        } else if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
            == PackageManager.PERMISSION_GRANTED
        ) {
            saveLegacy(file)
        } else {
            pendingSaveFile = file
            requestStoragePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }

    private fun saveViaMediaStore(file: File) {
        try {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, file.name)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/MapOverlay")
            }
            val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("ساخت رکورد گالری ناموفق بود.")
            val out = contentResolver.openOutputStream(uri)
                ?: throw IllegalStateException("نوشتن فایل در گالری ناموفق بود.")
            out.use { stream -> file.inputStream().use { it.copyTo(stream) } }
            Toast.makeText(this, getString(R.string.saved_to_gallery), Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.save_failed), Toast.LENGTH_LONG).show()
        }
    }

    private fun saveLegacy(file: File) {
        try {
            @Suppress("DEPRECATION")
            val picturesDir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                "MapOverlay"
            )
            picturesDir.mkdirs()
            val dest = File(picturesDir, file.name)
            file.copyTo(dest, overwrite = true)
            android.media.MediaScannerConnection.scanFile(
                this, arrayOf(dest.absolutePath), arrayOf("image/jpeg"), null
            )
            Toast.makeText(this, getString(R.string.saved_to_gallery), Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.save_failed), Toast.LENGTH_LONG).show()
        }
    }

    // ---------------- دیالوگ اطلاعات سازنده ----------------
    private fun showInfoDialog() {
        val dialogBinding = DialogInfoBinding.inflate(layoutInflater)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialogBinding.openInstagramButton.setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://instagram.com/asemanestahban")))
        }
        dialog.show()
    }

    // ---------------- ابزارهای رابط کاربری ----------------
    private fun showOnly(view: LinearLayout) {
        binding.siteContainer.visibility = if (view === binding.siteContainer) View.VISIBLE else View.GONE
        binding.zoomContainer.visibility = if (view === binding.zoomContainer) View.VISIBLE else View.GONE
        binding.processingContainer.visibility = if (view === binding.processingContainer) View.VISIBLE else View.GONE
        binding.resultContainer.visibility = if (view === binding.resultContainer) View.VISIBLE else View.GONE
        applyWatermarks(showHome = view === binding.siteContainer)
    }

    private fun applyWatermarks(showHome: Boolean) {
        val group = currentGroup
        val showWb = showHome || group === MenuData.weatherbell
        val showWu = showHome || group === MenuData.weatherus
        binding.watermarkWb.visibility = if (showWb) View.VISIBLE else View.GONE
        binding.watermarkWu.visibility = if (showWu) View.VISIBLE else View.GONE
        // لوگوی آسمان استهبان فقط وقتی دیده می‌شود که لوگوی weather.us و/یا weatherbell دیده شود.
        // این لوگوها فقط پس‌زمینه‌ی صفحات اپ هستند و هیچ‌جا روی تصویر خروجی نقشه نمی‌افتند
        // (OverlayEngine اصلاً به آن‌ها دسترسی ندارد).
        binding.watermarkAseman.visibility = if (showWb || showWu) View.VISIBLE else View.GONE
    }

    private fun makeSectionTitle(text: String): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = 15f
            setTextColor(getColor(R.color.text_primary))
            setPadding(0, 0, 0, 20)
        }
    }

    private fun makeMenuButton(text: String, onClick: () -> Unit): MaterialButton {
        return MaterialButton(this).apply {
            this.text = text
            isAllCaps = false
            minHeight = (54 * resources.displayMetrics.density).toInt()
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = (14 * resources.displayMetrics.density).toInt() }
            setOnClickListener { onClick() }
        }
    }
}
