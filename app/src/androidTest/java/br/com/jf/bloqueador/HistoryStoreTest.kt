package br.com.jf.bloqueador

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

/** Testes do banco real (SQLite + comparação de telefones do Android) no emulador. */
@RunWith(AndroidJUnit4::class)
class HistoryStoreTest {

    private val ctx = InstrumentationRegistry.getInstrumentation().targetContext
    private val dbName = "historico-teste.db"
    private lateinit var store: HistoryStore

    @Before fun setUp() {
        ctx.deleteDatabase(dbName)
        store = HistoryStore(ctx, dbName)
    }

    @After fun tearDown() {
        store.close()
        ctx.deleteDatabase(dbName)
    }

    @Test fun gravaELeBloqueiosDoMaisRecente() {
        val now = System.currentTimeMillis()
        store.recordBlocked(now - 1000, "48911111111", rejected = true)
        store.recordBlocked(now, null, rejected = false)
        val events = store.events()
        assertEquals(2, events.size)
        assertNull(events[0].number)
        assertEquals("48911111111", events[1].number)
    }

    @Test fun numeroVazioViraOculto() {
        store.recordBlocked(System.currentTimeMillis(), "   ", rejected = true)
        assertNull(store.events().single().number)
    }

    @Test fun retencaoApagaMaisAntigosQue90Dias() {
        val now = System.currentTimeMillis()
        val day = TimeUnit.DAYS.toMillis(1)
        store.recordBlocked(now - 91 * day, "48911111111", true)
        store.recordBlocked(now - 89 * day, "48922222222", true)
        store.purge(now)
        assertEquals(listOf("48922222222"), store.events().map { it.number })
    }

    @Test fun gravarNovoBloqueioJaAplicaRetencao() {
        val now = System.currentTimeMillis()
        val old = now - TimeUnit.DAYS.toMillis(100)
        store.writableDatabase.execSQL(
            "INSERT INTO blocked(ts, number, rejected) VALUES (?, ?, 1)",
            arrayOf<Any>(old, "48900000000")
        )
        store.recordBlocked(now, "48911111111", true)
        assertEquals(listOf("48911111111"), store.events().map { it.number })
    }

    @Test fun liberarEReconhecerFormatosDiferentes() {
        store.allow("48988887777")
        assertTrue(store.isAllowed("48988887777"))
        assertTrue(store.isAllowed("+5548988887777"))
        assertTrue(store.isAllowed("(48) 98888-7777"))
        assertFalse(store.isAllowed("48988887778"))
    }

    @Test fun liberarDuasVezesNaoDuplica() {
        store.allow("48988887777")
        store.allow("+55 48 98888-7777")
        assertEquals(1, store.allowed().size)
    }

    @Test fun removerLiberado() {
        store.allow("48988887777")
        store.disallow("+5548988887777")
        assertFalse(store.isAllowed("48988887777"))
        assertTrue(store.allowed().isEmpty())
    }

    @Test fun textoMaliciosoNaoQuebraConsultas() {
        val evil = "1'); DROP TABLE allowed; --"
        store.allow(evil)
        store.recordBlocked(System.currentTimeMillis(), evil, true)
        assertEquals(1, store.allowed().size)
        assertEquals(evil, store.events().single().number)
    }

    @Test fun vazioNaoELiberado() {
        store.allow("  ")
        assertTrue(store.allowed().isEmpty())
        assertFalse(store.isAllowed(""))
    }
}
