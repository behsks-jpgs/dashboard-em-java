package br.umc.thindesk.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import br.umc.thindesk.exception.NaoEncontradoException;
import br.umc.thindesk.exception.RegraNegocioException;
import br.umc.thindesk.exception.ValidacaoException;
import br.umc.thindesk.support.Fixtures;
import br.umc.thindesk.web.dto.ChamadoDtos.AlterarStatusRequest;
import br.umc.thindesk.web.dto.ChamadoDtos.ChamadoDetalhe;
import br.umc.thindesk.web.dto.ChamadoDtos.ChamadoResumo;
import br.umc.thindesk.web.dto.ChamadoDtos.NovaConsideracaoRequest;
import br.umc.thindesk.web.dto.ChamadoDtos.NovoChamadoRequest;
import br.umc.thindesk.web.dto.ChamadoDtos.PaginaResponse;

class ChamadoServiceTest {

    private ChamadoService service;

    @BeforeEach
    void setUp() {
        service = new ChamadoService(Fixtures.gatewayChamados(), Fixtures.gatewayCatalogo(), Fixtures.clock());
    }

    @Test
    void listaComPaginacaoEOrdenacao() {
        PaginaResponse<ChamadoResumo> p = service.listar(Map.of("porPagina", "3", "pagina", "1"));
        assertEquals(7, p.paginacao().total());
        assertEquals(3, p.paginacao().totalPaginas());
        assertEquals(3, p.dados().size());
        assertEquals(4, p.dados().get(0).numero()); // mais recente primeiro
        PaginaResponse<ChamadoResumo> asc = service.listar(Map.of("ordenarPor", "numero", "ordem", "asc"));
        assertEquals(1, asc.dados().get(0).numero());
    }

    @Test
    void listaSomenteAtrasadosEComBusca() {
        PaginaResponse<ChamadoResumo> atrasados = service.listar(Map.of("atrasados", "true"));
        assertEquals(2, atrasados.paginacao().total());
        assertTrue(atrasados.dados().stream().allMatch(ChamadoResumo::atrasado));
        assertEquals(3, service.listar(Map.of("busca", "mariana")).paginacao().total()); // #2, #4, #6
    }

    @Test
    void listaRejeitaParametrosInvalidos() {
        assertThrows(ValidacaoException.class, () -> service.listar(Map.of("ordenarPor", "senha")));
        assertThrows(ValidacaoException.class, () -> service.listar(Map.of("porPagina", "500")));
        assertThrows(ValidacaoException.class, () -> service.listar(Map.of("ordem", "aleatoria")));
    }

    @Test
    void detalheTrazConsideracoesETransicoes() {
        ChamadoDetalhe d = service.detalhar("ch-3");
        assertEquals("em_atendimento", d.resumo().status());
        assertTrue(d.resumo().atrasado());
        assertEquals(java.util.List.of("aguardando", "resolvido", "cancelado"), d.transicoesPermitidas());
        assertThrows(NaoEncontradoException.class, () -> service.detalhar("nao-existe"));
    }

    @Test
    void criaChamadoCalculandoSlaPelaPrioridade() {
        ChamadoDetalhe d = service.criar(new NovoChamadoRequest("Impressora sem toner", "Sem toner no 2º andar",
                "Carlos", "Estoque", "cat-imp", "alta"));
        assertEquals(8, d.resumo().numero());
        assertEquals("aberto", d.resumo().status());
        assertEquals("2026-11-14T12:00:00", d.resumo().dataAbertura());
        assertEquals("2026-11-14T20:00:00", d.resumo().prazoSla()); // +8h
        assertEquals(1, d.historico().size());
    }

    @Test
    void criacaoValidaCamposObrigatorios() {
        ValidacaoException e = assertThrows(ValidacaoException.class,
                () -> service.criar(new NovoChamadoRequest("", "x", "y", "z", "cat-inexistente", "urgente")));
        assertEquals(3, e.getDetalhes().size()); // titulo, prioridade, categoriaId
    }

    @Test
    void naoPermiteTransicaoForaDaMaquinaDeEstados() {
        RegraNegocioException e = assertThrows(RegraNegocioException.class,
                () -> service.alterarStatus("ch-4", new AlterarStatusRequest("resolvido", null, "ok")));
        assertEquals(409, e.getStatus());
        assertEquals("TRANSICAO_INVALIDA", e.getCodigo());
    }

    @Test
    void iniciarAtendimentoExigeTecnicoERegistraPrimeiroAtendimento() {
        assertThrows(ValidacaoException.class,
                () -> service.alterarStatus("ch-4", new AlterarStatusRequest("em_atendimento", null, null)));
        ChamadoDetalhe d = service.alterarStatus("ch-4", new AlterarStatusRequest("em_atendimento", "tec-bruno", null));
        assertEquals("Bruno", d.resumo().tecnico());
        assertEquals("2026-11-14T12:00:00", d.dataPrimeiroAtendimento());
        assertEquals(120.0, d.minutosAtePrimeiroAtendimento());
        assertEquals("em_atendimento", d.historico().get(d.historico().size() - 1).para());
    }

    @Test
    void resolverExigeSolucaoEGeraConsideracaoTecnica() {
        assertThrows(ValidacaoException.class,
                () -> service.alterarStatus("ch-3", new AlterarStatusRequest("resolvido", null, " ")));
        ChamadoDetalhe d = service.alterarStatus("ch-3",
                new AlterarStatusRequest("resolvido", null, "Switch reiniciado"));
        assertEquals("resolvido", d.resumo().status());
        assertFalse(d.resumo().atrasado());
        assertNotNull(d.resumo().dataResolucao());
        assertEquals("solucao", d.consideracoes().get(d.consideracoes().size() - 1).tipo());
    }

    @Test
    void adicionaConsideracaoTecnica() {
        ChamadoDetalhe d = service.adicionarConsideracao("ch-5",
                new NovaConsideracaoRequest("diagnostico", "Cabo de rede rompido", null));
        assertEquals(1, d.consideracoes().size());
        assertEquals("Bruno", d.consideracoes().get(0).tecnico()); // herda o técnico do chamado
        assertThrows(ValidacaoException.class,
                () -> service.adicionarConsideracao("ch-5", new NovaConsideracaoRequest("palpite", "", null)));
        assertThrows(RegraNegocioException.class,
                () -> service.adicionarConsideracao("ch-6", new NovaConsideracaoRequest("observacao", "x", null)));
    }
}
