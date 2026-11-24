package br.com.jf.bloqueador

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class HistoryGroupingTest {

    private val sp: ZoneId = ZoneId.of("America/Sao_Paulo")

    private fun ts(y: Int, m: Int, d: Int, h: Int, min: Int) =
        ZonedDateTime.of(y, m, d, h, min, 0, 0, sp).toInstant().toEpochMilli()

    @Test fun `lista vazia gera nenhum dia`() =
        assertTrue(HistoryGrouping.group(emptyList(), sp).isEmpty())

    @Test fun `agrupa por dia do mais recente para o mais antigo`() {
        val g = HistoryGrouping.group(
            listOf(
                BlockEvent(ts(2026, 11, 22, 10, 0), "48911111111"),
                BlockEvent(ts(2026, 11, 24, 9, 0), "48922222222"),
                BlockEvent(ts(2026, 11, 23, 15, 0), "48933333333"),
            ),
            sp
        )
        assertEquals(
            listOf(LocalDate.of(2026, 11, 24), LocalDate.of(2026, 11, 23), LocalDate.of(2026, 11, 22)),
            g.map { it.day }
        )
    }

    @Test fun `conta total do dia e agrupa repeticoes do mesmo numero`() {
        val g = HistoryGrouping.group(
            listOf(
                BlockEvent(ts(2026, 11, 24, 9, 0), "48911111111"),
                BlockEvent(ts(2026, 11, 24, 14, 32), "48911111111"),
                BlockEvent(ts(2026, 11, 24, 11, 0), "48922222222"),
            ),
            sp
        ).single()
        assertEquals(3, g.total)
        assertEquals(2, g.entries.size)
        val repetido = g.entries.first()
        assertEquals("48911111111", repetido.number)
        assertEquals(2, repetido.count)
        assertEquals(ts(2026, 11, 24, 14, 32), repetido.lastTs)
    }

    @Test fun `numeros ordenados pela ultima ligacao`() {
        val g = HistoryGrouping.group(
            listOf(
                BlockEvent(ts(2026, 11, 24, 8, 0), "48911111111"),
                BlockEvent(ts(2026, 11, 24, 20, 0), "48922222222"),
            ),
            sp
        ).single()
        assertEquals(listOf("48922222222", "48911111111"), g.entries.map { it.number })
    }

    @Test fun `virada da meia-noite no fuso de Sao Paulo`() {
        val g = HistoryGrouping.group(
            listOf(
                BlockEvent(ts(2026, 11, 23, 23, 59), "48911111111"),
                BlockEvent(ts(2026, 11, 24, 0, 0), "48911111111"),
            ),
            sp
        )
        assertEquals(2, g.size)
        assertEquals(LocalDate.of(2026, 11, 24), g[0].day)
        assertEquals(LocalDate.of(2026, 11, 23), g[1].day)
    }

    @Test fun `22h em Sao Paulo continua no mesmo dia mesmo sendo outro dia em UTC`() {
        val g = HistoryGrouping.group(listOf(BlockEvent(ts(2026, 11, 23, 22, 0), "4891")), sp)
        assertEquals(LocalDate.of(2026, 11, 23), g.single().day)
    }

    @Test fun `ocultos agrupados juntos sem numero`() {
        val g = HistoryGrouping.group(
            listOf(
                BlockEvent(ts(2026, 11, 24, 9, 0), null),
                BlockEvent(ts(2026, 11, 24, 10, 0), ""),
                BlockEvent(ts(2026, 11, 24, 11, 0), "  "),
            ),
            sp
        ).single()
        val oculto = g.entries.single()
        assertTrue(oculto.isHidden)
        assertNull(oculto.number)
        assertEquals(3, oculto.count)
    }

    @Test fun `comparador customizado une formatos diferentes do mesmo numero`() {
        val digits = { s: String -> s.filter(Char::isDigit).takeLast(11) }
        val g = HistoryGrouping.group(
            listOf(
                BlockEvent(ts(2026, 11, 24, 9, 0), "+55 48 91111-1111"),
                BlockEvent(ts(2026, 11, 24, 10, 0), "48911111111"),
            ),
            sp,
            sameNumber = { a, b -> digits(a) == digits(b) }
        ).single()
        assertEquals(1, g.entries.size)
        assertEquals(2, g.entries.single().count)
    }

    @Test fun `rotulos de dia`() {
        val hoje = LocalDate.of(2026, 11, 24) // terça
        assertEquals("Hoje", HistoryGrouping.dayLabel(hoje, hoje))
        assertEquals("Ontem", HistoryGrouping.dayLabel(hoje.minusDays(1), hoje))
        assertEquals("Dom, 22/11", HistoryGrouping.dayLabel(LocalDate.of(2026, 11, 22), hoje))
        assertEquals("Sáb, 21/11", HistoryGrouping.dayLabel(LocalDate.of(2026, 11, 21), hoje))
        assertEquals("Seg, 02/03", HistoryGrouping.dayLabel(LocalDate.of(2026, 3, 2), hoje))
        assertEquals("Seg, 01/12/2025", HistoryGrouping.dayLabel(LocalDate.of(2025, 12, 1), hoje))
    }
}
