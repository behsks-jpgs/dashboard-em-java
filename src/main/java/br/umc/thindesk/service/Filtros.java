package br.umc.thindesk.service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import br.umc.thindesk.domain.Chamado;
import br.umc.thindesk.domain.Prioridade;
import br.umc.thindesk.domain.Status;

/**
 * Filtros de consulta (RF05), comuns ao dashboard e à listagem de chamados.
 * O período (dataInicio/dataFim) é aplicado sobre a data de abertura do chamado.
 */
public record Filtros(LocalDate dataInicio,
                      LocalDate dataFim,
                      Set<Status> status,
                      Set<Prioridade> prioridades,
                      Set<String> categoriaIds,
                      Set<String> tecnicoIds) {

    public static Filtros vazio() {
        return new Filtros(null, null, Set.of(), Set.of(), Set.of(), Set.of());
    }

    /** Período imediatamente anterior e de mesmo tamanho (usado no comparativo dos indicadores). */
    public Filtros periodoAnterior() {
        if (dataInicio == null || dataFim == null) return null;
        long dias = ChronoUnit.DAYS.between(dataInicio, dataFim) + 1;
        return new Filtros(dataInicio.minusDays(dias), dataInicio.minusDays(1),
                status, prioridades, categoriaIds, tecnicoIds);
    }

    /** Aplica somente os filtros de dimensão (sem o período). */
    public boolean aceitaDimensoes(Chamado c) {
        return (status.isEmpty() || status.contains(c.getStatus()))
                && (prioridades.isEmpty() || prioridades.contains(c.getPrioridade()))
                && (categoriaIds.isEmpty() || (c.getCategoriaId() != null && categoriaIds.contains(c.getCategoriaId())))
                && (tecnicoIds.isEmpty() || (c.getTecnicoId() != null && tecnicoIds.contains(c.getTecnicoId())));
    }

    /** Representação devolvida pela API (eco dos filtros aplicados). */
    public Map<String, Object> descricao() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("dataInicio", dataInicio == null ? null : dataInicio.toString());
        m.put("dataFim", dataFim == null ? null : dataFim.toString());
        m.put("status", status.stream().map(Status::codigo).sorted().toList());
        m.put("prioridade", prioridades.stream().map(Prioridade::codigo).sorted().toList());
        m.put("categoriaId", categoriaIds.stream().sorted().toList());
        m.put("tecnicoId", tecnicoIds.stream().sorted().toList());
        return m;
    }
}
