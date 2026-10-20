package sa.tv.launcher

import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.*
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.chrono.HijrahDate
import java.time.temporal.ChronoField
import java.time.temporal.ChronoUnit
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.Executors

class MainActivity : Activity() {
    // ===== إعدادات =====
    private val IQAMA = mapOf("Fajr" to 25, "Dhuhr" to 20, "Asr" to 20, "Maghrib" to 15, "Isha" to 20)
    private val CITY = "Riyadh"; private val COUNTRY = "SA"; private val METHOD = 4
    private val TEAM = "Al Hilal"
    private val PRIORITY = listOf("com.google.android.youtube.tv", "com.netflix.ninja", "com.stremio.one",
        "com.apple.atve.androidtv.appletv", "com.google.android.youtube.tvmusic")
    private val HIDE = listOf("com.tcl.", "com.google.android.tvlauncher", "com.google.android.apps.tv.launcherx",
        "com.google.android.tvrecommendations", "sa.tv.launcher")
    // ====================
    private val KEYS = listOf("Fajr", "Dhuhr", "Asr", "Maghrib", "Isha")
    private val AR = mapOf("Fajr" to "الفجر", "Dhuhr" to "الظهر", "Asr" to "العصر", "Maghrib" to "المغرب", "Isha" to "العشاء")
    private val DEF = mapOf("Fajr" to "04:21", "Dhuhr" to "11:40", "Asr" to "15:03", "Maghrib" to "17:30", "Isha" to "19:00")
    private val DAYS = listOf("الأحد", "الإثنين", "الثلاثاء", "الأربعاء", "الخميس", "الجمعة", "السبت")
    private val MONTHS = listOf("يناير", "فبراير", "مارس", "أبريل", "مايو", "يونيو", "يوليو", "أغسطس", "سبتمبر", "أكتوبر", "نوفمبر", "ديسمبر")
    private val HM = listOf("محرم", "صفر", "ربيع الأول", "ربيع الآخر", "جمادى الأولى", "جمادى الآخرة", "رجب", "شعبان", "رمضان", "شوال", "ذو القعدة", "ذو الحجة")
    private val CLUB = mapOf("Al Hilal" to "الهلال", "Al-Hilal" to "الهلال", "Al Nassr" to "النصر", "Al-Nassr" to "النصر", "Al Ittihad" to "الاتحاد",
        "Al-Ittihad" to "الاتحاد", "Al Ahli" to "الأهلي", "Al-Ahli" to "الأهلي", "Al Shabab" to "الشباب", "Al-Shabab" to "الشباب", "Al Hazem" to "الحزم",
        "Al-Hazm" to "الحزم", "Al Taawoun" to "التعاون", "Al-Taawoun" to "التعاون", "Al Fateh" to "الفتح", "Al-Fateh" to "الفتح", "Al Ettifaq" to "الاتفاق",
        "Al-Ettifaq" to "الاتفاق", "Al Qadsiah" to "القادسية", "Al-Qadsiah" to "القادسية", "Al Fayha" to "الفيحاء", "Al-Fayha" to "الفيحاء", "Damac" to "ضمك",
        "Al Okhdood" to "الأخدود", "Al Khaleej" to "الخليج", "Al Riyadh" to "الرياض", "Neom" to "نيوم")
    private val SOFT = 0xB8FFFFFF.toInt(); private val FAINT = 0x80FFFFFF.toInt()
    private val UP = 0xFF8FE3B8.toInt(); private val DOWN = 0xFFF19A9A.toInt()
    private val M = -1; private val W = -2

    private val h = Handler(Looper.getMainLooper())
    private val ex = Executors.newSingleThreadExecutor()
    private lateinit var prefs: SharedPreferences
    private lateinit var fD: Typeface; private lateinit var fM: Typeface; private lateinit var fT: Typeface
    private lateinit var wk: TextView; private lateinit var dt: TextView
    private lateinit var hh: TextView; private lateinit var mm: TextView; private lateinit var ap: TextView
    private lateinit var tk: Array<Array<TextView>>
    private lateinit var cp: Array<TextView>; private lateinit var cr: Array<TextView>; private lateinit var cm: Array<TextView>
    private lateinit var appName: TextView; private lateinit var dockRow: LinearLayout
    private var dockSig = ""; private var rdDate: LocalDate? = null; private var rdDays = 0; private var rdText = ""
    private var round = 0

    private fun dp(x: Int) = (x * resources.displayMetrics.density).toInt()
    private fun ad(s: String) = s.map { if (it in '0'..'9') '٠' + (it - '0') else it }.joinToString("")
    private fun set(t: TextView, s: CharSequence) { if (t.text.toString() != s.toString()) t.text = s }
    private fun tv(sp: Float, f: Typeface, c: Int = Color.WHITE, g: Int = Gravity.RIGHT) = TextView(this).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sp); typeface = f; setTextColor(c); gravity = g
        includeFontPadding = false; textDirection = View.TEXT_DIRECTION_RTL
    }
    private fun fl(w: Int, hh: Int, g: Int, l: Int = 0, t: Int = 0, r: Int = 0, b: Int = 0) =
        FrameLayout.LayoutParams(w, hh, g).apply { setMargins(dp(l), dp(t), dp(r), dp(b)) }
    private fun span(main: String, tail: String): CharSequence = SpannableStringBuilder("$main  $tail").apply {
        setSpan(RelativeSizeSpan(0.55f), main.length, length, 0); setSpan(ForegroundColorSpan(SOFT), main.length, length, 0)
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        prefs = getSharedPreferences("d", MODE_PRIVATE)
        fD = resources.getFont(R.font.thmanyahserifdisplay_regular)
        fM = resources.getFont(R.font.thmanyahserifdisplay_medium)
        fT = resources.getFont(R.font.thmanyahseriftext_regular)
        val root = FrameLayout(this); root.layoutDirection = View.LAYOUT_DIRECTION_LTR; root.setBackgroundColor(Color.BLACK)
        root.addView(ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP; setImageResource(R.drawable.bg) }, FrameLayout.LayoutParams(M, M))
        root.addView(View(this).apply { background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(0x00000000, 0x00000000, 0xCC000000.toInt())) }, FrameLayout.LayoutParams(M, M))

        wk = tv(23f, fM); dt = tv(12.5f, fT, SOFT)
        root.addView(wk, fl(W, W, Gravity.RIGHT or Gravity.TOP, 0, 40, 52, 0))
        root.addView(dt, fl(W, W, Gravity.RIGHT or Gravity.TOP, 0, 76, 52, 0))

        hh = tv(160f, fD, Color.WHITE, Gravity.CENTER); mm = tv(160f, fD, Color.WHITE, Gravity.CENTER); ap = tv(25f, fT, SOFT, Gravity.CENTER)
        val dots = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        for (i in 0..1) dots.addView(View(this).apply { setBackgroundColor(0xD9BEBEBE.toInt()); rotation = 45f },
            LinearLayout.LayoutParams(dp(21), dp(21)).apply { bottomMargin = if (i == 0) dp(36) else 0 })
        val clock = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; layoutDirection = View.LAYOUT_DIRECTION_LTR }
        clock.addView(ap, LinearLayout.LayoutParams(W, W).apply { rightMargin = dp(8) })
        clock.addView(hh, LinearLayout.LayoutParams(W, W))
        clock.addView(dots, LinearLayout.LayoutParams(W, W).apply { leftMargin = dp(12); rightMargin = dp(12) })
        clock.addView(mm, LinearLayout.LayoutParams(W, W))
        root.addView(clock, fl(W, W, Gravity.RIGHT or Gravity.TOP, 0, 108, 48, 0))

        val tkRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; layoutDirection = View.LAYOUT_DIRECTION_LTR }
        val labels = listOf("تاسي", "إس آند بي ٥٠٠", "بتكوين")
        tk = Array(3) { i ->
            val c = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.RIGHT }
            val a = arrayOf(tv(10f, fT, SOFT), tv(18f, fD), tv(9.5f, fT, SOFT))
            a[0].text = labels[i]; a[1].text = "—"
            a.forEach { c.addView(it, LinearLayout.LayoutParams(W, W)) }
            (a[1].layoutParams as LinearLayout.LayoutParams).topMargin = dp(3); (a[2].layoutParams as LinearLayout.LayoutParams).topMargin = dp(5)
            tkRow.addView(c, LinearLayout.LayoutParams(dp(95), W).apply { rightMargin = dp(20) })
            a
        }
        root.addView(tkRow, fl(W, W, Gravity.LEFT or Gravity.TOP, 100, 40, 0, 0))

        fun cell(): Array<TextView> = arrayOf(tv(10.5f, fT, SOFT), tv(28f, fM), tv(11.5f, fT, SOFT), tv(10.5f, fT, FAINT))
        cm = cell(); cr = cell(); cp = cell()
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; layoutDirection = View.LAYOUT_DIRECTION_LTR }
        listOf(cm, cr, cp).forEachIndexed { i, a ->
            val c = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.RIGHT; setPadding(dp(18), 0, dp(18), 0) }
            a.forEachIndexed { k, t -> c.addView(t, LinearLayout.LayoutParams(M, W).apply { topMargin = dp(if (k == 1) 6 else if (k == 0) 0 else 4) }) }
            row.addView(c, LinearLayout.LayoutParams(0, W, 1f))
            if (i < 2) row.addView(View(this).apply { setBackgroundColor(0x38FFFFFF) }, LinearLayout.LayoutParams(dp(1), M))
        }
        root.addView(row, fl(M, W, Gravity.BOTTOM, 34, 0, 34, 126))

        appName = tv(13f, fT, SOFT)
        root.addView(appName, fl(W, W, Gravity.RIGHT or Gravity.BOTTOM, 0, 0, 52, 104))
        dockRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; layoutDirection = View.LAYOUT_DIRECTION_LTR }
        val sc = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false; clipToPadding = false; setPadding(dp(52), dp(8), dp(52), dp(8)); layoutDirection = View.LAYOUT_DIRECTION_LTR
        }
        sc.addView(dockRow, FrameLayout.LayoutParams(W, M))
        root.addView(sc, fl(M, dp(100), Gravity.BOTTOM, 0, 0, 0, 6))
        setContentView(root)
        render(); tick()
    }

    override fun onResume() {
        super.onResume(); buildDock(); tick(); refresh(); h.post(clockLoop)
    }
    override fun onPause() { super.onPause(); h.removeCallbacksAndMessages(null) }
    override fun onBackPressed() {}

    private val clockLoop = object : Runnable { override fun run() { tick(); h.postDelayed(this, 1000) } }
    private val netLoop = Runnable { refresh() }

    // ===== التطبيقات =====
    private fun buildDock() {
        val pm = packageManager
        fun q(cat: String) = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(cat), 0)
        val all = (q(Intent.CATEGORY_LEANBACK_LAUNCHER) + q(Intent.CATEGORY_LAUNCHER))
            .filter { r -> HIDE.none { r.activityInfo.packageName.startsWith(it) } }.distinctBy { it.activityInfo.packageName }
            .sortedWith(compareBy<android.content.pm.ResolveInfo>({ r -> PRIORITY.indexOf(r.activityInfo.packageName).let { if (it < 0) 99 else it } }, { r -> r.loadLabel(pm).toString() }))
        val sig = all.joinToString(",") { it.activityInfo.packageName }
        if (sig == dockSig) return
        dockSig = sig; dockRow.removeAllViews()
        all.forEach { r ->
            val label = r.loadLabel(pm).toString(); val ban = r.activityInfo.loadBanner(pm)
            val t = tile(label, ban ?: r.loadIcon(pm), ban != null) {
                try { startActivity(Intent(Intent.ACTION_MAIN).setClassName(r.activityInfo.packageName, r.activityInfo.name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (e: Exception) {}
            }
            dockRow.addView(t, LinearLayout.LayoutParams(dp(148), dp(84)).apply { rightMargin = dp(12) })
        }
        dockRow.addView(tile("الإعدادات", null, false) {
            try { startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (e: Exception) {}
        }, LinearLayout.LayoutParams(dp(148), dp(84)))
        dockRow.getChildAt(0)?.requestFocus()
    }

    private fun tile(label: String, d: Drawable?, banner: Boolean, click: () -> Unit): View {
        val f = FrameLayout(this)
        f.background = GradientDrawable().apply { cornerRadius = dp(14).toFloat(); setColor(0x33FFFFFF) }
        f.clipToOutline = true
        f.outlineProvider = object : ViewOutlineProvider() { override fun getOutline(v: View, o: Outline) { o.setRoundRect(0, 0, v.width, v.height, dp(14).toFloat()) } }
        if (d != null && banner) {
            f.addView(ImageView(this).apply { setImageDrawable(d); scaleType = ImageView.ScaleType.CENTER_CROP }, FrameLayout.LayoutParams(M, M))
        } else {
            if (d != null) f.addView(ImageView(this).apply { setImageDrawable(d) }, fl(dp(40), dp(40), Gravity.CENTER_HORIZONTAL or Gravity.TOP, 0, 10, 0, 0))
            f.addView(tv(if (d == null) 15f else 11f, fT, Color.WHITE, Gravity.CENTER).apply { text = label; maxLines = 1 },
                fl(M, W, if (d == null) Gravity.CENTER else Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL, 6, 0, 6, if (d == null) 0 else 8))
        }
        f.isFocusable = true; f.setOnClickListener { click() }
        f.setOnFocusChangeListener { v, has ->
            v.animate().scaleX(if (has) 1.1f else 1f).scaleY(if (has) 1.1f else 1f).setDuration(110).start()
            v.foreground = if (has) GradientDrawable().apply { cornerRadius = dp(14).toFloat(); setStroke(dp(3), Color.WHITE); setColor(0) } else null
            if (has) appName.text = label
        }
        return f
    }

    // ===== الوقت والصلاة =====
    private fun t12(c: Calendar): String { val x = c.get(Calendar.HOUR_OF_DAY); return ad("${if (x % 12 == 0) 12 else x % 12}:${"%02d".format(c.get(Calendar.MINUTE))} ${if (x >= 12) "م" else "ص"}") }
    private fun prayer(k: String): Calendar {
        val s = (prefs.getString("p_$k", DEF[k]) ?: DEF[k]!!).split(":")
        return Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, s[0].toInt()); set(Calendar.MINUTE, s[1].toInt()); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
    }

    private fun tick() {
        val c = Calendar.getInstance(); val hr = c.get(Calendar.HOUR_OF_DAY)
        set(hh, ad(((hr % 12).let { if (it == 0) 12 else it }).toString())); set(mm, ad("%02d".format(c.get(Calendar.MINUTE)))); set(ap, if (hr >= 12) "م" else "ص")
        set(wk, DAYS[c.get(Calendar.DAY_OF_WEEK) - 1])
        val ld = LocalDate.now(); val hd = HijrahDate.from(ld)
        set(dt, ad("${c.get(Calendar.DAY_OF_MONTH)} ${MONTHS[c.get(Calendar.MONTH)]} ${c.get(Calendar.YEAR)}  ·  ${hd.get(ChronoField.DAY_OF_MONTH)} ${HM[hd.get(ChronoField.MONTH_OF_YEAR) - 1]} ${hd.get(ChronoField.YEAR)} هـ"))
        if (ld != rdDate) {
            rdDate = ld
            for (i in 1..400) { val x = ld.plusDays(i.toLong()); val y = HijrahDate.from(x)
                if (y.get(ChronoField.MONTH_OF_YEAR) == 9 && y.get(ChronoField.DAY_OF_MONTH) == 1) {
                    rdDays = i; rdText = ad("يبدأ ${DAYS[x.dayOfWeek.value % 7]} ${x.dayOfMonth} ${MONTHS[x.monthValue - 1]} تقريبًا"); break } }
        }
        set(cr[0], "رمضان"); set(cr[1], span(ad(rdDays.toString()), "يومًا")); set(cr[2], rdText); set(cr[3], "")
        val now = c.timeInMillis
        for (k in KEYS) {
            val a = prayer(k); val q = Calendar.getInstance().apply { timeInMillis = a.timeInMillis + IQAMA[k]!! * 60000L }
            if (now >= a.timeInMillis && now < q.timeInMillis) {
                val s = ((q.timeInMillis - now) / 1000).toInt()
                set(cp[0], "الإقامة بعد"); set(cp[1], ad("%02d:%02d".format(s / 60, s % 60)))
                set(cp[2], "أُذّن لصلاة ${AR[k]}"); set(cp[3], "الإقامة ${t12(q)}"); return
            }
        }
        var pick: String? = null; var w = Calendar.getInstance()
        for (k in KEYS) { val a = prayer(k); if (a.timeInMillis > now) { pick = k; w = a; break } }
        if (pick == null) { pick = "Fajr"; w = prayer("Fajr").apply { add(Calendar.DAY_OF_MONTH, 1) } }
        val diff = ((w.timeInMillis - now) / 60000).toInt(); val q = Calendar.getInstance().apply { timeInMillis = w.timeInMillis + IQAMA[pick]!! * 60000L }
        set(cp[0], "الصلاة القادمة"); set(cp[1], span(AR[pick]!!, t12(w)))
        set(cp[2], ad("بعد ${if (diff / 60 > 0) "${diff / 60} ساعات و" else ""}${diff % 60} دقيقة"))
        set(cp[3], ad("الإقامة ${t12(q)} (بعد ${IQAMA[pick]} دقيقة من الأذان)"))
    }

    // ===== البيانات الحية =====
    private fun num(v: Float) = ad(String.format(Locale.US, "%,d", v.toLong())).replace(",", "٬")
    private fun chg(t: TextView, c: Float) { t.text = ad((if (c >= 0) "▲ " else "▼ ") + String.format(Locale.US, "%.1f", Math.abs(c)).replace(".", "٫") + "٪"); t.setTextColor(if (c >= 0) UP else DOWN) }

    private fun render() {
        listOf("tasi", "spx", "btc").forEachIndexed { i, k ->
            if (prefs.contains(k)) { tk[i][1].text = (if (k == "btc") "$ " else "") + num(prefs.getFloat(k, 0f)); chg(tk[i][2], prefs.getFloat(k + "c", 0f)) }
        }
        val ms = prefs.getLong("m_ms", 0L)
        if (ms == 0L) { cm[0].text = "مباراة الهلال القادمة"; cm[1].text = "—"; return }
        val cal = Calendar.getInstance().apply { timeInMillis = ms }
        val d = ChronoUnit.DAYS.between(LocalDate.now(), Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate())
        val day = when (d) { 0L -> "اليوم"; 1L -> "بكرة"; else -> "${DAYS[cal.get(Calendar.DAY_OF_WEEK) - 1]} ${cal.get(Calendar.DAY_OF_MONTH)} ${MONTHS[cal.get(Calendar.MONTH)]}" }
        cm[0].text = "مباراة ${prefs.getString("m_team", "الهلال")} القادمة"
        cm[1].text = "${prefs.getString("m_home", "")} × ${prefs.getString("m_away", "")}"
        cm[2].text = ad("$day · ${t12(cal)}"); cm[3].text = "${prefs.getString("m_league", "")} · ${prefs.getString("m_venue", "")}"
    }

    private fun get(u: String): String? = try {
        (URL(u).openConnection() as HttpURLConnection).run { connectTimeout = 8000; readTimeout = 8000; setRequestProperty("User-Agent", "Mozilla/5.0"); inputStream.bufferedReader().use { it.readText() } }
    } catch (e: Exception) { null }

    private fun quote(sym: String): Pair<Float, Float>? {
        val s = get("https://query1.finance.yahoo.com/v8/finance/chart/" + URLEncoder.encode(sym, "UTF-8") + "?interval=1d&range=1d") ?: return null
        return try {
            val m = JSONObject(s).getJSONObject("chart").getJSONArray("result").getJSONObject(0).getJSONObject("meta")
            val p = m.getDouble("regularMarketPrice"); val pc = m.getDouble("chartPreviousClose"); Pair(p.toFloat(), ((p - pc) / pc * 100).toFloat())
        } catch (e: Exception) { null }
    }

    private fun refresh() {
        h.removeCallbacks(netLoop); h.postDelayed(netLoop, 60000)
        val withMatch = round++ % 30 == 0
        ex.execute {
            val e = prefs.edit()
            try { get("https://api.aladhan.com/v1/timingsByCity?city=$CITY&country=$COUNTRY&method=$METHOD")?.let { s ->
                val t = JSONObject(s).getJSONObject("data").getJSONObject("timings"); KEYS.forEach { k -> e.putString("p_$k", t.getString(k).substring(0, 5)) } } } catch (x: Exception) {}
            quote("^GSPC")?.let { e.putFloat("spx", it.first).putFloat("spxc", it.second) }
            quote("^TASI.SR")?.let { e.putFloat("tasi", it.first).putFloat("tasic", it.second) }
            try { get("https://api.coingecko.com/api/v3/simple/price?ids=bitcoin&vs_currencies=usd&include_24hr_change=true")?.let { s ->
                val b = JSONObject(s).getJSONObject("bitcoin"); e.putFloat("btc", b.getDouble("usd").toFloat()).putFloat("btcc", b.getDouble("usd_24h_change").toFloat()) } } catch (x: Exception) {}
            if (withMatch) try {
                val b = "https://www.thesportsdb.com/api/v1/json/3/"
                val ts = JSONObject(get(b + "searchteams.php?t=" + URLEncoder.encode(TEAM, "UTF-8")) ?: "{}").optJSONArray("teams")
                var id = ""; var name = ""
                if (ts != null) for (i in 0 until ts.length()) { val o = ts.getJSONObject(i); if (o.optString("strCountry") == "Saudi Arabia" && o.optString("strSport") == "Soccer") { id = o.getString("idTeam"); name = o.getString("strTeam"); break } }
                if (id != "") {
                    val ev = JSONObject(get(b + "eventsnext.php?id=$id") ?: "{}").optJSONArray("events")?.getJSONObject(0)
                    if (ev != null) {
                        val f = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
                        val tm = ev.optString("strTime", "00:00:00").let { if (it.length >= 8) it.substring(0, 8) else "00:00:00" }
                        fun a(x: String) = CLUB[x] ?: x
                        e.putLong("m_ms", f.parse(ev.getString("dateEvent") + "T" + tm)!!.time).putString("m_team", a(name))
                            .putString("m_home", a(ev.getString("strHomeTeam"))).putString("m_away", a(ev.getString("strAwayTeam")))
                            .putString("m_league", ev.optString("strLeague")).putString("m_venue", ev.optString("strVenue"))
                    }
                }
            } catch (x: Exception) {}
            e.apply()
            runOnUiThread { render(); tick() }
        }
    }
}
