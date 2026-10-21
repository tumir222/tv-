package sa.tv.launcher

import android.content.Context
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import java.net.URL
import java.util.concurrent.Executors

val SOFT = 0xB8FFFFFF.toInt()
val FAINT = 0x80FFFFFF.toInt()
val UPC = 0xFF8FE3B8.toInt()
val DOWNC = 0xFFF19A9A.toInt()
const val MP = -1
const val WC = -2

object Fonts {
    lateinit var d: Typeface
    lateinit var m: Typeface
    lateinit var t: Typeface
    private var ok = false
    fun init(c: Context) {
        if (ok) return
        ok = true
        d = c.resources.getFont(R.font.thmanyahserifdisplay_regular)
        m = c.resources.getFont(R.font.thmanyahserifdisplay_medium)
        t = c.resources.getFont(R.font.thmanyahseriftext_regular)
    }
}

fun Context.dp(x: Int): Int = (x * resources.displayMetrics.density).toInt()

fun ad(s: String): String {
    val sb = StringBuilder()
    for (ch in s) sb.append(if (ch in '0'..'9') ('٠' + (ch - '0')) else ch)
    return sb.toString()
}

fun Context.tv(sp: Float, f: Typeface, c: Int = Color.WHITE, g: Int = Gravity.RIGHT): TextView = TextView(this).apply {
    setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
    typeface = f
    setTextColor(c)
    gravity = g
    includeFontPadding = false
    textDirection = View.TEXT_DIRECTION_RTL
    setShadowLayer(6f, 0f, 2f, 0x55000000)
}

fun rr(color: Int, r: Float): GradientDrawable = GradientDrawable().apply { cornerRadius = r; setColor(color) }

fun Context.fl(w: Int, h: Int, g: Int, l: Int = 0, t: Int = 0, r: Int = 0, b: Int = 0): FrameLayout.LayoutParams =
    FrameLayout.LayoutParams(w, h, g).apply { setMargins(dp(l), dp(t), dp(r), dp(b)) }

object Img {
    private val cache = LruCache<String, Bitmap>(120)
    private val ex = Executors.newFixedThreadPool(3)
    private val h = Handler(Looper.getMainLooper())
    fun load(v: ImageView, url: String?) {
        v.tag = url
        if (url == null) { v.setImageDrawable(null); return }
        val c = cache.get(url)
        if (c != null) { v.setImageBitmap(c); return }
        ex.execute {
            val b = try { URL(url).openStream().use { BitmapFactory.decodeStream(it) } } catch (e: Exception) { null }
            if (b != null) {
                cache.put(url, b)
                h.post { if (v.tag == url) v.setImageBitmap(b) }
            }
        }
    }
}
