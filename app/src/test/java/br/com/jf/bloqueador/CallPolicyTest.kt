package br.com.jf.bloqueador

import org.junit.Assert.assertEquals
import org.junit.Test

class CallPolicyTest {

    private val contato = "+5548999990001"
    private val desconhecido = "+5548988887777"
    private val agenda: (String) -> Boolean? = { it == contato }

    private fun decide(
        isIncoming: Boolean = true,
        number: String? = desconhecido,
        enabled: Boolean = true,
        blockHidden: Boolean = true,
        hasContactsPermission: Boolean = true,
        lookup: (String) -> Boolean? = agenda,
    ) = CallPolicy.decide(isIncoming, number, enabled, blockHidden, hasContactsPermission, lookup)

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
}
