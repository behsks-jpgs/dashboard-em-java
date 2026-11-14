package br.umc.thindesk.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import br.umc.thindesk.domain.Chamado;
import br.umc.thindesk.domain.Status;
import br.umc.thindesk.seed.GeradorDados;
import br.umc.thindesk.support.Fixtures;

class GeradorDadosTest {

    @Test
    void geraDadosReprodutiveisEConsistentes() {
        var a = new GeradorDados(42).gerar(Fixtures.AGORA, 365);
        var b = new GeradorDados(42).gerar(Fixtures.AGORA, 365);
        assertEquals(a.chamados().size(), b.chamados().size());
        assertTrue(a.chamados().size() > 500, "deve gerar volume relevante");
        assertEquals(8, a.categorias().size());
        for (Chamado c : a.chamados()) {
            assertTrue(!c.getDataAbertura().isAfter(Fixtures.AGORA), "abertura no futuro");
            if (c.getStatus() == Status.RESOLVIDO) {
                assertTrue(c.getDataResolucao() != null && !c.getDataResolucao().isAfter(Fixtures.AGORA));
                assertTrue(c.getTecnicoId() != null);
            }
            if (c.getStatus() == Status.ABERTO) assertTrue(c.getTecnicoId() == null);
        }
    }
}
