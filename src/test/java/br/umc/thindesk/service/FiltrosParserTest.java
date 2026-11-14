package br.umc.thindesk.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import br.umc.thindesk.domain.Prioridade;
import br.umc.thindesk.domain.Status;
import br.umc.thindesk.exception.ValidacaoException;

class FiltrosParserTest {

    @Test
    void semParametrosRetornaFiltroVazio() {
        Filtros f = FiltrosParser.parse(Map.of());
        assertNull(f.dataInicio());
        assertTrue(f.status().isEmpty());
    }

    @Test
    void converteTodosOsFiltros() {
        Filtros f = FiltrosParser.parse(Map.of(
                "dataInicio", "2026-11-01", "dataFim", "2026-11-30",
                "status", "aberto, em_atendimento", "prioridade", "critica",
                "categoriaId", "cat-hw,cat-rede", "tecnicoId", "tec-ana"));
        assertEquals(LocalDate.of(2026, 11, 1), f.dataInicio());
        assertEquals(Set.of(Status.ABERTO, Status.EM_ATENDIMENTO), f.status());
        assertEquals(Set.of(Prioridade.CRITICA), f.prioridades());
        assertEquals(Set.of("cat-hw", "cat-rede"), f.categoriaIds());
        assertEquals(Set.of("tec-ana"), f.tecnicoIds());
    }

    @Test
    void acumulaTodosOsErrosEmUmaUnicaExcecao() {
        ValidacaoException e = assertThrows(ValidacaoException.class, () -> FiltrosParser.parse(Map.of(
                "dataInicio", "31/12/2026", "status", "pendente", "prioridade", "urgente")));
        assertEquals(3, e.getDetalhes().size());
        assertEquals("dataInicio", e.getDetalhes().get(0).campo());
        assertEquals(400, e.getStatus());
    }

    @Test
    void rejeitaPeriodoInvertido() {
        ValidacaoException e = assertThrows(ValidacaoException.class,
                () -> FiltrosParser.parse(Map.of("dataInicio", "2026-11-30", "dataFim", "2026-11-01")));
        assertTrue(e.getDetalhes().get(0).problema().contains("posterior"));
    }

    @Test
    void periodoAnteriorTemOMesmoTamanho() {
        Filtros f = FiltrosParser.parse(Map.of("dataInicio", "2026-11-10", "dataFim", "2026-11-14"));
        Filtros ant = f.periodoAnterior();
        assertEquals(LocalDate.of(2026, 11, 5), ant.dataInicio());
        assertEquals(LocalDate.of(2026, 11, 9), ant.dataFim());
    }

    @Test
    void validaInteiros() {
        assertEquals(10, FiltrosParser.inteiro(Map.of(), "limite", 10, 1, 50));
        assertEquals(5, FiltrosParser.inteiro(Map.of("limite", "5"), "limite", 10, 1, 50));
        assertThrows(ValidacaoException.class, () -> FiltrosParser.inteiro(Map.of("limite", "0"), "limite", 10, 1, 50));
        assertThrows(ValidacaoException.class, () -> FiltrosParser.inteiro(Map.of("limite", "abc"), "limite", 10, 1, 50));
    }
}
