package br.com.jf.bloqueador

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Um bloqueio registrado. number nulo/vazio = número oculto. */
data class BlockEvent(val ts: Long, val number: String?)

/** Um número dentro de um dia: quantas vezes ligou e a hora da última. */
data class NumberEntry(val number: String?, val count: Int, val lastTs: Long) {
    val isHidden: Boolean get() = number.isNullOrBlank()
}

/** Um dia da sanfona. */
data class DayGroup(val day: LocalDate, val total: Int, val entries: List<NumberEntry>)

/** Agrupamento e rótulos do histórico, sem dependência do Android. */
object HistoryGrouping {

    private val WEEKDAYS = arrayOf("Seg", "Ter", "Qua", "Qui", "Sex", "Sáb", "Dom")

    /**
     * Agrupa por dia (no fuso informado) e, dentro do dia, por número.
     * Dias e números ficam do mais recente para o mais antigo.
     * @param sameNumber critério para considerar dois números iguais
     *                   (no app: comparação de telefones do Android)
     */
    fun group(
        events: List<BlockEvent>,
        zone: ZoneId,
        sameNumber: (String, String) -> Boolean = { a, b -> a == b },
    ): List<DayGroup> {
        val byDay = events.groupBy { Instant.ofEpochMilli(it.ts).atZone(zone).toLocalDate() }
        return byDay.entries
            .sortedByDescending { it.key }
            .map { (day, dayEvents) ->
                val entries = mutableListOf<NumberEntry>()
                for (e in dayEvents) {
                    val n = e.number?.trim()?.takeIf { it.isNotEmpty() }
                    val idx = entries.indexOfFirst { existing ->
                        if (n == null) existing.isHidden
                        else !existing.isHidden && sameNumber(existing.number!!, n)
                    }
                    if (idx < 0) {
                        entries += NumberEntry(n, 1, e.ts)
                    } else {
                        val old = entries[idx]
                        entries[idx] = old.copy(count = old.count + 1, lastTs = maxOf(old.lastTs, e.ts))
                    }
                }
                DayGroup(day, dayEvents.size, entries.sortedByDescending { it.lastTs })
            }
    }

    /** "Hoje", "Ontem", "Sáb, 22/11" ou, em outro ano, "Sáb, 22/11/2025". */
    fun dayLabel(day: LocalDate, today: LocalDate): String {
        if (day == today) return "Hoje"
        if (day == today.minusDays(1)) return "Ontem"
        val wd = WEEKDAYS[day.dayOfWeek.value - 1]
        val dm = "%02d/%02d".format(day.dayOfMonth, day.monthValue)
        return if (day.year == today.year) "$wd, $dm" else "$wd, $dm/${day.year}"
    }
}
