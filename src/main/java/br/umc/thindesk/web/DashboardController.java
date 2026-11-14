package br.umc.thindesk.web;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.umc.thindesk.service.DashboardService;
import br.umc.thindesk.service.FiltrosParser;
import br.umc.thindesk.web.dto.DashboardDtos.DesempenhoResponse;
import br.umc.thindesk.web.dto.DashboardDtos.IndicadoresResponse;
import br.umc.thindesk.web.dto.DashboardDtos.RankingTiposResponse;
import br.umc.thindesk.web.dto.DashboardDtos.SerieResponse;
import br.umc.thindesk.web.dto.DashboardDtos.SituacaoResponse;

/** Endpoints de leitura agregada que alimentam o dashboard. Todos aceitam os filtros do RF05. */
@RestController
@RequestMapping("/api/v1/dashboard")
public class DashboardController {

    private final DashboardService service;

    public DashboardController(DashboardService service) {
        this.service = service;
    }

    /** RF01 */
    @GetMapping("/indicadores")
    public IndicadoresResponse indicadores(@RequestParam Map<String, String> params) {
        return service.indicadores(FiltrosParser.parse(params));
    }

    /** RF02 */
    @GetMapping("/chamados-por-periodo")
    public SerieResponse chamadosPorPeriodo(@RequestParam Map<String, String> params) {
        return service.chamadosPorPeriodo(FiltrosParser.parse(params), params.getOrDefault("agrupamento", "dia"));
    }

    /** RF03 */
    @GetMapping("/situacao")
    public SituacaoResponse situacao(@RequestParam Map<String, String> params) {
        return service.situacao(FiltrosParser.parse(params));
    }

    /** RF04 */
    @GetMapping("/tipos-mais-atendidos")
    public RankingTiposResponse tiposMaisAtendidos(@RequestParam Map<String, String> params) {
        return service.tiposMaisAtendidos(FiltrosParser.parse(params),
                FiltrosParser.inteiro(params, "limite", 10, 1, 50));
    }

    @GetMapping("/desempenho-tecnicos")
    public DesempenhoResponse desempenhoTecnicos(@RequestParam Map<String, String> params) {
        return service.desempenhoTecnicos(FiltrosParser.parse(params));
    }
}
