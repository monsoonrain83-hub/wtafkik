package ir.wtafkik.mapoverlay

/**
 * یک گزینه‌ی زوم: برچسب فارسی + نام فایل PNG پایه داخل assets/base/
 */
data class ZoomItem(val label: String, val assetFile: String, val enabled: Boolean = true)

data class SiteGroup(val title: String, val items: List<ZoomItem>)

object MenuData {

    val weatherbell = SiteGroup(
        title = "تفکیک نقشه‌های سایت ودربل (weatherbell.com)",
        items = listOf(
            ZoomItem("زوم ایران (استانی)", "iran_ostan.png"),
            ZoomItem("زوم ایران (شهرستانی)", "iran_shahrestan.png"),
            ZoomItem("راه‌های ایران (به زودی)", "", enabled = false),
        )
    )

    val weatherus = SiteGroup(
        title = "تفکیک نقشه‌های سایت کچلمن (weather.us)",
        items = listOf(
            ZoomItem("زوم ایران", "iran.png"),
            ZoomItem("اردبیل، آذربایجان‌غربی و شرقی، غرب گیلان، شمال زنجان", "azarbaijan.png"),
            ZoomItem("فارس، کهگیلویه و بویراحمد، بوشهر (تفکیک شهرستانی)", "iran_farsKohkBushehr.png"),
            ZoomItem("فارس، کهگیلویه و بویراحمد، بوشهر (تفکیک جزئیات)", "iran_fars_koh_bushehr_detail.png"),
            ZoomItem("شمال فارس", "north_of_fars.png"),
            ZoomItem("شمال‌غرب فارس", "northwest-of-fars.png"),
            ZoomItem("شرق فارس", "east_of_fars.png"),
            ZoomItem("جنوب‌شرق فارس", "southeast_of_fars.png"),
            ZoomItem("جنوب‌غرب فارس", "southwest_of_fars.png"),
            ZoomItem("خوزستان، چهارمحال و بختیاری، ک.بویراحمد، جنوب ایلام و لرستان، غرب اصفهان", "khuzestan.png"),
            ZoomItem("تهران، قم، البرز، قزوین، غرب سمنان، مرکز و غرب مازندران، شرق استان مرکزی", "tehran.png"),
            ZoomItem("گیلان", "gilan.png"),
            ZoomItem("هرمزگان", "hormozgan.png"),
            ZoomItem("راه‌های ایران (به زودی)", "", enabled = false),
        )
    )
}
