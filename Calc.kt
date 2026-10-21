package sa.tv.launcher

import java.time.LocalDate
import java.time.chrono.HijrahDate
import java.time.temporal.ChronoField
import java.util.Calendar

class PState(val k: String, val iqama: Boolean, val secs: Int, val adhan: Calendar, val iq: Calendar, val frac: Float)

object Calc {
    val DAYS = listOf("الأحد", "الإثنين", "الثلاثاء", "الأربعاء", "الخميس", "الجمعة", "السبت")
    val MONTHS = listOf("يناير", "فبراير", "مارس", "أبريل", "مايو", "يونيو", "يوليو", "أغسطس", "سبتمبر", "أكتوبر", "نوفمبر", "ديسمبر")
    val HM = listOf("محرم", "صفر", "ربيع الأول", "ربيع الآخر", "جمادى الأولى", "جمادى الآخرة", "رجب", "شعبان", "رمضان", "شوال", "ذو القعدة", "ذو الحجة")

    fun t12(c: Calendar): String {
        val x = c.get(Calendar.HOUR_OF_DAY)
        return ad("${if (x % 12 == 0) 12 else x % 12}:${"%02d".format(c.get(Calendar.MINUTE))} ${if (x >= 12) "م" else "ص"}")
    }

    fun t12(ms: Long): String = t12(Calendar.getInstance().apply { timeInMillis = ms })

    fun hms(s: Int): String {
        val h = s / 3600; val m = s % 3600 / 60; val x = s % 60
        return ad(if (h > 0) "%02d:%02d:%02d".format(h, m, x) else "%02d:%02d".format(m, x))
    }

    fun day(ms: Long): String {
        val a = Calendar.getInstance(); a.set(Calendar.HOUR_OF_DAY, 0); a.set(Calendar.MINUTE, 0); a.set(Calendar.SECOND, 0); a.set(Calendar.MILLISECOND, 0)
        val b = Calendar.getInstance().apply { timeInMillis = ms; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
        val d = Math.round((b.timeInMillis - a.timeInMillis) / 86400000.0).toInt()
        if (d == 0) return "اليوم"
        if (d == 1) return "بكرة"
        if (d == -1) return "أمس"
        val c = Calendar.getInstance().apply { timeInMillis = ms }
        return ad("${DAYS[c.get(Calendar.DAY_OF_WEEK) - 1]} ${c.get(Calendar.DAY_OF_MONTH)} ${MONTHS[c.get(Calendar.MONTH)]}")
    }

    fun rel(ms: Long): String {
        val m = Math.round(ms / 60000.0).toInt()
        if (m < 1) return "الآن"
        if (m < 60) return ad("بعد $m دقيقة")
        val h = m / 60
        if (h < 24) return ad("بعد $h ساعات" + (if (m % 60 > 0) " و${m % 60} دقيقة" else ""))
        return ad("بعد ${h / 24} أيام")
    }

    fun weekday(): String = DAYS[Calendar.getInstance().get(Calendar.DAY_OF_WEEK) - 1]

    fun dateLine(): String {
        val c = Calendar.getInstance()
        val hd = HijrahDate.from(LocalDate.now())
        return ad("${c.get(Calendar.DAY_OF_MONTH)} ${MONTHS[c.get(Calendar.MONTH)]} ${c.get(Calendar.YEAR)}  ·  ${hd.get(ChronoField.DAY_OF_MONTH)} ${HM[hd.get(ChronoField.MONTH_OF_YEAR) - 1]} ${hd.get(ChronoField.YEAR)} هـ")
    }

    private var rdDate: LocalDate? = null
    private var rdDays = 0
    private var rdText = ""
    fun ramadan(): Pair<Int, String> {
        val ld = LocalDate.now()
        if (ld != rdDate) {
            rdDate = ld
            for (i in 1..400) {
                val x = ld.plusDays(i.toLong()); val y = HijrahDate.from(x)
                if (y.get(ChronoField.MONTH_OF_YEAR) == 9 && y.get(ChronoField.DAY_OF_MONTH) == 1) {
                    rdDays = i
                    rdText = ad("يبدأ ${DAYS[x.dayOfWeek.value % 7]} ${x.dayOfMonth} ${MONTHS[x.monthValue - 1]} تقريبًا")
                    break
                }
            }
        }
        return Pair(rdDays, rdText)
    }

    private fun prayer(k: String): Calendar {
        val s = Repo.prayerStr(k).split(":")
        return Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, s[0].toInt()); set(Calendar.MINUTE, s[1].toInt()); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
    }

    fun state(): PState {
        val now = System.currentTimeMillis()
        var prev: Calendar? = null
        for (k in Repo.KEYS) {
            val a = prayer(k)
            val q = Calendar.getInstance().apply { timeInMillis = a.timeInMillis + Repo.iq(k) * 60000L }
            if (now >= a.timeInMillis && now < q.timeInMillis)
                return PState(k, true, ((q.timeInMillis - now) / 1000).toInt(), a, q, (now - a.timeInMillis).toFloat() / (q.timeInMillis - a.timeInMillis))
            if (a.timeInMillis <= now) prev = a
        }
        var pk = ""
        var w = Calendar.getInstance()
        for (k in Repo.KEYS) { val a = prayer(k); if (a.timeInMillis > now) { pk = k; w = a; break } }
        if (pk == "") { pk = "Fajr"; w = prayer("Fajr"); w.add(Calendar.DAY_OF_MONTH, 1); prev = prayer("Isha") }
        val pv = prev ?: prayer("Isha").apply { add(Calendar.DAY_OF_MONTH, -1) }
        val q = Calendar.getInstance().apply { timeInMillis = w.timeInMillis + Repo.iq(pk) * 60000L }
        val frac = 1f - (w.timeInMillis - now).toFloat() / (w.timeInMillis - pv.timeInMillis)
        return PState(pk, false, ((w.timeInMillis - now) / 1000).toInt(), w, q, frac)
    }

    fun icon(k: String): String = when (k) { "Fajr" -> "🌅"; "Dhuhr" -> "☀️"; "Asr" -> "🌤"; "Maghrib" -> "🌇"; else -> "🌙" }
}
