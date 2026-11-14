package br.umc.thindesk.web.dto;

import java.util.List;
import java.util.Map;

/** Respostas dos endpoints /api/v1/dashboard/** */
public final class DashboardDtos {
    private DashboardDtos() { }

    public record Indicadores(long total, long abertos, long emAtendimento, long aguardando,
                              long resolvidos, long cancelados, long pendentes, long atrasados,
                              double taxaResolucao,
                              Double tempoMedioPrimeiroAtendimentoMin,
                              Double tempoMedioResolucaoHoras,
                              double slaCumpridoPercentual,
                              Double avaliacaoMedia) { }

    public record Periodo(String dataInicio, String dataFim) { }

    public record Comparativo(Periodo periodoAnterior,
                              Map<String, Object> valoresAnteriores,
                              Map<String, Double> variacaoPercentual) { }

    public record IndicadoresResponse(Map<String, Object> filtros, Indicadores indicadores,
                                      Comparativo comparativo, String geradoEm) { }

    public record PontoSerie(String periodo, String rotulo, long abertos, long resolvidos) { }

    public record SerieResponse(String agrupamento, Map<String, Object> filtros, List<PontoSerie> serie,
                                Map<String, Long> totais) { }

    public record ItemStatus(String status, String rotulo, long quantidade, double percentual) { }

    public record ItemPrioridade(String prioridade, String rotulo, long quantidade, long atrasados) { }

    public record ItemIdade(String faixa, String rotulo, long quantidade) { }

    public record SituacaoResponse(Map<String, Object> filtros, long total, List<ItemStatus> porStatus,
                                   List<ItemPrioridade> pendentesPorPrioridade,
                                   List<ItemIdade> pendentesPorIdade, long atrasados, String geradoEm) { }

    public record ItemTipo(int posicao, String categoriaId, String categoria, long total, long atendidos,
                           long resolvidos, double percentual, Double tempoMedioResolucaoHoras,
                           double slaCumpridoPercentual) { }

    public record RankingTiposResponse(Map<String, Object> filtros, int limite, List<ItemTipo> ranking) { }

    public record ItemTecnico(String tecnicoId, String nome, String nivel, long atendidos, long resolvidos,
                              long pendentes, Double tempoMedioResolucaoHoras, double slaCumpridoPercentual,
                              Double avaliacaoMedia) { }

    public record DesempenhoResponse(Map<String, Object> filtros, List<ItemTecnico> tecnicos) { }
}
