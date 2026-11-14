package br.umc.thindesk.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import br.umc.thindesk.exception.ValidacaoException;
import br.umc.thindesk.support.Fixtures;
import br.umc.thindesk.web.dto.DashboardDtos.DesempenhoResponse;
import br.umc.thindesk.web.dto.DashboardDtos.Indicadores;
import br.umc.thindesk.web.dto.DashboardDtos.IndicadoresResponse;
import br.umc.thindesk.web.dto.DashboardDtos.RankingTiposResponse;
import br.umc.thindesk.web.dto.DashboardDtos.SerieResponse;
import br.umc.thindesk.web.dto.DashboardDtos.SituacaoResponse;

/** Valores esperados calculados à mão a partir da tabela em {@link Fixtures}. */
class DashboardServiceTest {

    private DashboardService service;
    private final Filtros semana = FiltrosParser.parse(Map.of("dataInicio", "2026-11-10", "dataFim", "2026-11-14"));

    @BeforeEach
    void setUp() {
        service = new DashboardService(Fixtures.gatewayChamados(), Fixtures.gatewayCatalogo(), Fixtures.clock());
    }

    @Test
    void rf01_indicadoresGeraisDoPeriodo() {
        Indicadores i = service.indicadores(semana).indicadores();
        assertEquals(6, i.total());
        assertEquals(1, i.abertos());
        assertEquals(1, i.emAtendimento());
        assertEquals(1, i.aguardando());
        assertEquals(2, i.resolvidos());
        assertEquals(1, i.cancelados());
        assertEquals(3, i.pendentes());
        assertEquals(2, i.atrasados());                       // #3 e #5
        assertEquals(40.0, i.taxaResolucao());                // 2 / (6 - 1 cancelado)
        assertEquals(30.0, i.tempoMedioPrimeiroAtendimentoMin()); // (30+60+10+20)/4
        assertEquals(18.0, i.tempoMedioResolucaoHoras());      // (6+30)/2
        assertEquals(50.0, i.slaCumpridoPercentual());        // 1 de 2 no prazo
        assertEquals(4.0, i.avaliacaoMedia());                // (5+3)/2
    }

    @Test
    void rf01_comparativoComPeriodoAnterior() {
        IndicadoresResponse r = service.indicadores(semana);
        assertNotNull(r.comparativo());
        assertEquals("2026-11-05", r.comparativo().periodoAnterior().dataInicio());
        assertEquals(500.0, r.comparativo().variacaoPercentual().get("total"));      // 1 -> 6
        assertEquals(100.0, r.comparativo().variacaoPercentual().get("resolvidos")); // 1 -> 2
    }

    @Test
    void rf01_periodoAnteriorVazioNaoGeraVariacao() {
        Filtros f = FiltrosParser.parse(Map.of("dataInicio", "2026-11-05", "dataFim", "2026-11-14"));
        IndicadoresResponse r = service.indicadores(f); // anterior = 26/10 a 04/11, sem chamados
        assertNull(r.comparativo().valoresAnteriores().get("total"));
        assertNull(r.comparativo().variacaoPercentual().get("taxaResolucao"));
    }

    @Test
    void rf01_semPeriodoNaoHaComparativo() {
        IndicadoresResponse r = service.indicadores(Filtros.vazio());
        assertEquals(7, r.indicadores().total());
        assertNull(r.comparativo());
    }

    @Test
    void rf02_seriePorDiaPreencheDiasVaziosEContaResolucaoPelaDataDeResolucao() {
        SerieResponse s = service.chamadosPorPeriodo(semana, "dia");
        assertEquals(5, s.serie().size());
        assertEquals("2026-11-10", s.serie().get(0).periodo());
        assertEquals(1, s.serie().get(0).abertos());
        assertEquals(1, s.serie().get(0).resolvidos());  // #1 resolvido em 10/11
        assertEquals(1, s.serie().get(2).resolvidos());  // #2 resolvido em 12/11
        assertEquals(2, s.serie().get(4).abertos());     // #3 e #4 em 14/11
        assertEquals(6L, s.totais().get("abertos"));
        assertEquals(2L, s.totais().get("resolvidos"));
    }

    @Test
    void rf02_agrupamentoMensalESemanal() {
        SerieResponse mes = service.chamadosPorPeriodo(Filtros.vazio(), "mes");
        assertEquals(1, mes.serie().size());
        assertEquals("nov/26", mes.serie().get(0).rotulo());
        assertEquals(7, mes.serie().get(0).abertos());
        LocalDate segunda = DashboardService.inicioDoBalde(LocalDate.of(2026, 11, 14), "semana");
        assertEquals(DayOfWeek.MONDAY, segunda.getDayOfWeek());
    }

    @Test
    void rf02_agrupamentoInvalido() {
        assertThrows(ValidacaoException.class, () -> service.chamadosPorPeriodo(semana, "ano"));
    }

    @Test
    void rf03_situacaoPorStatusPrioridadeEIdade() {
        SituacaoResponse s = service.situacao(semana);
        assertEquals(6, s.total());
        assertEquals(5, s.porStatus().size());           // todos os status, inclusive zerados
        assertEquals("resolvido", s.porStatus().get(3).status());
        assertEquals(2, s.porStatus().get(3).quantidade());
        assertEquals(33.3, s.porStatus().get(3).percentual());
        assertEquals(2, s.atrasados());
        assertEquals(1, s.pendentesPorPrioridade().get(3).quantidade()); // crítica
        assertEquals(1, s.pendentesPorPrioridade().get(3).atrasados());
        assertEquals(2, s.pendentesPorIdade().get(0).quantidade());      // até 24h: #3 e #4
        assertEquals(1, s.pendentesPorIdade().get(1).quantidade());      // 1-3 dias: #5
    }

    @Test
    void rf04_rankingDosTiposMaisAtendidos() {
        RankingTiposResponse r = service.tiposMaisAtendidos(semana, 10);
        assertEquals(3, r.ranking().size());
        assertEquals("Hardware", r.ranking().get(0).categoria());
        assertEquals(2, r.ranking().get(0).atendidos());
        assertEquals(50.0, r.ranking().get(0).percentual());
        assertEquals("Rede", r.ranking().get(1).categoria()); // empate desfeito por nome
        assertEquals(2, service.tiposMaisAtendidos(semana, 2).ranking().size());
    }

    @Test
    void rf05_filtrosDeDimensao() {
        Filtros soAna = FiltrosParser.parse(Map.of("tecnicoId", "tec-ana"));
        assertEquals(3, service.indicadores(soAna).indicadores().total());
        Filtros criticos = FiltrosParser.parse(Map.of("prioridade", "critica"));
        assertEquals(1, service.indicadores(criticos).indicadores().total());
    }

    @Test
    void desempenhoPorTecnico() {
        DesempenhoResponse d = service.desempenhoTecnicos(semana);
        assertEquals(2, d.tecnicos().size());
        assertEquals("Ana", d.tecnicos().get(0).nome());
        assertEquals("N1", d.tecnicos().get(0).nivel());
        assertEquals(2, d.tecnicos().get(0).atendidos());
        assertEquals(100.0, d.tecnicos().get(0).slaCumpridoPercentual());
        assertEquals(0.0, d.tecnicos().get(1).slaCumpridoPercentual());
    }

    @Test
    void periodoSemDadosDevolveZeros() {
        Filtros vazio = FiltrosParser.parse(Map.of("dataInicio", "2020-01-01", "dataFim", "2020-01-31"));
        Indicadores i = service.indicadores(vazio).indicadores();
        assertEquals(0, i.total());
        assertEquals(0.0, i.taxaResolucao());
        assertNull(i.tempoMedioResolucaoHoras());
        assertEquals(31, service.chamadosPorPeriodo(vazio, "dia").serie().size());
    }
}
