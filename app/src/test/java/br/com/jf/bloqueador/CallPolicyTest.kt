package br.com.jf.bloqueador

import org.junit.Assert.assertEquals
import org.junit.Test

class CallPolicyTest {

    private val contato = "+5548999990001"
    private val desconhecido = "+5548988887777"
    private val agenda: (String) -> Boolean? = { it == contato }
    private val nenhumLiberado: (String) -> Boolean? = { false }

    private fun decide(
        isIncoming: Boolean = true,
        number: String? = desconhecido,
        enabled: Boolean = true,
        blockHidden: Boolean = true,
        hasContactsPermission: Boolean = true,
        isAllowListed: (String) -> Boolean? = nenhumLiberado,
        lookup: (String) -> Boolean? = agenda,
    ) = CallPolicy.decide(
        isIncoming, number, enabled, blockHidden, hasContactsPermission, isAllowListed, lookup
    )

    @Test fun `desconhecido e bloqueado`() =
        assertEquals(Decision.BLOCK, decide())

    @Test fun `contato passa`() =
        assertEquals(Decision.ALLOW, decide(number = contato))

    @Test fun `numero com espacos e normalizado antes da consulta`() =
        assertEquals(Decision.ALLOW, decide(number = "  $contato  "))

    @Test fun `ligacao feita nunca e bloqueada`() =
        assertEquals(Decision.ALLOW, decide(isIncoming = false))

    @Test fun `bloqueio desligado deixa tudo passar`() =
        assertEquals(Decision.ALLOW, decide(enabled = false))

    @Test fun `oculto bloqueado quando opcao ligada`() {
        assertEquals(Decision.BLOCK, decide(number = null))
        assertEquals(Decision.BLOCK, decide(number = ""))
        assertEquals(Decision.BLOCK, decide(number = "   "))
    }

    @Test fun `oculto passa quando opcao desligada`() =
        assertEquals(Decision.ALLOW, decide(number = null, blockHidden = false))

    @Test fun `oculto passa com bloqueio desligado mesmo com opcao ligada`() =
        assertEquals(Decision.ALLOW, decide(number = null, enabled = false))

    @Test fun `sem permissao de contatos deixa passar`() =
        assertEquals(Decision.ALLOW, decide(hasContactsPermission = false))

    @Test fun `sem permissao nem consulta a agenda`() {
        var consultou = false
        decide(hasContactsPermission = false, lookup = { consultou = true; false })
        assertEquals(false, consultou)
    }

    @Test fun `consulta indisponivel deixa passar`() =
        assertEquals(Decision.ALLOW, decide(lookup = { null }))

    @Test fun `excecao na consulta deixa passar`() =
        assertEquals(Decision.ALLOW, decide(lookup = { throw SecurityException("boom") }))

    @Test fun `ligacao feita nao consulta a agenda`() {
        var consultou = false
        decide(isIncoming = false, lookup = { consultou = true; false })
        assertEquals(false, consultou)
    }

    // ---- Lista de liberados (v2) ----

    @Test fun `liberado passa mesmo fora da agenda`() =
        assertEquals(Decision.ALLOW, decide(isAllowListed = { it == desconhecido }))

    @Test fun `liberado passa mesmo sem permissao de contatos`() =
        assertEquals(
            Decision.ALLOW,
            decide(hasContactsPermission = false, isAllowListed = { true })
        )

    @Test fun `liberado nao precisa consultar a agenda`() {
        var consultou = false
        decide(isAllowListed = { true }, lookup = { consultou = true; false })
        assertEquals(false, consultou)
    }

    @Test fun `erro na lista de liberados cai na regra da agenda`() {
        assertEquals(Decision.BLOCK, decide(isAllowListed = { throw IllegalStateException() }))
        assertEquals(
            Decision.ALLOW,
            decide(number = contato, isAllowListed = { throw IllegalStateException() })
        )
    }

    @Test fun `lista de liberados indisponivel cai na regra da agenda`() =
        assertEquals(Decision.BLOCK, decide(isAllowListed = { null }))

    @Test fun `oculto nao consulta lista de liberados`() {
        var consultou = false
        decide(number = null, isAllowListed = { consultou = true; true })
        assertEquals(false, consultou)
    }
}
