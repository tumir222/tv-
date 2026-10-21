package sa.tv.launcher

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

class AmbientView(c: Context) : FrameLayout(c) {
    init { Fonts.init(c); Repo.init(c) }

    private val h = Handler(Looper.getMainLooper())
    private val wall = ImageView(c).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
    private val wk = c.tv(23f, Fonts.m)
    private val dt = c.tv(12.5f, Fonts.t, SOFT)
    private val hh = c.tv(165f, Fonts.d, Color.WHITE, Gravity.CENTER)
    private val mm = c.tv(165f, Fonts.d, Color.WHITE, Gravity.CENTER)
    private val ap = c.tv(25f, Fonts.t, SOFT, Gravity.CENTER)
    private val tk: Array<Array<TextView>>
    private val cm: Array<TextView>
    private val cr: Array<TextView>
    private val cp: Array<TextView>
    private val tkBox = LinearLayout(c)
    private var mi = 0
    private var tickN = 0

    init {
        layoutDirection = View.LAYOUT_DIRECTION_LTR
        setBackgroundColor(Color.BLACK)
        addView(wall, LayoutParams(MP, MP))
        addView(View(c).apply {
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(0x40000000, 0x00000000, 0x00000000, 0xB3000000.toInt()))
        }, LayoutParams(MP, MP))
        addView(wk, c.fl(WC, WC, Gravity.RIGHT or Gravity.TOP, 0, 40, 52, 0))
        addView(dt, c.fl(WC, WC, Gravity.RIGHT or Gravity.TOP, 0, 76, 52, 0))

        val dots = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }
        for (i in 0..1) dots.addView(View(c).apply { setBackgroundColor(0xD9BEBEBE.toInt()); rotation = 45f },
            LinearLayout.LayoutParams(c.dp(21), c.dp(21)).apply { bottomMargin = if (i == 0) c.dp(36) else 0 })
        val clock = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; layoutDirection = View.LAYOUT_DIRECTION_LTR }
        clock.addView(ap, LinearLayout.LayoutParams(WC, WC).apply { rightMargin = c.dp(8) })
        clock.addView(hh, LinearLayout.LayoutParams(WC, WC))
        clock.addView(dots, LinearLayout.LayoutParams(WC, WC).apply { leftMargin = c.dp(12); rightMargin = c.dp(12) })
        clock.addView(mm, LinearLayout.LayoutParams(WC, WC))
        addView(clock, c.fl(WC, WC, Gravity.RIGHT or Gravity.TOP, 0, 108, 48, 0))

        tkBox.orientation = LinearLayout.HORIZONTAL; tkBox.layoutDirection = View.LAYOUT_DIRECTION_LTR
        val labels = listOf("تاسي", "إس آند بي ٥٠٠", "بتكوين")
        tk = Array(3) { i ->
            val col = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.RIGHT }
            val a = arrayOf(c.tv(10f, Fonts.t, SOFT), c.tv(18f, Fonts.d), c.tv(9.5f, Fonts.t, SOFT))
            a[0].text = labels[i]; a[1].text = "—"
            a.forEach { col.addView(it, LinearLayout.LayoutParams(WC, WC)) }
            (a[1].layoutParams as LinearLayout.LayoutParams).topMargin = c.dp(3)
            (a[2].layoutParams as LinearLayout.LayoutParams).topMargin = c.dp(5)
            tkBox.addView(col, LinearLayout.LayoutParams(c.dp(95), WC).apply { rightMargin = c.dp(20) })
            a
        }
        addView(tkBox, c.fl(WC, WC, Gravity.LEFT or Gravity.TOP, 100, 40, 0, 0))

        fun cell(): Array<TextView> = arrayOf(c.tv(10.5f, Fonts.t, SOFT), c.tv(28f, Fonts.m), c.tv(11.5f, Fonts.t, SOFT), c.tv(10.5f, Fonts.t, FAINT))
        cm = cell(); cr = cell(); cp = cell()
        val row = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL; layoutDirection = View.LAYOUT_DIRECTION_LTR }
        listOf(cm, cr, cp).forEachIndexed { i, a ->
            val col = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.RIGHT; setPadding(c.dp(18), 0, c.dp(18), 0) }
            a.forEachIndexed { k, t -> col.addView(t, LinearLayout.LayoutParams(MP, WC).apply { topMargin = c.dp(if (k == 1) 6 else if (k == 0) 0 else 4) }) }
            row.addView(col, LinearLayout.LayoutParams(0, WC, 1f))
            if (i < 2) row.addView(View(c).apply { setBackgroundColor(0x38FFFFFF) }, LinearLayout.LayoutParams(c.dp(1), MP))
        }
        addView(row, c.fl(MP, WC, Gravity.BOTTOM, 34, 0, 34, 44))
    }

    private val loop = object : Runnable { override fun run() { tick(); h.postDelayed(this, 1000) } }
    fun start() { wall.setImageResource(Repo.wallRes(context)); h.removeCallbacks(loop); h.post(loop) }
    fun stop() { h.removeCallbacks(loop) }
    fun setWall() { wall.setImageResource(Repo.wallRes(context)) }

    private fun span(main: String, tail: String): CharSequence = SpannableStringBuilder("$main  $tail").apply {
        setSpan(RelativeSizeSpan(0.55f), main.length, length, 0)
        setSpan(ForegroundColorSpan(SOFT), main.length, length, 0)
    }

    private fun set(t: TextView, s: CharSequence) { if (t.text.toString() != s.toString()) t.text = s }

    private fun tick() {
        Repo.refresh()
        val c = java.util.Calendar.getInstance()
        val hr = c.get(java.util.Calendar.HOUR_OF_DAY)
        set(hh, ad((if (hr % 12 == 0) 12 else hr % 12).toString()))
        set(mm, ad("%02d".format(c.get(java.util.Calendar.MINUTE))))
        set(ap, if (hr >= 12) "م" else "ص")
        set(wk, Calc.weekday()); set(dt, Calc.dateLine())

        val keys = listOf("tasi", "spx", "btc"); val on = listOf("tasi", "spx", "btc")
        for (i in 0..2) {
            val show = Repo.gb(on[i], true)
            (tk[i][0].parent as View).visibility = if (show) View.VISIBLE else View.GONE
            val n = Repo.num(keys[i])
            if (n != null) {
                set(tk[i][1], (if (keys[i] == "btc") "$ " else "") + n)
                val ch = Repo.chg(keys[i] + "c")
                set(tk[i][2], ad((if (ch >= 0) "▲ " else "▼ ") + String.format(java.util.Locale.US, "%.1f", Math.abs(ch)).replace(".", "٫") + "٪"))
                tk[i][2].setTextColor(if (ch >= 0) UPC else DOWNC)
            }
        }

        val rd = Calc.ramadan()
        set(cr[0], "رمضان"); set(cr[1], span(ad(rd.first.toString()), "يومًا")); set(cr[2], rd.second); set(cr[3], "")

        val s = Calc.state()
        if (s.iqama) {
            set(cp[0], "الإقامة بعد"); set(cp[1], Calc.hms(s.secs))
            set(cp[2], "أُذّن لصلاة ${Repo.AR[s.k]}"); set(cp[3], "الإقامة ${Calc.t12(s.iq)}")
        } else {
            set(cp[0], "الصلاة القادمة · الأذان بعد")
            set(cp[1], span(Calc.hms(s.secs), "${Repo.AR[s.k]}  ${Calc.t12(s.adhan)}"))
            set(cp[2], "الإقامة ${Calc.t12(s.iq)}")
            set(cp[3], ad("بعد ${Repo.iq(s.k)} دقيقة من الأذان"))
        }

        tickN++
        if (tickN % 7 == 0) mi++
        val f = Repo.featured()
        if (f.isEmpty()) {
            set(cm[0], "المباراة القادمة"); set(cm[1], "—"); set(cm[2], ""); set(cm[3], "")
        } else {
            val m = f[mi % f.size]
            val lg = Repo.LEAGUES.firstOrNull { it.id == m.lg }?.name ?: ""
            set(cm[0], "مباراة قادمة · $lg")
            set(cm[1], "${Repo.ar(m.h)} × ${Repo.ar(m.a)}")
            set(cm[2], if (Repo.status(m) == 1) "مباشر الآن" else "${Calc.day(m.ms)} · ${Calc.t12(m.ms)}")
            set(cm[3], if (Repo.status(m) == 1) "" else Calc.rel(m.ms - System.currentTimeMillis()))
        }
    }
}
