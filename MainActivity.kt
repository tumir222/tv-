package sa.tv.launcher

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.ResolveInfo
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.media.tv.TvContract
import android.media.tv.TvInputInfo
import android.media.tv.TvInputManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class Row(val t: Int, val label: String, val sub: String = "", val key: String = "", val opts: List<String> = emptyList(), val def: Boolean = true)

class MainActivity : Activity() {
    private val h = Handler(Looper.getMainLooper())
    private lateinit var root: FrameLayout
    private lateinit var home: FrameLayout
    private lateinit var amb: AmbientView
    private lateinit var mx: FrameLayout
    private lateinit var st: FrameLayout
    private lateinit var wall: ImageView
    private var mode = 0 // 0 رئيسية، 1 سكون، 2 مباريات، 3 إعدادات

    // الرئيسية
    private lateinit var hello: TextView
    private lateinit var htime: TextView
    private lateinit var hdate: TextView
    private lateinit var wxChip: TextView
    private lateinit var chipRow: View
    private lateinit var mCard: LinearLayout
    private lateinit var pCard: LinearLayout
    private lateinit var mLg: TextView
    private lateinit var mDots: TextView
    private lateinit var mHomeName: TextView
    private lateinit var mAwayName: TextView
    private lateinit var mHomeImg: ImageView
    private lateinit var mAwayImg: ImageView
    private lateinit var mBig: TextView
    private lateinit var mSmall: TextView
    private lateinit var pIcon: TextView
    private lateinit var pLbl: TextView
    private lateinit var pVal: TextView
    private lateinit var pSub: TextView
    private lateinit var pBar: View
    private lateinit var dockRow: LinearLayout
    private var dockSig = ""
    private var mi = 0
    private var tickN = 0

    // المباريات
    private lateinit var mxChips: LinearLayout
    private lateinit var mxScroll: HorizontalScrollView
    private lateinit var mxList: LinearLayout
    private lateinit var mxVs: ScrollView
    private var fx = 0

    // الإعدادات
    private lateinit var stChips: LinearLayout
    private lateinit var stScroll: HorizontalScrollView
    private lateinit var stList: LinearLayout
    private lateinit var stVs: ScrollView
    private var sc = 0
    private var sr = 0
    private var sz = 0 // 0 تبويبات، 1 صفوف
    private val CATS = listOf("المظهر", "الصلاة", "المباريات", "البطولات", "الأسهم", "النظام")

    private val PRIORITY = listOf("com.google.android.youtube.tv", "com.netflix.ninja", "com.stremio.one",
        "com.apple.atve.androidtv.appletv", "com.google.android.youtube.tvmusic")
    private val HIDE = listOf("com.tcl.", "com.google.android.tvlauncher", "com.google.android.apps.tv.launcherx",
        "com.google.android.tvrecommendations", "sa.tv.launcher")

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        Fonts.init(this); Repo.init(this)
        root = FrameLayout(this)
        root.layoutDirection = View.LAYOUT_DIRECTION_LTR
        root.setBackgroundColor(Color.BLACK)
        home = buildHome()
        amb = AmbientView(this); amb.visibility = View.GONE
        mx = buildMatches(); mx.visibility = View.GONE
        st = buildSettings(); st.visibility = View.GONE
        root.addView(home, FrameLayout.LayoutParams(MP, MP))
        root.addView(mx, FrameLayout.LayoutParams(MP, MP))
        root.addView(st, FrameLayout.LayoutParams(MP, MP))
        root.addView(amb, FrameLayout.LayoutParams(MP, MP))
        setContentView(root)
        applyView()
    }

    override fun onResume() {
        super.onResume()
        wall.setImageResource(Repo.wallRes(this))
        buildDock(); tick(); Repo.refresh(true)
        Repo.listeners.add(update)
        h.post(loop)
    }

    override fun onPause() {
        super.onPause()
        h.removeCallbacksAndMessages(null)
        Repo.listeners.remove(update)
        amb.stop()
    }

    override fun onBackPressed() { if (mode != 0) show(0) }

    private val update = Runnable { tick(); if (mode == 2) drawMx(); if (mode == 3) drawSt() }
    private val loop = object : Runnable { override fun run() { tick(); h.postDelayed(this, 1000) } }

    private fun show(m: Int) {
        mode = m
        if (m != 1) amb.stop()
        amb.visibility = if (m == 1) View.VISIBLE else View.GONE
        mx.visibility = if (m == 2) View.VISIBLE else View.GONE
        st.visibility = if (m == 3) View.VISIBLE else View.GONE
        if (m == 1) amb.start()
        if (m == 2) { fx = 0; drawMx() }
        if (m == 3) { sz = 0; sc = 0; sr = 0; drawSt() }
        if (m == 0) { dockRow.getChildAt(0)?.requestFocus() }
    }

    override fun dispatchKeyEvent(e: KeyEvent): Boolean {
        if (mode == 0) return super.dispatchKeyEvent(e)
        if (e.action != KeyEvent.ACTION_DOWN) return true
        when (mode) {
            1 -> show(0)
            2 -> mxKey(e.keyCode)
            3 -> stKey(e.keyCode)
        }
        return true
    }

    // ================= الرئيسية =================
    private fun pill(text: String, click: () -> Unit): TextView = tv(12.5f, Fonts.t, Color.WHITE, Gravity.CENTER).apply {
        this.text = text
        setPadding(dp(16), 0, dp(16), 0)
        background = rr(0x2EFFFFFF, dp(21).toFloat())
        isFocusable = true
        setOnClickListener { click() }
        setOnFocusChangeListener { v, has ->
            v.background = rr(if (has) Color.WHITE else 0x2EFFFFFF, dp(21).toFloat())
            (v as TextView).setTextColor(if (has) 0xFF16130F.toInt() else Color.WHITE)
            v.setShadowLayer(if (has) 0f else 6f, 0f, 2f, 0x55000000)
        }
    }

    private fun side(): Triple<LinearLayout, ImageView, TextView> {
        val l = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL }
        val img = ImageView(this).apply { scaleType = ImageView.ScaleType.FIT_CENTER }
        val n = tv(13f, Fonts.m, Color.WHITE, Gravity.CENTER).apply { maxLines = 1 }
        l.addView(img, LinearLayout.LayoutParams(dp(44), dp(44)))
        l.addView(n, LinearLayout.LayoutParams(dp(112), WC).apply { topMargin = dp(4) })
        return Triple(l, img, n)
    }

    private fun buildHome(): FrameLayout {
        val f = FrameLayout(this)
        wall = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
        f.addView(wall, FrameLayout.LayoutParams(MP, MP))
        f.addView(View(this).apply {
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(0x61000000, 0x00000000, 0x14000000, 0x99000000.toInt()))
        }, FrameLayout.LayoutParams(MP, MP))

        // ساعة وتحية
        hello = tv(15f, Fonts.t, SOFT); htime = tv(52f, Fonts.d); hdate = tv(14f, Fonts.t, SOFT)
        wxChip = tv(11.5f, Fonts.t, Color.WHITE, Gravity.CENTER).apply { setPadding(dp(11), dp(5), dp(11), dp(5)); background = rr(0x2EFFFFFF, dp(15).toFloat()) }
        val hc = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.RIGHT }
        hc.addView(hello, LinearLayout.LayoutParams(WC, WC))
        hc.addView(htime, LinearLayout.LayoutParams(WC, WC))
        hc.addView(hdate, LinearLayout.LayoutParams(WC, WC).apply { topMargin = dp(4) })
        hc.addView(wxChip, LinearLayout.LayoutParams(WC, WC).apply { topMargin = dp(10); gravity = Gravity.RIGHT })
        chipRow = wxChip
        f.addView(hc, fl(WC, WC, Gravity.RIGHT or Gravity.TOP, 0, 32, 48, 0))

        // أزرار فوق
        val ic = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; layoutDirection = View.LAYOUT_DIRECTION_LTR }
        fun add(p: TextView) = ic.addView(p, LinearLayout.LayoutParams(WC, dp(42)).apply { rightMargin = dp(10) })
        add(pill("⚙") { show(3) }.apply { textSize = 18f; setPadding(dp(13), 0, dp(13), 0) })
        add(pill("HDMI") { showInputs() })
        add(pill("الخلفية") { Repo.si("wall", (Repo.gi("wall", 0) + 1) % 5); wall.setImageResource(Repo.wallRes(this)); amb.setWall() })
        add(pill("المباريات") { show(2) })
        add(pill("شاشة السكون") { show(1) })
        f.addView(ic, fl(WC, WC, Gravity.LEFT or Gravity.TOP, 48, 39, 0, 0))

        // بطاقة المباراة
        mCard = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; background = rr(0x4D0C0C10, dp(17).toFloat()); setPadding(dp(15), dp(10), dp(15), dp(10)) }
        mLg = tv(9.5f, Fonts.t, SOFT); mDots = tv(9.5f, Fonts.t, SOFT, Gravity.LEFT)
        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        top.addView(mDots, LinearLayout.LayoutParams(0, WC, 1f)); top.addView(mLg, LinearLayout.LayoutParams(0, WC, 3f))
        mCard.addView(top, LinearLayout.LayoutParams(MP, WC))
        val hs = side(); val aw = side()
        mHomeImg = hs.second; mHomeName = hs.third; mAwayImg = aw.second; mAwayName = aw.third
        mBig = tv(25f, Fonts.d, Color.WHITE, Gravity.CENTER); mSmall = tv(10f, Fonts.t, SOFT, Gravity.CENTER)
        val mid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER }
        mid.addView(mBig, LinearLayout.LayoutParams(WC, WC)); mid.addView(mSmall, LinearLayout.LayoutParams(WC, WC).apply { topMargin = dp(3) })
        val mr = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; layoutDirection = View.LAYOUT_DIRECTION_LTR }
        mr.addView(aw.first, LinearLayout.LayoutParams(0, WC, 1f))
        mr.addView(mid, LinearLayout.LayoutParams(0, WC, 1f))
        mr.addView(hs.first, LinearLayout.LayoutParams(0, WC, 1f))
        mCard.addView(mr, LinearLayout.LayoutParams(MP, WC).apply { topMargin = dp(6) })
        f.addView(mCard, fl(dp(385), WC, Gravity.LEFT or Gravity.TOP, 48, 205, 0, 0))

        // بطاقة الصلاة
        pCard = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; background = rr(0x4D0C0C10, dp(17).toFloat()); setPadding(dp(15), dp(11), dp(15), dp(11)) }
        pIcon = tv(22f, Fonts.t, Color.WHITE, Gravity.CENTER); pLbl = tv(11.5f, Fonts.t, SOFT)
        val pr = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; layoutDirection = View.LAYOUT_DIRECTION_RTL }
        pr.addView(pIcon, LinearLayout.LayoutParams(dp(28), WC)); pr.addView(pLbl, LinearLayout.LayoutParams(WC, WC).apply { rightMargin = dp(8) })
        pCard.addView(pr, LinearLayout.LayoutParams(MP, WC))
        pVal = tv(36f, Fonts.m, Color.WHITE, Gravity.LEFT or Gravity.CENTER_VERTICAL).apply { textDirection = View.TEXT_DIRECTION_LTR }
        pCard.addView(pVal, LinearLayout.LayoutParams(MP, WC).apply { topMargin = dp(4) })
        pSub = tv(10.5f, Fonts.t, SOFT)
        pCard.addView(pSub, LinearLayout.LayoutParams(MP, WC).apply { topMargin = dp(3) })
        val bar = FrameLayout(this).apply { background = rr(0x2EFFFFFF, dp(2).toFloat()) }
        pBar = View(this).apply { background = rr(Color.WHITE, dp(2).toFloat()) }
        bar.addView(pBar, FrameLayout.LayoutParams(0, MP))
        pCard.addView(bar, LinearLayout.LayoutParams(MP, dp(3)).apply { topMargin = dp(8) })
        f.addView(pCard, fl(dp(232), WC, Gravity.RIGHT or Gravity.TOP, 0, 205, 48, 0))

        // شريط التطبيقات
        dockRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; layoutDirection = View.LAYOUT_DIRECTION_LTR }
        val hsv = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false; clipToPadding = false; clipChildren = false
            setPadding(dp(48), dp(16), dp(48), dp(8)); layoutDirection = View.LAYOUT_DIRECTION_LTR
        }
        hsv.addView(dockRow, FrameLayout.LayoutParams(WC, WC))
        f.addView(hsv, fl(MP, WC, Gravity.BOTTOM, 0, 0, 0, 18))
        return f
    }

    private fun buildDock() {
        val pm = packageManager
        fun q(cat: String) = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(cat), 0)
        val all = (q(Intent.CATEGORY_LEANBACK_LAUNCHER) + q(Intent.CATEGORY_LAUNCHER))
            .filter { r -> HIDE.none { r.activityInfo.packageName.startsWith(it) } }
            .distinctBy { it.activityInfo.packageName }
            .sortedWith(compareBy<ResolveInfo>({ r -> PRIORITY.indexOf(r.activityInfo.packageName).let { if (it < 0) 99 else it } }, { r -> r.loadLabel(pm).toString() }))
        val sig = all.joinToString(",") { it.activityInfo.packageName }
        if (sig == dockSig) return
        dockSig = sig
        dockRow.removeAllViews()
        all.forEach { r ->
            val label = r.loadLabel(pm).toString()
            val t = tile(label, r.loadIcon(pm)) {
                try { startActivity(Intent(Intent.ACTION_MAIN).setClassName(r.activityInfo.packageName, r.activityInfo.name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (e: Exception) { }
            }
            dockRow.addView(t, LinearLayout.LayoutParams(dp(100), WC).apply { rightMargin = dp(18) })
        }
        dockRow.addView(tile("الإعدادات", null) { show(3) }, LinearLayout.LayoutParams(dp(100), WC))
        dockRow.getChildAt(0)?.requestFocus()
    }

    private fun tile(label: String, d: android.graphics.drawable.Drawable?, click: () -> Unit): View {
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL }
        val box = FrameLayout(this)
        box.background = rr(0x33FFFFFF, dp(24).toFloat())
        box.clipToOutline = true
        box.outlineProvider = object : ViewOutlineProvider() { override fun getOutline(v: View, o: Outline) { o.setRoundRect(0, 0, v.width, v.height, dp(24).toFloat()) } }
        if (d != null) box.addView(ImageView(this).apply { setImageDrawable(d); scaleType = ImageView.ScaleType.FIT_CENTER }, fl(MP, MP, Gravity.CENTER, 14, 14, 14, 14))
        else box.addView(tv(38f, Fonts.t, Color.WHITE, Gravity.CENTER).apply { text = "⚙" }, FrameLayout.LayoutParams(MP, MP))
        col.addView(box, LinearLayout.LayoutParams(dp(100), dp(100)))
        val lb = tv(12.5f, Fonts.t, Color.WHITE, Gravity.CENTER).apply { text = label; maxLines = 1; alpha = 0f }
        col.addView(lb, LinearLayout.LayoutParams(dp(110), WC).apply { topMargin = dp(8) })
        col.isFocusable = true
        col.setOnClickListener { click() }
        col.setOnFocusChangeListener { v, has ->
            v.animate().scaleX(if (has) 1.1f else 1f).scaleY(if (has) 1.1f else 1f).setDuration(110).start()
            box.foreground = if (has) GradientDrawable().apply { cornerRadius = dp(24).toFloat(); setStroke(dp(3), Color.WHITE); setColor(0) } else null
            lb.alpha = if (has || Repo.gb("labels", false)) 1f else 0f
        }
        return col
    }

    private fun showInputs() {
        try {
            val tm = getSystemService(TV_INPUT_SERVICE) as TvInputManager
            val list: List<TvInputInfo> = tm.tvInputList.filter { it.isPassthroughInput }
            if (list.isEmpty()) { Toast.makeText(this, "لم يتم العثور على مصادر إدخال", Toast.LENGTH_LONG).show(); return }
            val names = list.map { it.loadLabel(this)?.toString() ?: it.id }.toTypedArray()
            AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert).setTitle("مصدر الإدخال")
                .setItems(names) { _, i ->
                    try { startActivity(Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(list[i].id)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (e: Exception) { }
                }.show()
        } catch (e: Exception) { Toast.makeText(this, "تعذر فتح مصادر الإدخال", Toast.LENGTH_LONG).show() }
    }

    private fun applyView() {
        val c = Repo.gb("chips", true); val k = Repo.gb("cards", true)
        chipRow.visibility = if (c) View.VISIBLE else View.GONE
        mCard.visibility = if (k) View.VISIBLE else View.GONE
        pCard.visibility = if (k) View.VISIBLE else View.GONE
        for (i in 0 until dockRow.childCount) {
            val col = dockRow.getChildAt(i) as LinearLayout
            if (!col.hasFocus()) col.getChildAt(1).alpha = if (Repo.gb("labels", false)) 1f else 0f
        }
    }

    private fun tick() {
        val c = java.util.Calendar.getInstance()
        val hr = c.get(java.util.Calendar.HOUR_OF_DAY)
        hello.text = if (hr < 12) "صباح الخير" else "مساء الخير"
        htime.text = ad("${if (hr % 12 == 0) 12 else hr % 12}:${"%02d".format(c.get(java.util.Calendar.MINUTE))}")
        hdate.text = Calc.weekday() + "،  " + Calc.dateLine().split("  ·  ")[0]
        val w = Repo.weatherText()
        wxChip.text = if (w == "") "—" else w

        val s = Calc.state()
        pIcon.text = Calc.icon(s.k)
        pLbl.text = Repo.AR[s.k] + (if (s.iqama) " · الإقامة بعد" else " · الأذان بعد")
        pVal.text = Calc.hms(s.secs)
        pSub.text = "الأذان ${Calc.t12(s.adhan)}  ·  الإقامة ${Calc.t12(s.iq)}"
        val total = dp(232 - 30)
        (pBar.layoutParams as FrameLayout.LayoutParams).width = (total * s.frac.coerceIn(0f, 1f)).toInt()
        pBar.requestLayout()

        tickN++
        if (tickN % 7 == 0) mi++
        val f = Repo.featured()
        if (f.isEmpty()) {
            mLg.text = "المباراة القادمة"; mDots.text = ""; mHomeName.text = ""; mAwayName.text = ""
            mHomeImg.setImageDrawable(null); mAwayImg.setImageDrawable(null); mBig.text = "—"; mSmall.text = "جاري التحميل"
        } else {
            val m = f[mi % f.size]
            mLg.text = Repo.LEAGUES.firstOrNull { it.id == m.lg }?.name ?: ""
            mDots.text = f.indices.joinToString(" ") { if (it == mi % f.size) "●" else "○" }
            mHomeName.text = Repo.ar(m.h); mAwayName.text = Repo.ar(m.a)
            if (mHomeImg.tag != m.hb || mHomeImg.drawable == null) Img.load(mHomeImg, m.hb)
            if (mAwayImg.tag != m.ab || mAwayImg.drawable == null) Img.load(mAwayImg, m.ab)
            val live = Repo.status(m) == 1
            mBig.text = if (live) "● مباشر" else Calc.t12(m.ms)
            mBig.setTextColor(if (live) 0xFFFF6B6B.toInt() else Color.WHITE)
            mSmall.text = if (live) "قيد اللعب" else Calc.day(m.ms) + " · " + Calc.rel(m.ms - System.currentTimeMillis())
        }
    }

    // ================= المباريات =================
    private fun buildMatches(): FrameLayout {
        val f = FrameLayout(this)
        f.setBackgroundColor(0xB30A0A0C.toInt())
        f.addView(tv(32f, Fonts.m).apply { text = "المباريات" }, fl(WC, WC, Gravity.RIGHT or Gravity.TOP, 0, 32, 48, 0))
        f.addView(tv(11.5f, Fonts.t, SOFT).apply { text = "تتحدث تلقائيًا · يمين/يسار للبطولات · فوق/تحت للتمرير · رجوع" }, fl(WC, WC, Gravity.LEFT or Gravity.TOP, 48, 46, 0, 0))
        mxChips = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; layoutDirection = View.LAYOUT_DIRECTION_RTL }
        mxScroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; layoutDirection = View.LAYOUT_DIRECTION_RTL; setPadding(dp(48), 0, dp(48), 0); clipToPadding = false }
        mxScroll.addView(mxChips, FrameLayout.LayoutParams(WC, WC))
        f.addView(mxScroll, fl(MP, WC, Gravity.TOP, 0, 88, 0, 0))
        mxList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(110), 0, dp(110), dp(20)) }
        mxVs = ScrollView(this).apply { isVerticalScrollBarEnabled = false }
        mxVs.addView(mxList, FrameLayout.LayoutParams(MP, WC))
        f.addView(mxVs, fl(MP, MP, Gravity.TOP, 0, 140, 0, 0))
        return f
    }

    private fun chipView(text: String, on: Boolean, dim: Boolean): TextView = tv(12f, Fonts.t, if (on && !dim) 0xFF111111.toInt() else Color.WHITE, Gravity.CENTER).apply {
        this.text = text
        setShadowLayer(0f, 0f, 0f, 0)
        setPadding(dp(14), dp(6), dp(14), dp(6))
        background = rr(if (on) (if (dim) 0x40FFFFFF else Color.WHITE) else 0x20FFFFFF, dp(17).toFloat())
    }

    private fun drawChips(box: LinearLayout, sv: HorizontalScrollView, names: List<String>, sel: Int, dim: Boolean) {
        box.removeAllViews()
        names.forEachIndexed { i, n -> box.addView(chipView(n, i == sel, dim), LinearLayout.LayoutParams(WC, WC).apply { leftMargin = dp(7) }) }
        sv.post {
            val c = box.getChildAt(sel) ?: return@post
            sv.smoothScrollTo(c.left - (sv.width - c.width) / 2, 0)
        }
    }

    private fun mxNames(): List<String> = listOf("الكل") + Repo.LEAGUES.filter { Repo.followed(it.id) }.map { it.name }
    private fun mxIds(): List<String> = listOf("all") + Repo.LEAGUES.filter { Repo.followed(it.id) }.map { it.id }

    private fun teamBlock(name: String, badge: String?): LinearLayout {
        val l = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; layoutDirection = View.LAYOUT_DIRECTION_RTL }
        val img = ImageView(this).apply { scaleType = ImageView.ScaleType.FIT_CENTER }
        Img.load(img, badge)
        l.addView(img, LinearLayout.LayoutParams(dp(30), dp(30)))
        l.addView(tv(13.5f, Fonts.t, Color.WHITE).apply { text = name; maxLines = 1 }, LinearLayout.LayoutParams(0, WC, 1f).apply { rightMargin = dp(9) })
        return l
    }

    private fun drawMx() {
        val names = mxNames(); val ids = mxIds()
        if (fx >= names.size) fx = names.size - 1
        drawChips(mxChips, mxScroll, names, fx, false)
        mxList.removeAllViews()
        val id = ids[fx]
        val now = System.currentTimeMillis()
        val L = Repo.matches.filter { (id == "all" || it.lg == id) && Repo.followed(it.lg) }
        val live = L.filter { Repo.status(it, now) == 1 }
        val pre = L.filter { Repo.status(it, now) == 0 }.sortedBy { it.ms }.take(14)
        val fin = L.filter { Repo.status(it, now) >= 2 }.sortedByDescending { it.ms }.take(12)
        fun header(t: String) = mxList.addView(tv(13f, Fonts.t, SOFT).apply { text = t }, LinearLayout.LayoutParams(MP, WC).apply { topMargin = dp(14); bottomMargin = dp(6) })
        fun row(m: Match) {
            val r = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; layoutDirection = View.LAYOUT_DIRECTION_LTR; background = rr(0x20FFFFFF, dp(15).toFloat()); setPadding(dp(14), dp(8), dp(14), dp(8)) }
            val s = Repo.status(m, now)
            val big = when (s) { 2 -> ad("${m.hs} – ${m.asc}"); 1 -> "● مباشر"; 0 -> Calc.t12(m.ms); else -> "انتهت" }
            val small = when (s) { 2 -> "انتهت · " + Calc.day(m.ms); 1 -> "قيد اللعب"; 0 -> Calc.day(m.ms) + " · " + Calc.rel(m.ms - now); else -> "بانتظار النتيجة" }
            val mid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER }
            mid.addView(tv(18f, Fonts.m, if (s == 1) 0xFFFF6B6B.toInt() else Color.WHITE, Gravity.CENTER).apply { text = big }, LinearLayout.LayoutParams(WC, WC))
            mid.addView(tv(9.5f, Fonts.t, SOFT, Gravity.CENTER).apply { text = small }, LinearLayout.LayoutParams(WC, WC))
            r.addView(teamBlock(Repo.ar(m.a), m.ab), LinearLayout.LayoutParams(0, WC, 1f))
            r.addView(mid, LinearLayout.LayoutParams(dp(150), WC))
            r.addView(teamBlock(Repo.ar(m.h), m.hb), LinearLayout.LayoutParams(0, WC, 1f))
            mxList.addView(r, LinearLayout.LayoutParams(MP, WC).apply { bottomMargin = dp(7) })
        }
        if (live.isNotEmpty()) { header("مباشرة الآن"); live.forEach { row(it) } }
        if (pre.isNotEmpty()) { header("قادمة"); pre.forEach { row(it) } }
        if (fin.isNotEmpty()) { header("آخر النتائج"); fin.forEach { row(it) } }
        if (live.isEmpty() && pre.isEmpty() && fin.isEmpty())
            mxList.addView(tv(15f, Fonts.t, SOFT, Gravity.CENTER).apply { text = if (Repo.matches.isEmpty()) "جاري تحميل المباريات…" else "لا توجد مباريات متاحة لهذه البطولة الآن" }, LinearLayout.LayoutParams(MP, WC).apply { topMargin = dp(40) })
        mxVs.scrollTo(0, 0)
    }

    private fun mxKey(k: Int) {
        when (k) {
            KeyEvent.KEYCODE_DPAD_LEFT -> { fx = minOf(mxNames().size - 1, fx + 1); drawMx() }
            KeyEvent.KEYCODE_DPAD_RIGHT -> { fx = maxOf(0, fx - 1); drawMx() }
            KeyEvent.KEYCODE_DPAD_DOWN -> mxVs.smoothScrollBy(0, dp(120))
            KeyEvent.KEYCODE_DPAD_UP -> mxVs.smoothScrollBy(0, -dp(120))
            KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE -> show(0)
        }
    }

    // ================= الإعدادات =================
    private fun buildSettings(): FrameLayout {
        val f = FrameLayout(this)
        f.setBackgroundColor(0xB80A0A0C.toInt())
        f.addView(tv(32f, Fonts.m).apply { text = "الإعدادات" }, fl(WC, WC, Gravity.RIGHT or Gravity.TOP, 0, 32, 48, 0))
        f.addView(tv(11.5f, Fonts.t, SOFT).apply { text = "أسهم للتنقل · موافق للتغيير · رجوع للشاشة الرئيسية" }, fl(WC, WC, Gravity.LEFT or Gravity.TOP, 48, 46, 0, 0))
        stChips = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; layoutDirection = View.LAYOUT_DIRECTION_RTL }
        stScroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; layoutDirection = View.LAYOUT_DIRECTION_RTL; setPadding(dp(48), 0, dp(48), 0); clipToPadding = false }
        stScroll.addView(stChips, FrameLayout.LayoutParams(WC, WC))
        f.addView(stScroll, fl(MP, WC, Gravity.TOP, 0, 88, 0, 0))
        stList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(160), 0, dp(160), dp(20)) }
        stVs = ScrollView(this).apply { isVerticalScrollBarEnabled = false }
        stVs.addView(stList, FrameLayout.LayoutParams(MP, WC))
        f.addView(stVs, fl(MP, MP, Gravity.TOP, 0, 140, 0, 0))
        return f
    }

    // أنواع: 0 مفتاح، 1 خيارات، 2 دقائق إقامة، 3 اختصار، 4 معلومة
    private fun rows(c: Int): List<Row> = when (c) {
        0 -> listOf(
            Row(1, "الخلفية", "صورة تتغير كل يوم، أو اختر منظرًا ثابتًا", "wall", listOf("تلقائي يوميًا", "غروب الرصيف", "الوادي", "ليل الجبال", "القمم الثلجية")),
            Row(0, "الطقس تحت التاريخ", "شريط صغير بدرجة الحرارة", "chips", def = true),
            Row(0, "بطاقتا المباريات والصلاة", "تظهر في الشاشة الرئيسية", "cards", def = true),
            Row(0, "إظهار أسماء كل التطبيقات", "بدل إظهار اسم التطبيق المختار فقط", "labels", def = false))
        1 -> listOf(
            Row(1, "المدينة", "تحدد مواقيت الصلاة والطقس", "city", Repo.CITIES.map { it[0] }),
            Row(1, "طريقة الحساب", "طريقة تقويم المواقيت", "method", Repo.METHODS.map { it.first })) +
            Repo.KEYS.map { Row(2, "إقامة " + Repo.AR[it], "الدقائق بين الأذان والإقامة", it) }
        2 -> listOf(Row(1, "فريقي المفضل", "تظهر مبارياته أولًا في البطاقة وشاشة السكون", "team", Repo.TEAMS.map { it.first }))
        3 -> Repo.LEAGUES.map { Row(0, it.name, "", "f_" + it.id, def = true) }
        4 -> listOf(Row(0, "بتكوين", "السعر بالدولار", "btc", def = true), Row(0, "إس آند بي ٥٠٠", "المؤشر الأمريكي", "spx", def = true), Row(0, "تاسي", "مؤشر السوق السعودي", "tasi", def = true))
        else -> listOf(
            Row(3, "إعدادات التلفاز", "كل إعدادات النظام", "sys"), Row(3, "الشبكة والإنترنت", "", "wifi"), Row(3, "الصورة", "", "disp"),
            Row(3, "الصوت", "", "sound"), Row(3, "إدارة التطبيقات", "", "apps"), Row(3, "الواجهة الافتراضية", "اختر هذي الواجهة كواجهة رئيسية", "home"),
            Row(4, "إصدار الواجهة", "", "ver"))
    }

    private fun drawSt() {
        drawChips(stChips, stScroll, CATS, sc, sz == 1)
        stList.removeAllViews()
        val R = rows(sc)
        if (sr >= R.size) sr = R.size - 1
        var focused: View? = null
        R.forEachIndexed { i, x ->
            val on = sz == 1 && i == sr
            val r = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; layoutDirection = View.LAYOUT_DIRECTION_RTL
                background = rr(if (on) Color.WHITE else 0x20FFFFFF, dp(15).toFloat()); setPadding(dp(17), dp(11), dp(17), dp(11))
                setOnClickListener { sz = 1; sr = i; act(x, 0, true); drawSt() }
            }
            val txtC = if (on) 0xFF16130F.toInt() else Color.WHITE
            val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            col.addView(tv(17f, Fonts.m, txtC).apply { text = x.label; setShadowLayer(0f, 0f, 0f, 0) }, LinearLayout.LayoutParams(WC, WC))
            if (x.sub != "") col.addView(tv(11f, Fonts.t, if (on) 0x9E16130F.toInt() else SOFT).apply { text = x.sub; setShadowLayer(0f, 0f, 0f, 0) }, LinearLayout.LayoutParams(WC, WC).apply { topMargin = dp(2) })
            r.addView(col, LinearLayout.LayoutParams(0, WC, 1f))
            when (x.t) {
                0 -> {
                    val v = Repo.gb(x.key, x.def)
                    val track = FrameLayout(this).apply { layoutDirection = View.LAYOUT_DIRECTION_LTR; background = rr(if (v) 0xFF35C47A.toInt() else if (on) 0x33000000 else 0x40FFFFFF, dp(13).toFloat()) }
                    val knob = View(this).apply { background = rr(Color.WHITE, dp(10).toFloat()) }
                    track.addView(knob, FrameLayout.LayoutParams(dp(20), dp(20)).apply { topMargin = dp(3); leftMargin = if (v) dp(23) else dp(3) })
                    r.addView(track, LinearLayout.LayoutParams(dp(46), dp(26)))
                }
                1 -> r.addView(tv(14f, Fonts.t, txtC, Gravity.LEFT).apply { text = "‹ " + x.opts[Repo.gi(x.key, 0).coerceIn(0, x.opts.size - 1)] + " ›"; setShadowLayer(0f, 0f, 0f, 0) }, LinearLayout.LayoutParams(WC, WC))
                2 -> r.addView(tv(14f, Fonts.t, txtC, Gravity.LEFT).apply { textDirection = View.TEXT_DIRECTION_LTR; text = ad("− ${Repo.iq(x.key)} دقيقة +"); setShadowLayer(0f, 0f, 0f, 0) }, LinearLayout.LayoutParams(WC, WC))
                4 -> r.addView(tv(14f, Fonts.t, txtC, Gravity.LEFT).apply { text = "٢٫٠"; setShadowLayer(0f, 0f, 0f, 0) }, LinearLayout.LayoutParams(WC, WC))
                else -> r.addView(tv(14f, Fonts.t, txtC, Gravity.LEFT).apply { text = "‹"; setShadowLayer(0f, 0f, 0f, 0) }, LinearLayout.LayoutParams(WC, WC))
            }
            stList.addView(r, LinearLayout.LayoutParams(MP, WC).apply { bottomMargin = dp(8) })
            if (on) focused = r
        }
        val fv = focused
        if (fv != null) stVs.post { stVs.requestChildRectangleOnScreen(stList, Rect(0, fv.top - dp(40), stList.width, fv.bottom + dp(40)), false) }
    }

    private fun act(x: Row, d: Int, enter: Boolean) {
        when (x.t) {
            0 -> if (enter || d != 0) { Repo.sb(x.key, !Repo.gb(x.key, x.def)); changed(x.key) }
            1 -> {
                val n = x.opts.size; var c = Repo.gi(x.key, 0)
                c = if (enter) (c + 1) % n else (c + (if (d > 0) 1 else -1) + n) % n
                Repo.si(x.key, c); changed(x.key)
            }
            2 -> {
                var v = Repo.iq(x.key) + (if (enter) 5 else d * 5)
                if (enter && v > 60) v = 5
                Repo.si("iq_" + x.key, v.coerceIn(5, 60))
            }
            3 -> if (enter) {
                val a = when (x.key) {
                    "wifi" -> Settings.ACTION_WIFI_SETTINGS; "disp" -> Settings.ACTION_DISPLAY_SETTINGS; "sound" -> Settings.ACTION_SOUND_SETTINGS
                    "apps" -> Settings.ACTION_APPLICATION_SETTINGS; "home" -> Settings.ACTION_HOME_SETTINGS; else -> Settings.ACTION_SETTINGS
                }
                try { startActivity(Intent(a).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (e: Exception) {
                    try { startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (e2: Exception) { }
                }
            }
        }
    }

    private fun changed(key: String) {
        when (key) {
            "wall" -> { wall.setImageResource(Repo.wallRes(this)); amb.setWall() }
            "city", "method" -> { Repo.clearPrayers(); Repo.refresh(true) }
            else -> applyView()
        }
    }

    private fun stKey(k: Int) {
        if (k == KeyEvent.KEYCODE_BACK || k == KeyEvent.KEYCODE_ESCAPE) { show(0); return }
        val R = rows(sc)
        if (sz == 0) {
            when (k) {
                KeyEvent.KEYCODE_DPAD_LEFT -> sc = minOf(CATS.size - 1, sc + 1)
                KeyEvent.KEYCODE_DPAD_RIGHT -> sc = maxOf(0, sc - 1)
                KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> { sz = 1; sr = 0 }
            }
            drawSt(); return
        }
        val x = R[sr]
        when (k) {
            KeyEvent.KEYCODE_DPAD_DOWN -> sr = minOf(R.size - 1, sr + 1)
            KeyEvent.KEYCODE_DPAD_UP -> if (sr == 0) sz = 0 else sr--
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> act(x, 0, true)
            KeyEvent.KEYCODE_DPAD_RIGHT -> act(x, 1, false)
            KeyEvent.KEYCODE_DPAD_LEFT -> act(x, -1, false)
        }
        drawSt()
    }
}
