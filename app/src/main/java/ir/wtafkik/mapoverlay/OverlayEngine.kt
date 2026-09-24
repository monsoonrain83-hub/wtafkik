package ir.wtafkik.mapoverlay

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.net.Uri
import android.os.Build
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * موتور اصلی ترکیب تصویر:
 * - تصویر کاربر را دقیقاً به اندازه‌ی PNG پایه (همان زوم انتخابی) ریسایز می‌کند
 * - PNG پایه (مرزها/اسامی مکان‌ها) را رویش overlay می‌کند
 * - خروجی را با کیفیت ۱۰۰ به‌صورت JPG ذخیره می‌کند
 *
 * نکته‌ی مهم فنی: این پردازش کاملاً روی یک Canvas نرم‌افزاری (Bitmap-backed) انجام می‌شود،
 * نه روی یک View یا تکسچر گرافیکی — بنابراین هیچ محدودیت "حداکثر اندازه‌ی تکسچر GPU"
 * (که معمولاً حدود ۴۰۹۶ یا ۸۱۹۲ پیکسل است) روی آن اثر نمی‌گذارد. تنها محدودیت واقعی،
 * حافظه‌ی RAM در دسترس است؛ به همین دلیل PNG پایه به‌جای یک‌باره لود شدن، به‌صورت
 * نواری (strip) با BitmapRegionDecoder خوانده و ترسیم می‌شود تا هیچ‌وقت دو تصویر
 * کامل هم‌زمان در حافظه نباشد.
 */
object OverlayEngine {

    data class Result(
        val outputFile: File,
        val finalWidth: Int,
        val finalHeight: Int,
        val requestedWidth: Int,
        val requestedHeight: Int,
    ) {
        val wasScaledDown: Boolean
            get() = finalWidth < requestedWidth || finalHeight < requestedHeight
    }

    /**
     * @param context برای دسترسی به assets و ContentResolver
     * @param sourceUri تصویری که کاربر از گالری انتخاب کرده
     * @param assetFileName نام فایل PNG پایه داخل assets/base/
     * @param outputFile فایلی که خروجی JPG در آن نوشته می‌شود
     */
    fun process(context: Context, sourceUri: Uri, assetFileName: String, outputFile: File): Result {
        val (nativeW, nativeH) = readAssetPngSize(context, assetFileName)
        val maxMemory = Runtime.getRuntime().maxMemory()

        var (targetW, targetH) = computeSafeSize(nativeW, nativeH, maxMemory)

        var attempt = 0
        var lastError: OutOfMemoryError? = null
        while (attempt < 4) {
            try {
                renderAndSave(context, sourceUri, assetFileName, targetW, targetH, outputFile)
                return Result(outputFile, targetW, targetH, nativeW, nativeH)
            } catch (oom: OutOfMemoryError) {
                lastError = oom
                System.gc()
                // اگر حتی سایز ایمن هم زیاد بود، ۲۵٪ کوچک‌ترش کن و دوباره امتحان کن
                targetW = (targetW * 0.75).toInt().coerceAtLeast(200)
                targetH = (targetH * 0.75).toInt().coerceAtLeast(200)
                attempt++
            }
        }
        throw IllegalStateException("پردازش تصویر با کمبود حافظه مواجه شد.", lastError)
    }

    private fun renderAndSave(
        context: Context,
        sourceUri: Uri,
        assetFileName: String,
        targetW: Int,
        targetH: Int,
        outputFile: File,
    ) {
        val dest = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(dest)

        // ۱) تصویر کاربر را دقیقاً به اندازه‌ی مقصد بکش (مثل سایت اصلی که ابعاد را force می‌کند)
        drawSourceScaled(context, sourceUri, canvas, targetW, targetH)

        // ۲) PNG پایه را به‌صورت نواری روی همان canvas بکش (حفظ شفافیت خودکار است)
        drawBaseOverlayStripped(context, assetFileName, canvas, targetW, targetH)

        // ۳) ذخیره با کیفیت کامل
        FileOutputStream(outputFile).use { fos ->
            dest.compress(Bitmap.CompressFormat.JPEG, 100, fos)
            fos.flush()
        }
        dest.recycle()
    }

    private fun drawSourceScaled(context: Context, uri: Uri, canvas: Canvas, targetW: Int, targetH: Int) {
        val resolver = context.contentResolver

        val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val boundsStream = resolver.openInputStream(uri)
            ?: throw IllegalStateException("خواندن تصویر ورودی ممکن نشد.")
        boundsStream.use { BitmapFactory.decodeStream(it, null, boundsOpts) }
        val srcW = boundsOpts.outWidth
        val srcH = boundsOpts.outHeight
        if (srcW <= 0 || srcH <= 0) throw IllegalStateException("فرمت تصویر پشتیبانی نمی‌شود.")

        var sample = 1
        while (srcW / (sample * 2) >= targetW && srcH / (sample * 2) >= targetH) {
            sample *= 2
        }

        val decodeOpts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val rough = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, decodeOpts) }
            ?: throw IllegalStateException("رمزگشایی تصویر ورودی ممکن نشد.")

        val scaled = if (rough.width == targetW && rough.height == targetH) {
            rough
        } else {
            val s = Bitmap.createScaledBitmap(rough, targetW, targetH, true)
            if (s !== rough) rough.recycle()
            s
        }

        canvas.drawBitmap(scaled, 0f, 0f, null)
        scaled.recycle()
    }

    private fun drawBaseOverlayStripped(
        context: Context,
        assetFileName: String,
        canvas: Canvas,
        targetW: Int,
        targetH: Int,
    ) {
        val decoder = context.assets.open("base/$assetFileName").use { input ->
            newRegionDecoderCompat(input)
        } ?: throw IllegalStateException("فایل پایه‌ی این زوم پیدا نشد.")

        val nativeW = decoder.width
        val nativeH = decoder.height
        val scaleX = targetW.toFloat() / nativeW
        val scaleY = targetH.toFloat() / nativeH

        // ارتفاع هر نوار (به مقیاس تصویر اصلی PNG) طوری انتخاب می‌شود که بعد از
        // اسکیل‌شدن، حدود ۴۰۰ پیکسل ارتفاع داشته باشد — این‌طور هیچ‌وقت حجم زیادی
        // از PNG پایه هم‌زمان در حافظه نیست.
        val stripNativeHeight = max(8, (400f / max(scaleY, 0.0001f)).toInt())

        val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        var y = 0
        while (y < nativeH) {
            val bottom = min(y + stripNativeHeight, nativeH)
            val rect = Rect(0, y, nativeW, bottom)
            val stripOpts = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }
            val strip = try {
                decoder.decodeRegion(rect, stripOpts)
            } catch (oom: OutOfMemoryError) {
                decoder.recycle()
                throw oom
            } ?: break

            val destRect = RectF(0f, y * scaleY, targetW.toFloat(), bottom * scaleY)
            canvas.drawBitmap(strip, null, destRect, paint)
            strip.recycle()
            y = bottom
        }
        decoder.recycle()
    }

    @Suppress("DEPRECATION")
    private fun newRegionDecoderCompat(input: java.io.InputStream): BitmapRegionDecoder? {
        return if (Build.VERSION.SDK_INT >= 31) {
            BitmapRegionDecoder.newInstance(input)
        } else {
            BitmapRegionDecoder.newInstance(input, false)
        }
    }

    private fun readAssetPngSize(context: Context, assetFileName: String): Pair<Int, Int> {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.assets.open("base/$assetFileName").use { input ->
            BitmapFactory.decodeStream(input, null, opts)
        }
        if (opts.outWidth <= 0 || opts.outHeight <= 0) {
            throw IllegalStateException("خواندن ابعاد فایل پایه ممکن نشد.")
        }
        return Pair(opts.outWidth, opts.outHeight)
    }

    /**
     * حداکثر اندازه‌ای که می‌توان با حافظه‌ی در دسترس این گوشی، با خیال راحت پردازش کرد.
     * فقط بیت‌مپ مقصد (targetW × targetH × 4 بایت) را در نظر می‌گیریم چون تصویر پایه
     * به‌صورت نواری پردازش می‌شود و هیچ‌وقت کامل در حافظه نیست.
     */
    private fun computeSafeSize(nativeW: Int, nativeH: Int, maxMemory: Long): Pair<Int, Int> {
        val budgetBytes = (maxMemory * 0.5).toLong()
        val nativeBytes = nativeW.toLong() * nativeH.toLong() * 4L
        if (nativeBytes <= budgetBytes || budgetBytes <= 0) {
            return Pair(nativeW, nativeH)
        }
        val scale = sqrt(budgetBytes.toDouble() / nativeBytes.toDouble())
        val newW = (nativeW * scale).toInt().coerceAtLeast(200)
        val newH = (nativeH * scale).toInt().coerceAtLeast(200)
        return Pair(newW, newH)
    }
}
