package ir.wtafkik.mapoverlay

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import ir.wtafkik.mapoverlay.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var selectedZoom: ZoomItem? = null
    private var lastResult: OverlayEngine.Result? = null

    private val pickImageLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            if (uri != null) startProcessing(uri) else Unit
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        buildSiteMenu()

        binding.pickImageButton.setOnClickListener {
            pickImageLauncher.launch("image/*")
        }
        binding.backFromZoomButton.setOnClickListener { showZoomMenuFor(currentGroupTitle) }
        binding.backFromResultButton.setOnClickListener { showZoomMenuFor(currentGroupTitle) }
        binding.newImageButton.setOnClickListener { showProcessingScreen() }
        binding.saveShareButton.setOnClickListener { shareResult() }
    }

    private var currentGroupTitle: String = ""

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
    private var currentGroup: SiteGroup? = null

    private fun openGroup(group: SiteGroup) {
        currentGroup = group
        currentGroupTitle = group.title
        buildZoomMenu(group)
        showOnly(binding.zoomContainer)
    }

    private fun showZoomMenuFor(title: String) {
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
        val backBtn = makeMenuButton(getString(R.string.back)) { buildSiteMenu() }
        binding.zoomContainer.addView(backBtn)
    }

    // ---------------- مرحله ۳: انتخاب تصویر / پردازش ----------------
    private fun showProcessingScreen() {
        binding.selectedZoomText.text = selectedZoom?.label ?: ""
        binding.progressBar.visibility = View.GONE
        binding.processingText.visibility = View.GONE
        binding.pickImageButton.visibility = View.VISIBLE
        binding.backFromZoomButton.visibility = View.VISIBLE
        showOnly(binding.processingContainer)
    }

    private fun startProcessing(uri: Uri) {
        val zoom = selectedZoom ?: return
        binding.pickImageButton.visibility = View.GONE
        binding.backFromZoomButton.visibility = View.GONE
        binding.progressBar.visibility = View.VISIBLE
        binding.processingText.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    val outDir = File(getExternalFilesDir(null), "outputs").apply { mkdirs() }
                    val stamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                    val outFile = File(outDir, "map_${stamp}.jpg")
                    OverlayEngine.process(this@MainActivity, uri, zoom.assetFile, outFile)
                }
                lastResult = result
                showResult(result)
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, getString(R.string.error_generic), Toast.LENGTH_LONG).show()
                showProcessingScreen()
            }
        }
    }

    // ---------------- مرحله ۴: نتیجه ----------------
    private fun showResult(result: OverlayEngine.Result) {
        binding.previewImage.setImageURI(Uri.fromFile(result.outputFile))
        binding.resultDimsText.text = getString(R.string.result_dims, result.finalWidth, result.finalHeight)
        binding.scaledDownNotice.visibility = if (result.wasScaledDown) View.VISIBLE else View.GONE
        showOnly(binding.resultContainer)
    }

    private fun shareResult() {
        val result = lastResult ?: return
        val uri = FileProvider.getUriForFile(
            this,
            "ir.wtafkik.mapoverlay.fileprovider",
            result.outputFile
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/jpeg"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, getString(R.string.save_share)))
    }

    // ---------------- ابزارهای رابط کاربری ----------------
    private fun showOnly(view: LinearLayout) {
        binding.siteContainer.visibility = if (view === binding.siteContainer) View.VISIBLE else View.GONE
        binding.zoomContainer.visibility = if (view === binding.zoomContainer) View.VISIBLE else View.GONE
        binding.processingContainer.visibility = if (view === binding.processingContainer) View.VISIBLE else View.GONE
        binding.resultContainer.visibility = if (view === binding.resultContainer) View.VISIBLE else View.GONE
    }

    private fun makeSectionTitle(text: String): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = 15f
            setTextColor(getColor(R.color.brand_green_dark))
            setPadding(0, 0, 0, 24)
        }
    }

    private fun makeMenuButton(text: String, onClick: () -> Unit): Button {
        return Button(this).apply {
            this.text = text
            setTextColor(getColor(R.color.white))
            backgroundTintList = getColorStateList(R.color.brand_green)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 20 }
            setOnClickListener { onClick() }
        }
    }
}
