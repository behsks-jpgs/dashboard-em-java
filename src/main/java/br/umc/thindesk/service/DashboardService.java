package br.umc.thindesk.service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalDouble;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import br.umc.thindesk.domain.Chamado;
import br.umc.thindesk.domain.Prioridade;
import br.umc.thindesk.domain.Status;
import br.umc.thindesk.exception.ValidacaoException;
import br.umc.thindesk.domain.Tecnico;
import br.umc.thindesk.repository.CampoData;
import br.umc.thindesk.repository.CatalogoGateway;
import br.umc.thindesk.repository.ChamadoGateway;
import br.umc.thindesk.web.dto.DashboardDtos.Comparativo;
import br.umc.thindesk.web.dto.DashboardDtos.DesempenhoResponse;
import br.umc.thindesk.web.dto.DashboardDtos.Indicadores;
import br.umc.thindesk.web.dto.DashboardDtos.IndicadoresResponse;
import br.umc.thindesk.web.dto.DashboardDtos.ItemIdade;
import br.umc.thindesk.web.dto.DashboardDtos.ItemPrioridade;
import br.umc.thindesk.web.dto.DashboardDtos.ItemStatus;
import br.umc.thindesk.web.dto.DashboardDtos.ItemTecnico;
import br.umc.thindesk.web.dto.DashboardDtos.ItemTipo;
import br.umc.thindesk.web.dto.DashboardDtos.Periodo;
import br.umc.thindesk.web.dto.DashboardDtos.PontoSerie;
import br.umc.thindesk.web.dto.DashboardDtos.RankingTiposResponse;
import br.umc.thindesk.web.dto.DashboardDtos.SerieResponse;
import br.umc.thindesk.web.dto.DashboardDtos.SituacaoResponse;
import br.umc.thindesk.web.dto.Datas;

/**
 * Regras de cálculo dos indicadores do dashboard (RF01–RF04).
 *
 * O MongoDB faz a filtragem (usando os índices); os cálculos são feitos aqui, em Java,
 * o que deixa as regras de negócio explícitas e testáveis sem banco.
 */
@Service
public class DashboardService {

    public static final List<String> AGRUPAMENTOS = List.of("dia", "semana", "mes");
    private static final String[] MESES = {"jan", "fev", "mar", "abr", "mai", "jun",
                                           "jul", "ago", "set", "out", "nov", "dez"};

    private final ChamadoGateway chamados;
    private final CatalogoGateway catalogo;
    private final Clock clock;

    public DashboardService(ChamadoGateway chamados, CatalogoGateway catalogo, Clock clock) {
        this.chamados = chamados;
        this.catalogo = catalogo;
        this.clock = clock;
    }

    private LocalDateTime agora() {
        return LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
    }

    // ===================== RF01 — Indicadores gerais =====================

    public IndicadoresResponse indicadores(Filtros filtros) {
        LocalDateTime agora = agora();
        Indicadores atual = calcular(chamados.buscar(filtros, CampoData.ABERTURA), agora);
        Comparativo comparativo = null;
        Filtros anterior = filtros.periodoAnterior();
        if (anterior != null) {
            Indicadores calc = calcular(chamados.buscar(anterior, CampoData.ABERTURA), agora);
            // período anterior sem nenhum chamado: não existe base de comparação (evita "+100%" enganoso)
            Indicadores ant = calc.total() == 0 ? null : calc;
            Map<String, Object> valores = new LinkedHashMap<>();
            Map<String, Double> variacao = new LinkedHashMap<>();
            registrar(valores, variacao, "total", atual.total(), (ant == null ? null : ant.total()));
            registrar(valores, variacao, "resolvidos", atual.resolvidos(), (ant == null ? null : ant.resolvidos()));
            registrar(valores, variacao, "pendentes", atual.pendentes(), (ant == null ? null : ant.pendentes()));
            registrar(valores, variacao, "taxaResolucao", atual.taxaResolucao(), (ant == null ? null : ant.taxaResolucao()));
            registrar(valores, variacao, "tempoMedioPrimeiroAtendimentoMin",
                    atual.tempoMedioPrimeiroAtendimentoMin(), (ant == null ? null : ant.tempoMedioPrimeiroAtendimentoMin()));
            registrar(valores, variacao, "tempoMedioResolucaoHoras",
                    atual.tempoMedioResolucaoHoras(), (ant == null ? null : ant.tempoMedioResolucaoHoras()));
            registrar(valores, variacao, "slaCumpridoPercentual",
                    atual.slaCumpridoPercentual(), (ant == null ? null : ant.slaCumpridoPercentual()));
            comparativo = new Comparativo(
                    new Periodo(anterior.dataInicio().toString(), anterior.dataFim().toString()), valores, variacao);
        }
        return new IndicadoresResponse(filtros.descricao(), atual, comparativo, Datas.iso(agora));
    }

    static Indicadores calcular(List<Chamado> lista, LocalDateTime agora) {
        Map<Status, Long> porStatus = lista.stream()
                .collect(Collectors.groupingBy(Chamado::getStatus, Collectors.counting()));
        long total = lista.size();
        long abertos = porStatus.getOrDefault(Status.ABERTO, 0L);
        long emAtendimento = porStatus.getOrDefault(Status.EM_ATENDIMENTO, 0L);
        long aguardando = porStatus.getOrDefault(Status.AGUARDANDO, 0L);
        long resolvidos = porStatus.getOrDefault(Status.RESOLVIDO, 0L);
        long cancelados = porStatus.getOrDefault(Status.CANCELADO, 0L);
        long noPrazo = lista.stream().filter(Chamado::resolvidoNoPrazo).count();
        long atrasados = lista.stream().filter(c -> c.atrasado(agora)).count();

        return new Indicadores(total, abertos, emAtendimento, aguardando, resolvidos, cancelados,
                abertos + emAtendimento + aguardando, atrasados,
                percentual(resolvidos, total - cancelados),
                media(lista, Chamado::minutosAtePrimeiroAtendimento),
                media(lista, Chamado::horasAteResolucao),
                percentual(noPrazo, resolvidos),
                media(lista, c -> c.getAvaliacao() == null ? null : c.getAvaliacao().doubleValue(), 2));
    }

    private static void registrar(Map<String, Object> valores, Map<String, Double> variacao,
                                  String chave, Number atual, Number anterior) {
        valores.put(chave, anterior);
        variacao.put(chave, anterior == null ? null : variacao(atual, anterior));
    }

    // ===================== RF02 — Chamados por período =====================

    public SerieResponse chamadosPorPeriodo(Filtros filtros, String agrupamento) {
        if (!AGRUPAMENTOS.contains(agrupamento)) {
            throw new ValidacaoException("agrupamento", "Use um destes valores: " + String.join(", ", AGRUPAMENTOS) + ".");
        }
        List<LocalDateTime> aberturas = chamados.buscar(filtros, CampoData.ABERTURA).stream()
                .map(Chamado::getDataAbertura).toList();
        // resolvidos: o período vale para a DATA DE RESOLUÇÃO
        List<LocalDateTime> resolucoes = chamados.buscar(filtros, CampoData.RESOLUCAO).stream()
                .filter(c -> c.getStatus() == Status.RESOLVIDO && c.getDataResolucao() != null)
                .map(Chamado::getDataResolucao).toList();

        List<LocalDateTime> todas = new ArrayList<>(aberturas);
        todas.addAll(resolucoes);
        if (todas.isEmpty() && (filtros.dataInicio() == null || filtros.dataFim() == null)) {
            return new SerieResponse(agrupamento, filtros.descricao(), List.of(),
                    Map.of("abertos", 0L, "resolvidos", 0L));
        }
        LocalDate inicio = filtros.dataInicio() != null ? filtros.dataInicio()
                : todas.stream().min(Comparator.naturalOrder()).orElseThrow().toLocalDate();
        LocalDate fim = filtros.dataFim() != null ? filtros.dataFim()
                : todas.stream().max(Comparator.naturalOrder()).orElseThrow().toLocalDate();
        if ("dia".equals(agrupamento) && ChronoUnit.DAYS.between(inicio, fim) > 400) {
            throw new ValidacaoException("agrupamento",
                    "Período maior que 400 dias: use agrupamento 'semana' ou 'mes'.");
        }

        // cria todos os intervalos do período (inclusive vazios) para o gráfico não "pular" datas
        TreeMap<LocalDate, long[]> baldes = new TreeMap<>();
        for (LocalDate d = inicioDoBalde(inicio, agrupamento); !d.isAfter(fim); d = proximo(d, agrupamento)) {
            baldes.put(d, new long[2]);
        }
        aberturas.forEach(dt -> baldes.computeIfAbsent(inicioDoBalde(dt.toLocalDate(), agrupamento), k -> new long[2])[0]++);
        resolucoes.forEach(dt -> baldes.computeIfAbsent(inicioDoBalde(dt.toLocalDate(), agrupamento), k -> new long[2])[1]++);

        List<PontoSerie> serie = baldes.entrySet().stream()
                .map(e -> new PontoSerie(chave(e.getKey(), agrupamento), rotulo(e.getKey(), agrupamento),
                        e.getValue()[0], e.getValue()[1]))
                .toList();
        Map<String, Long> totais = new LinkedHashMap<>();
        totais.put("abertos", (long) aberturas.size());
        totais.put("resolvidos", (long) resolucoes.size());
        return new SerieResponse(agrupamento, filtros.descricao(), serie, totais);
    }

    static LocalDate inicioDoBalde(LocalDate d, String agrupamento) {
        return switch (agrupamento) {
            case "semana" -> d.minusDays(d.getDayOfWeek().getValue() - 1L); // segunda-feira
            case "mes" -> d.withDayOfMonth(1);
            default -> d;
        };
    }

    private static LocalDate proximo(LocalDate d, String agrupamento) {
        return switch (agrupamento) {
            case "semana" -> d.plusWeeks(1);
            case "mes" -> d.plusMonths(1);
            default -> d.plusDays(1);
        };
    }

    private static String chave(LocalDate d, String agrupamento) {
        return "mes".equals(agrupamento) ? d.toString().substring(0, 7) : d.toString();
    }

    private static String rotulo(LocalDate d, String agrupamento) {
        if ("mes".equals(agrupamento)) {
            return MESES[d.getMonthValue() - 1] + "/" + String.valueOf(d.getYear()).substring(2);
        }
        return String.format("%02d/%02d", d.getDayOfMonth(), d.getMonthValue());
    }

    // ===================== RF03 — Situação dos chamados =====================

    public SituacaoResponse situacao(Filtros filtros) {
        LocalDateTime agora = agora();
        List<Chamado> lista = chamados.buscar(filtros, CampoData.ABERTURA);
        long total = lista.size();

        List<ItemStatus> porStatus = new ArrayList<>();
        for (Status s : Status.values()) {
            long q = lista.stream().filter(c -> c.getStatus() == s).count();
            porStatus.add(new ItemStatus(s.codigo(), s.rotulo(), q, percentual(q, total)));
        }

        List<Chamado> pendentes = lista.stream().filter(c -> c.getStatus().pendente()).toList();
        List<ItemPrioridade> porPrioridade = new ArrayList<>();
        for (Prioridade p : Prioridade.values()) {
            List<Chamado> daPrioridade = pendentes.stream().filter(c -> c.getPrioridade() == p).toList();
            porPrioridade.add(new ItemPrioridade(p.codigo(), p.rotulo(), daPrioridade.size(),
                    daPrioridade.stream().filter(c -> c.atrasado(agora)).count()));
        }

        long[] idades = new long[4];
        for (Chamado c : pendentes) {
            long horas = Duration.between(c.getDataAbertura(), agora).toHours();
            idades[horas < 24 ? 0 : horas < 72 ? 1 : horas < 168 ? 2 : 3]++;
        }
        List<ItemIdade> porIdade = List.of(
                new ItemIdade("ate_24h", "Até 24 h", idades[0]),
                new ItemIdade("1_a_3_dias", "1 a 3 dias", idades[1]),
                new ItemIdade("3_a_7_dias", "3 a 7 dias", idades[2]),
                new ItemIdade("mais_de_7_dias", "Mais de 7 dias", idades[3]));

        long atrasados = pendentes.stream().filter(c -> c.atrasado(agora)).count();
        return new SituacaoResponse(filtros.descricao(), total, porStatus, porPrioridade, porIdade,
                atrasados, Datas.iso(agora));
    }

    // ===================== RF04 — Tipos mais atendidos =====================

    public RankingTiposResponse tiposMaisAtendidos(Filtros filtros, int limite) {
        Map<String, List<Chamado>> porCategoria = chamados.buscar(filtros, CampoData.ABERTURA).stream()
                .collect(Collectors.groupingBy(Chamado::getCategoriaId));
        long totalAtendidos = porCategoria.values().stream().flatMap(List::stream).filter(Chamado::foiAtendido).count();

        List<ItemTipo> itens = porCategoria.values().stream()
                .map(l -> {
                    long atendidos = l.stream().filter(Chamado::foiAtendido).count();
                    long resolvidos = contar(l, c -> c.getStatus() == Status.RESOLVIDO);
                    return new ItemTipo(0, l.get(0).getCategoriaId(), l.get(0).getCategoriaNome(), l.size(),
                            atendidos, resolvidos, percentual(atendidos, totalAtendidos),
                            media(l, Chamado::horasAteResolucao),
                            percentual(contar(l, Chamado::resolvidoNoPrazo), resolvidos));
                })
                .sorted(Comparator.comparingLong(ItemTipo::atendidos).reversed()
                        .thenComparing(Comparator.comparingLong(ItemTipo::total).reversed())
                        .thenComparing(ItemTipo::categoria))
                .limit(limite)
                .toList();

        List<ItemTipo> ranking = new ArrayList<>();
        for (int i = 0; i < itens.size(); i++) {
            ItemTipo t = itens.get(i);
            ranking.add(new ItemTipo(i + 1, t.categoriaId(), t.categoria(), t.total(), t.atendidos(),
                    t.resolvidos(), t.percentual(), t.tempoMedioResolucaoHoras(), t.slaCumpridoPercentual()));
        }
        return new RankingTiposResponse(filtros.descricao(), limite, ranking);
    }

    // ===================== Apoio à decisão — desempenho por técnico =====================

    public DesempenhoResponse desempenhoTecnicos(Filtros filtros) {
        Map<String, List<Chamado>> porTecnico = chamados.buscar(filtros, CampoData.ABERTURA).stream()
                .filter(c -> c.getTecnicoId() != null)
                .collect(Collectors.groupingBy(Chamado::getTecnicoId));
        Map<String, Tecnico> cadastro = catalogo.tecnicos().stream()
                .collect(Collectors.toMap(Tecnico::getId, Function.identity()));
        List<ItemTecnico> itens = porTecnico.values().stream()
                .map(l -> {
                    long resolvidos = contar(l, c -> c.getStatus() == Status.RESOLVIDO);
                    Tecnico t = cadastro.get(l.get(0).getTecnicoId());
                    return new ItemTecnico(l.get(0).getTecnicoId(),
                            t != null ? t.getNome() : l.get(0).getTecnicoNome(), t != null ? t.getNivel() : null,
                            l.size(), resolvidos, contar(l, c -> c.getStatus().pendente()),
                            media(l, Chamado::horasAteResolucao),
                            percentual(contar(l, Chamado::resolvidoNoPrazo), resolvidos),
                            media(l, c -> c.getAvaliacao() == null ? null : c.getAvaliacao().doubleValue(), 2));
                })
                .sorted(Comparator.comparingLong(ItemTecnico::resolvidos).reversed().thenComparing(ItemTecnico::nome))
                .toList();
        return new DesempenhoResponse(filtros.descricao(), itens);
    }

    // ===================== utilitários =====================

    private static long contar(List<Chamado> l, Predicate<Chamado> p) {
        return l.stream().filter(p).count();
    }

    static Double media(List<Chamado> l, Function<Chamado, Double> f) {
        return media(l, f, 1);
    }

    static Double media(List<Chamado> l, Function<Chamado, Double> f, int casas) {
        ToDoubleFunction<Double> unbox = Double::doubleValue;
        OptionalDouble m = l.stream().map(f).filter(Objects::nonNull).mapToDouble(unbox).average();
        return m.isPresent() ? arred(m.getAsDouble(), casas) : null;
    }

    static double percentual(long parte, long todo) {
        return todo <= 0 ? 0.0 : arred(parte * 100.0 / todo, 1);
    }

    static Double variacao(Number atual, Number anterior) {
        if (atual == null || anterior == null || anterior.doubleValue() == 0) return null;
        return arred((atual.doubleValue() - anterior.doubleValue()) * 100 / anterior.doubleValue(), 1);
    }

    static double arred(double v, int casas) {
        double f = Math.pow(10, casas);
        return Math.round(v * f) / f;
    }
}
