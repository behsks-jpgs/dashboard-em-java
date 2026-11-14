package br.umc.thindesk.support;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import br.umc.thindesk.domain.Categoria;
import br.umc.thindesk.domain.Chamado;
import br.umc.thindesk.domain.Prioridade;
import br.umc.thindesk.domain.Status;
import br.umc.thindesk.domain.Tecnico;

/**
 * Massa de dados pequena e conhecida, com resultados calculados à mão, para os testes.
 * "Agora" = 14/11/2026 12:00 (America/Sao_Paulo).
 *
 * | #  | categoria   | prioridade | status         | abertura     | 1º atend. | resolução | técnico |
 * |----|-------------|------------|----------------|--------------|-----------|-----------|---------|
 * | 1  | Hardware    | alta (8h)  | resolvido      | 10/11 09:00  | +30 min   | +6 h  ✔SLA| Ana     |
 * | 2  | Hardware    | media(24h) | resolvido      | 11/11 10:00  | +60 min   | +30 h ✘SLA| Bruno   |
 * | 3  | Rede        | critica(4h)| em_atendimento | 14/11 06:00  | +10 min   | atrasado  | Ana     |
 * | 4  | Rede        | baixa(48h) | aberto         | 14/11 10:00  | -         | no prazo  | -       |
 * | 5  | Software    | media(24h) | aguardando     | 12/11 08:00  | +20 min   | atrasado  | Bruno   |
 * | 6  | Software    | baixa      | cancelado      | 13/11 09:00  | -         | -         | -       |
 * | 7  | Impressoras | media      | resolvido      | 06/11 09:00  | +40 min   | +4 h  ✔SLA| Ana     |  (período anterior)
 */
public final class Fixtures {

    public static final ZoneId ZONA = ZoneId.of("America/Sao_Paulo");
    public static final LocalDateTime AGORA = LocalDateTime.of(2026, 11, 14, 12, 0);

    private Fixtures() { }

    public static Clock clock() {
        return Clock.fixed(AGORA.atZone(ZONA).toInstant(), ZONA);
    }

    public static List<Categoria> categorias() {
        return List.of(new Categoria("cat-hw", "Hardware", null), new Categoria("cat-rede", "Rede", null),
                new Categoria("cat-sw", "Software", null), new Categoria("cat-imp", "Impressoras", null));
    }

    public static List<Tecnico> tecnicos() {
        return List.of(new Tecnico("tec-ana", "Ana", "ana@x", "N1"), new Tecnico("tec-bruno", "Bruno", "bruno@x", "N2"));
    }

    public static List<Chamado> chamados() {
        List<Chamado> l = new ArrayList<>();
        l.add(chamado(1, "cat-hw", "Hardware", Prioridade.ALTA, Status.RESOLVIDO, at(10, 9), 30, 6.0, "tec-ana", "Ana", 5));
        l.add(chamado(2, "cat-hw", "Hardware", Prioridade.MEDIA, Status.RESOLVIDO, at(11, 10), 60, 30.0, "tec-bruno", "Bruno", 3));
        l.add(chamado(3, "cat-rede", "Rede", Prioridade.CRITICA, Status.EM_ATENDIMENTO, at(14, 6), 10, null, "tec-ana", "Ana", null));
        l.add(chamado(4, "cat-rede", "Rede", Prioridade.BAIXA, Status.ABERTO, at(14, 10), null, null, null, null, null));
        l.add(chamado(5, "cat-sw", "Software", Prioridade.MEDIA, Status.AGUARDANDO, at(12, 8), 20, null, "tec-bruno", "Bruno", null));
        l.add(chamado(6, "cat-sw", "Software", Prioridade.BAIXA, Status.CANCELADO, at(13, 9), null, null, null, null, null));
        l.add(chamado(7, "cat-imp", "Impressoras", Prioridade.MEDIA, Status.RESOLVIDO, at(6, 9), 40, 4.0, "tec-ana", "Ana", null));
        return l;
    }

    public static InMemoryChamadoGateway gatewayChamados() {
        return new InMemoryChamadoGateway(chamados());
    }

    public static InMemoryCatalogoGateway gatewayCatalogo() {
        return new InMemoryCatalogoGateway(categorias(), tecnicos());
    }

    private static LocalDateTime at(int dia, int hora) {
        return LocalDateTime.of(2026, 11, dia, hora, 0);
    }

    private static Chamado chamado(long numero, String catId, String catNome, Prioridade p, Status s,
                                   LocalDateTime abertura, Integer minPrimeiro, Double horasResolucao,
                                   String tecId, String tecNome, Integer avaliacao) {
        Chamado c = new Chamado();
        c.setId("ch-" + numero);
        c.setNumero(numero);
        c.setTitulo("Chamado " + numero);
        c.setDescricao("Descrição " + numero);
        c.setSolicitante(numero % 2 == 0 ? "Mariana" : "João");
        c.setSetor("Financeiro");
        c.setCategoriaId(catId);
        c.setCategoriaNome(catNome);
        c.setPrioridade(p);
        c.setStatus(s);
        c.setDataAbertura(abertura);
        c.setPrazoSla(abertura.plusHours(p.slaHoras()));
        c.setTecnicoId(tecId);
        c.setTecnicoNome(tecNome);
        if (minPrimeiro != null) c.setDataPrimeiroAtendimento(abertura.plusMinutes(minPrimeiro));
        if (horasResolucao != null) c.setDataResolucao(abertura.plusMinutes((long) (horasResolucao * 60)));
        c.setAvaliacao(avaliacao);
        return c;
    }
}
