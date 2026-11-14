package br.umc.thindesk.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import br.umc.thindesk.service.ChamadoService;
import br.umc.thindesk.service.DashboardService;

/** Testes de integração da camada HTTP (rotas, JSON, códigos de status e formato de erro). */
@WebMvcTest(controllers = {DashboardController.class, ChamadoController.class, CatalogoController.class})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD) // cada teste parte da massa original
@Import({ApiTestConfig.class, DashboardService.class, ChamadoService.class, ApiExceptionHandler.class})
class ApiControllerTest {

    private static final String SEMANA = "dataInicio=2026-11-10&dataFim=2026-11-14";

    @Autowired
    private MockMvc mvc;

    @Test
    void health() throws Exception {
        mvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.totalChamados").value(7));
    }

    @Test
    void filtrosDisponiveis() throws Exception {
        mvc.perform(get("/api/v1/filtros"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status.length()").value(5))
                .andExpect(jsonPath("$.categorias.length()").value(4))
                .andExpect(jsonPath("$.tecnicos[0].rotulo").value("Ana"));
    }

    @Test
    void indicadores() throws Exception {
        mvc.perform(get("/api/v1/dashboard/indicadores?" + SEMANA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.indicadores.total").value(6))
                .andExpect(jsonPath("$.indicadores.taxaResolucao").value(40.0))
                .andExpect(jsonPath("$.comparativo.variacaoPercentual.total").value(500.0))
                .andExpect(jsonPath("$.filtros.dataInicio").value("2026-11-10"));
    }

    @Test
    void indicadoresComDataInvalidaRetorna400() throws Exception {
        mvc.perform(get("/api/v1/dashboard/indicadores?dataInicio=2026-13-40"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro.codigo").value("VALIDACAO"))
                .andExpect(jsonPath("$.erro.detalhes[0].campo").value("dataInicio"));
    }

    @Test
    void seriePorPeriodo() throws Exception {
        mvc.perform(get("/api/v1/dashboard/chamados-por-periodo?agrupamento=dia&" + SEMANA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.serie.length()").value(5))
                .andExpect(jsonPath("$.serie[4].abertos").value(2));
        mvc.perform(get("/api/v1/dashboard/chamados-por-periodo?agrupamento=ano"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro.detalhes[0].campo").value("agrupamento"));
    }

    @Test
    void situacaoERanking() throws Exception {
        mvc.perform(get("/api/v1/dashboard/situacao?" + SEMANA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.atrasados").value(2))
                .andExpect(jsonPath("$.porStatus[3].status").value("resolvido"));
        mvc.perform(get("/api/v1/dashboard/tipos-mais-atendidos?limite=1&" + SEMANA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ranking.length()").value(1))
                .andExpect(jsonPath("$.ranking[0].categoria").value("Hardware"));
        mvc.perform(get("/api/v1/dashboard/tipos-mais-atendidos?limite=0"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listarEDetalharChamados() throws Exception {
        mvc.perform(get("/api/v1/chamados?status=aberto,em_atendimento&porPagina=10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paginacao.total").value(2))
                .andExpect(jsonPath("$.dados[0].statusRotulo").exists());
        mvc.perform(get("/api/v1/chamados/ch-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resumo.numero").value(1));
        mvc.perform(get("/api/v1/chamados/nao-existe"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.erro.codigo").value("NAO_ENCONTRADO"));
    }

    @Test
    void criarChamado() throws Exception {
        String corpo = """
                {"titulo":"Mouse quebrado","descricao":"Botão esquerdo falha","solicitante":"Carlos",
                 "setor":"RH","categoriaId":"cat-hw","prioridade":"baixa"}""";
        mvc.perform(post("/api/v1/chamados").contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.resumo.status").value("aberto"));
    }

    @Test
    void criarComJsonMalformadoOuCamposInvalidos() throws Exception {
        mvc.perform(post("/api/v1/chamados").contentType(MediaType.APPLICATION_JSON).content("{titulo:"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro.codigo").value("JSON_INVALIDO"));
        mvc.perform(post("/api/v1/chamados").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro.detalhes.length()").value(6));
    }

    @Test
    void alterarStatus() throws Exception {
        mvc.perform(patch("/api/v1/chamados/ch-4/status").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"resolvido\",\"observacao\":\"ok\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.erro.codigo").value("TRANSICAO_INVALIDA"));
        mvc.perform(patch("/api/v1/chamados/ch-3/status").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"resolvido\",\"observacao\":\"Switch trocado\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resumo.status").value("resolvido"));
    }

    @Test
    void adicionarConsideracao() throws Exception {
        mvc.perform(post("/api/v1/chamados/ch-5/consideracoes").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tipo\":\"observacao\",\"texto\":\"Usuário de férias\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.consideracoes[0].tipo").value("observacao"));
    }

    @Test
    void metodoNaoPermitido() throws Exception {
        mvc.perform(delete("/api/v1/chamados/ch-1"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.erro.codigo").value("METODO_NAO_PERMITIDO"));
    }
}
