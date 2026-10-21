package sa.tv.launcher

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors

class League(val id: String, val name: String, val tsdb: String, val kw: String)
class Match(val lg: String, val h: String, val a: String, val hb: String?, val ab: String?, val ms: Long, val hs: Int, val asc: Int)

object Repo {
    val KEYS = listOf("Fajr", "Dhuhr", "Asr", "Maghrib", "Isha")
    val AR = mapOf("Fajr" to "الفجر", "Dhuhr" to "الظهر", "Asr" to "العصر", "Maghrib" to "المغرب", "Isha" to "العشاء")
    private val DEF = mapOf("Fajr" to "04:21", "Dhuhr" to "11:40", "Asr" to "15:03", "Maghrib" to "17:30", "Isha" to "19:00")
    private val DEF_IQ = mapOf("Fajr" to 25, "Dhuhr" to 20, "Asr" to 20, "Maghrib" to 15, "Isha" to 20)

    // مدينة: اسم عربي، اسم لاتيني، خط عرض، خط طول
    val CITIES = listOf(
        listOf("الرياض", "Riyadh", "24.71", "46.67"), listOf("جدة", "Jeddah", "21.54", "39.17"),
        listOf("مكة المكرمة", "Makkah", "21.39", "39.86"), listOf("المدينة المنورة", "Madinah", "24.47", "39.61"),
        listOf("الدمام", "Dammam", "26.43", "50.10"), listOf("أبها", "Abha", "18.22", "42.50"), listOf("تبوك", "Tabuk", "28.38", "36.57"))
    val METHODS = listOf("أم القرى" to 4, "رابطة العالم الإسلامي" to 3, "الهيئة المصرية" to 5, "جامعة العلوم بكراتشي" to 1, "أمريكا الشمالية" to 2)
    val TEAMS = listOf("الهلال" to "hilal", "النصر" to "nassr", "الأهلي" to "ahli", "الاتحاد" to "ittihad", "الشباب" to "shabab",
        "ريال مدريد" to "real madrid", "برشلونة" to "barcelona", "مانشستر سيتي" to "manchester city", "ليفربول" to "liverpool",
        "مانشستر يونايتد" to "manchester united", "أرسنال" to "arsenal", "بايرن ميونخ" to "bayern")
    val LEAGUES = listOf(
        League("spl", "دوري روشن السعودي", "4668", "Saudi"),
        League("epl", "الدوري الإنجليزي", "4328", "Premier"),
        League("liga", "الدوري الإسباني", "4335", "Spanish|La Liga"),
        League("bun", "الدوري الألماني", "4331", "German|Bundesliga"),
        League("ned", "الدوري الهولندي", "4337", "Dutch|Eredivisie"),
        League("tur", "الدوري التركي", "4339", "Turkish|Super Lig"),
        League("bra", "الدوري البرازيلي", "4351", "Brazil"),
        League("ucl", "دوري أبطال أوروبا", "4480", "Champions"),
        League("uel", "الدوري الأوروبي", "4481", "Europa"),
        League("lib", "كوبا ليبرتادوريس", "4501", "Libertadores"),
        League("wc", "كأس العالم", "4429", "World Cup"))

    private lateinit var p: SharedPreferences
    private var inited = false
    private val ex = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var busy = false
    @Volatile private var last = 0L
    private var lastMatches = 0L
    @Volatile var matches: List<Match> = emptyList()
    val listeners = CopyOnWriteArrayList<Runnable>()

    fun init(c: Context) {
        if (inited) return
        inited = true
        p = c.applicationContext.getSharedPreferences("d", Context.MODE_PRIVATE)
        loadMatches()
    }

    fun gi(k: String, d: Int) = p.getInt(k, d)
    fun si(k: String, v: Int) { p.edit().putInt(k, v).apply() }
    fun gb(k: String, d: Boolean) = p.getBoolean(k, d)
    fun sb(k: String, v: Boolean) { p.edit().putBoolean(k, v).apply() }
    fun iq(k: String) = p.getInt("iq_$k", DEF_IQ[k] ?: 20)
    fun prayerStr(k: String): String = p.getString("p_$k", DEF[k]) ?: DEF[k]!!
    fun clearPrayers() { val e = p.edit(); KEYS.forEach { e.remove("p_$it") }; e.apply() }
    fun city(): List<String> = CITIES[gi("city", 0).coerceIn(0, CITIES.size - 1)]
    fun followed(id: String) = gb("f_$id", true)

    fun wallRes(c: Context): Int {
        val w = gi("wall", 0)
        val i = if (w == 0) Calendar.getInstance().get(Calendar.DAY_OF_MONTH) % 4 else w - 1
        return c.resources.getIdentifier("wall$i", "drawable", c.packageName)
    }

    // ===== شبكة =====
    private fun get(u: String): String? = try {
        val c = URL(u).openConnection() as HttpURLConnection
        c.connectTimeout = 8000; c.readTimeout = 8000
        c.setRequestProperty("User-Agent", "Mozilla/5.0")
        c.inputStream.bufferedReader().use { it.readText() }
    } catch (e: Exception) { null }

    fun refresh(force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (busy || (!force && now - last < 50000)) return
        busy = true; last = now
        ex.execute {
            try { work() } catch (e: Exception) { } finally {
                busy = false
                main.post { listeners.forEach { it.run() } }
            }
        }
    }

    private fun quote(sym: String): Pair<Float, Float>? {
        val s = get("https://query1.finance.yahoo.com/v8/finance/chart/" + URLEncoder.encode(sym, "UTF-8") + "?interval=1d&range=1d") ?: return null
        return try {
            val m = JSONObject(s).getJSONObject("chart").getJSONArray("result").getJSONObject(0).getJSONObject("meta")
            val pr = m.getDouble("regularMarketPrice"); val pc = m.getDouble("chartPreviousClose")
            Pair(pr.toFloat(), ((pr - pc) / pc * 100).toFloat())
        } catch (e: Exception) { null }
    }

    private fun work() {
        val e = p.edit()
        val c = city()
        try {
            get("https://api.aladhan.com/v1/timingsByCity?city=${c[1]}&country=SA&method=${METHODS[gi("method", 0).coerceIn(0, METHODS.size - 1)].second}")?.let { s ->
                val t = JSONObject(s).getJSONObject("data").getJSONObject("timings")
                KEYS.forEach { k -> e.putString("p_$k", t.getString(k).substring(0, 5)) }
            }
        } catch (x: Exception) { }
        quote("^GSPC")?.let { e.putFloat("spx", it.first).putFloat("spxc", it.second) }
        quote("^TASI.SR")?.let { e.putFloat("tasi", it.first).putFloat("tasic", it.second) }
        try {
            get("https://api.coingecko.com/api/v3/simple/price?ids=bitcoin&vs_currencies=usd&include_24hr_change=true")?.let { s ->
                val b = JSONObject(s).getJSONObject("bitcoin")
                e.putFloat("btc", b.getDouble("usd").toFloat()).putFloat("btcc", b.getDouble("usd_24h_change").toFloat())
            }
        } catch (x: Exception) { }
        try {
            get("https://api.open-meteo.com/v1/forecast?latitude=${c[2]}&longitude=${c[3]}&current=temperature_2m,weather_code")?.let { s ->
                val cur = JSONObject(s).getJSONObject("current")
                e.putInt("wx_t", Math.round(cur.getDouble("temperature_2m")).toInt()).putInt("wx_c", cur.getInt("weather_code")).putString("wx_city", c[0])
            }
        } catch (x: Exception) { }
        e.apply()
        val now = System.currentTimeMillis()
        if (now - lastMatches > 170000 || matches.isEmpty()) {
            lastMatches = now
            val all = ArrayList<Match>()
            for (l in LEAGUES) {
                val got = league(l)
                if (got.isNotEmpty()) all.addAll(got) else all.addAll(matches.filter { it.lg == l.id })
            }
            if (all.isNotEmpty()) { matches = all; saveMatches() }
        }
    }

    private fun badge(s: String?): String? {
        if (s == null || s.isBlank() || s == "null") return null
        return if (s.endsWith(".png") || s.endsWith(".jpg")) "$s/medium" else s
    }

    private fun league(l: League): List<Match> {
        val out = ArrayList<Match>()
        val rx = Regex(l.kw, RegexOption.IGNORE_CASE)
        val f = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
        for (path in listOf("eventsnextleague", "eventspastleague")) {
            val s = get("https://www.thesportsdb.com/api/v1/json/3/$path.php?id=${l.tsdb}") ?: continue
            val arr = try { JSONObject(s).optJSONArray("events") } catch (x: Exception) { null } ?: continue
            for (i in 0 until arr.length()) {
                try {
                    val e = arr.getJSONObject(i)
                    if (!rx.containsMatchIn(e.optString("strLeague"))) continue
                    var ts = e.optString("strTimestamp")
                    if (ts.length < 19) ts = e.optString("dateEvent") + "T" + e.optString("strTime", "00:00:00").take(8)
                    val ms = f.parse(ts.take(19))?.time ?: continue
                    val hs = e.optString("intHomeScore").toIntOrNull() ?: -1
                    val asc = e.optString("intAwayScore").toIntOrNull() ?: -1
                    out.add(Match(l.id, e.getString("strHomeTeam"), e.getString("strAwayTeam"),
                        badge(e.optString("strHomeTeamBadge")), badge(e.optString("strAwayTeamBadge")), ms, hs, asc))
                } catch (x: Exception) { }
            }
        }
        return out
    }

    private fun saveMatches() {
        val a = JSONArray()
        matches.forEach {
            a.put(JSONObject().put("l", it.lg).put("h", it.h).put("a", it.a).put("hb", it.hb ?: "").put("ab", it.ab ?: "")
                .put("m", it.ms).put("hs", it.hs).put("as", it.asc))
        }
        p.edit().putString("matches", a.toString()).apply()
    }

    private fun loadMatches() {
        try {
            val a = JSONArray(p.getString("matches", "[]"))
            val l = ArrayList<Match>()
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                l.add(Match(o.getString("l"), o.getString("h"), o.getString("a"), o.getString("hb").ifEmpty { null },
                    o.getString("ab").ifEmpty { null }, o.getLong("m"), o.getInt("hs"), o.getInt("as")))
            }
            matches = l
        } catch (x: Exception) { }
    }

    // ===== مباريات =====
    fun status(m: Match, now: Long = System.currentTimeMillis()): Int =
        if (m.hs >= 0) 2 else if (now < m.ms) 0 else if (now < m.ms + 125 * 60000L) 1 else 3

    fun featured(): List<Match> {
        val now = System.currentTimeMillis()
        val fav = TEAMS[gi("team", 0).coerceIn(0, TEAMS.size - 1)].second
        return matches.filter { followed(it.lg) && it.hs < 0 && now < it.ms + 125 * 60000L }
            .sortedWith(compareBy<Match>({ if (status(it, now) == 1) 0 else 1 },
                { if (it.h.lowercase().contains(fav) || it.a.lowercase().contains(fav)) 0 else 1 }, { it.ms }))
            .take(6)
    }

    private val NAMES: List<Pair<String, String>> by lazy {
        """al hilal|الهلال
al nassr|النصر
al ittihad|الاتحاد
al ahli|الأهلي
al shabab|الشباب
al hazem|الحزم
al taawoun|التعاون
al fateh|الفتح
al ettifaq|الاتفاق
al qadsiah|القادسية
al qadisiyah|القادسية
al fayha|الفيحاء
al okhdood|الأخدود
al khaleej|الخليج
al riyadh|الرياض
al kholood|الخلود
al najma|النجمة
al orobah|العروبة
damac|ضمك
neom|نيوم
manchester city|مانشستر سيتي
man city|مانشستر سيتي
manchester united|مانشستر يونايتد
man united|مانشستر يونايتد
liverpool|ليفربول
arsenal|أرسنال
chelsea|تشيلسي
tottenham|توتنهام
newcastle|نيوكاسل
aston villa|أستون فيلا
brighton|برايتون
west ham|وست هام
everton|إيفرتون
fulham|فولهام
brentford|برينتفورد
crystal palace|كريستال بالاس
bournemouth|بورنموث
nottingham|نوتنغهام فورست
wolverhampton|وولفرهامبتون
wolves|وولفرهامبتون
leeds|ليدز
sunderland|سندرلاند
burnley|بيرنلي
ipswich|إيبسويتش
coventry|كوفنتري
hull|هال سيتي
real madrid|ريال مدريد
barcelona|برشلونة
atletico|أتلتيكو مدريد
sevilla|إشبيلية
real sociedad|ريال سوسيداد
villarreal|فياريال
betis|ريال بيتيس
valencia|فالنسيا
athletic|أتلتيك بلباو
girona|جيرونا
bayern|بايرن ميونخ
dortmund|دورتموند
leverkusen|ليفركوزن
leipzig|لايبزيغ
stuttgart|شتوتغارت
frankfurt|آينتراخت فرانكفورت
inter milan|إنتر ميلان
internazionale|إنتر ميلان
milan|ميلان
juventus|يوفنتوس
napoli|نابولي
roma|روما
lazio|لاتسيو
atalanta|أتالانتا
paris|باريس سان جيرمان
psg|باريس سان جيرمان
marseille|مارسيليا
lyon|ليون
monaco|موناكو
lille|ليل
porto|بورتو
benfica|بنفيكا
sporting|سبورتينغ
ajax|أياكس
psv|آيندهوفن
feyenoord|فينورد
galatasaray|غلطة سراي
fenerbahce|فنربخشة
besiktas|بشكتاش
trabzonspor|طرابزون سبور
flamengo|فلامنغو
palmeiras|بالميراس
corinthians|كورينثيانز
sao paulo|ساو باولو
santos|سانتوس
fluminense|فلوميننسي
gremio|غريميو
botafogo|بوتافوغو
river plate|ريفر بليت
boca juniors|بوكا جونيورز
brugge|كلوب بروج
shakhtar|شاختار
slavia|سلافيا براغ
celtic|سيلتيك
rangers|رينجرز
como|كومو
viking|فايكنغ
lask|لاسك
glimt|بودو غليمت
aek|آيك أثينا
slovan|سلوفان براتيسلافا
lens|لانس"""
            .lines().map { val a = it.split("|"); a[0] to a[1] }
    }

    fun ar(en: String): String {
        val k = en.lowercase().replace("-", " ").replace(".", " ").replace("  ", " ").trim()
        for ((n, a) in NAMES) if (k.contains(n)) return a
        return en
    }

    fun weatherText(): String {
        if (!p.contains("wx_t")) return ""
        val c = p.getInt("wx_c", 0)
        val d = when {
            c <= 1 -> "صافي"; c == 2 -> "غائم جزئيًا"; c == 3 -> "غائم"; c == 45 || c == 48 -> "ضباب"
            c in 51..67 || c in 80..82 -> "أمطار"; c in 71..77 -> "ثلوج"; c >= 95 -> "عاصفة"; else -> "متغير"
        }
        return ad("${p.getInt("wx_t", 0)}° $d · ${p.getString("wx_city", "")}")
    }

    fun num(k: String): String? {
        if (!p.contains(k)) return null
        val v = p.getFloat(k, 0f)
        return ad(String.format(Locale.US, "%,d", v.toLong())).replace(",", "٬")
    }

    fun chg(k: String): Float = p.getFloat(k, 0f)
}
