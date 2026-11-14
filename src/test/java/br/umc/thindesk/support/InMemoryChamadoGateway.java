package br.umc.thindesk.support;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import br.umc.thindesk.domain.Chamado;
import br.umc.thindesk.repository.CampoData;
import br.umc.thindesk.repository.ChamadoGateway;
import br.umc.thindesk.repository.ConsultaChamados;
import br.umc.thindesk.repository.Pagina;
import br.umc.thindesk.service.Filtros;

/** Implementação em memória do gateway, usada nos testes (dispensa o MongoDB). */
public class InMemoryChamadoGateway implements ChamadoGateway {

    private final Map<String, Chamado> dados = new LinkedHashMap<>();
    private final AtomicLong sequencia = new AtomicLong();

    public InMemoryChamadoGateway(List<Chamado> iniciais) {
        iniciais.forEach(this::salvar);
        sequencia.set(iniciais.stream().mapToLong(Chamado::getNumero).max().orElse(0));
    }

    @Override
    public List<Chamado> buscar(Filtros f, CampoData campo) {
        return dados.values().stream().filter(c -> noPeriodo(c, f, campo) && f.aceitaDimensoes(c)).toList();
    }

    private static boolean noPeriodo(Chamado c, Filtros f, CampoData campo) {
        LocalDateTime d = campo == CampoData.ABERTURA ? c.getDataAbertura() : c.getDataResolucao();
        if (f.dataInicio() == null && f.dataFim() == null) return true;
        if (d == null) return false;
        if (f.dataInicio() != null && d.isBefore(f.dataInicio().atStartOfDay())) return false;
        return f.dataFim() == null || !d.isAfter(LocalDateTime.of(f.dataFim(), LocalTime.MAX));
    }

    @Override
    public Pagina<Chamado> listar(ConsultaChamados c) {
        List<Chamado> l = new ArrayList<>(buscar(c.filtros(), CampoData.ABERTURA));
        if (c.busca() != null && !c.busca().isBlank()) {
            String b = c.busca().toLowerCase();
            l.removeIf(x -> !(x.getTitulo().toLowerCase().contains(b) || x.getSolicitante().toLowerCase().contains(b)
                    || x.getSetor().toLowerCase().contains(b)));
        }
        if (c.somenteAtrasados()) l.removeIf(x -> !x.atrasado(c.agora()));
        Comparator<Chamado> cmp = switch (c.ordenarPor()) {
            case "numero" -> Comparator.comparingLong(Chamado::getNumero);
            case "prazoSla" -> Comparator.comparing(Chamado::getPrazoSla);
            default -> Comparator.comparing(Chamado::getDataAbertura);
        };
        l.sort(c.ascendente() ? cmp : cmp.reversed());
        int de = Math.min(l.size(), (c.pagina() - 1) * c.porPagina());
        int ate = Math.min(l.size(), de + c.porPagina());
        return new Pagina<>(List.copyOf(l.subList(de, ate)), l.size(), c.pagina(), c.porPagina());
    }

    @Override
    public Optional<Chamado> porId(String id) {
        return Optional.ofNullable(dados.get(id));
    }

    @Override
    public Chamado salvar(Chamado chamado) {
        if (chamado.getId() == null) chamado.setId("ch-" + chamado.getNumero());
        dados.put(chamado.getId(), chamado);
        return chamado;
    }

    @Override
    public long proximoNumero() {
        return sequencia.incrementAndGet();
    }

    @Override
    public long contar() {
        return dados.size();
    }
}
