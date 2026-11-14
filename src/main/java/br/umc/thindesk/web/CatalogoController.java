package br.umc.thindesk.web;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.umc.thindesk.domain.Categoria;
import br.umc.thindesk.domain.Prioridade;
import br.umc.thindesk.domain.Status;
import br.umc.thindesk.domain.Tecnico;
import br.umc.thindesk.repository.CatalogoGateway;
import br.umc.thindesk.repository.ChamadoGateway;
import br.umc.thindesk.web.dto.Datas;

/** Dados de apoio: opções dos filtros, catálogos e verificação de saúde. */
@RestController
@RequestMapping("/api/v1")
public class CatalogoController {

    public record Opcao(String valor, String rotulo) { }

    public record FiltrosDisponiveis(List<Opcao> status, List<Opcao> prioridades, List<Opcao> categorias,
                                     List<Opcao> tecnicos, List<String> agrupamentos) { }

    private final CatalogoGateway catalogo;
    private final ChamadoGateway chamados;
    private final Clock clock;

    public CatalogoController(CatalogoGateway catalogo, ChamadoGateway chamados, Clock clock) {
        this.catalogo = catalogo;
        this.chamados = chamados;
        this.clock = clock;
    }

    /** Opções para montar os filtros do dashboard (RF05). */
    @GetMapping("/filtros")
    public FiltrosDisponiveis filtros() {
        return new FiltrosDisponiveis(
                Arrays.stream(Status.values()).map(s -> new Opcao(s.codigo(), s.rotulo())).toList(),
                Arrays.stream(Prioridade.values()).map(p -> new Opcao(p.codigo(), p.rotulo())).toList(),
                catalogo.categorias().stream().map(c -> new Opcao(c.getId(), c.getNome())).toList(),
                catalogo.tecnicos().stream().map(t -> new Opcao(t.getId(), t.getNome())).toList(),
                List.of("dia", "semana", "mes"));
    }

    @GetMapping("/categorias")
    public List<Categoria> categorias() {
        return catalogo.categorias();
    }

    @GetMapping("/tecnicos")
    public List<Tecnico> tecnicos() {
        return catalogo.tecnicos();
    }

    /** Health check: confirma que a API está no ar e que o banco responde. */
    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("status", "UP");
        try {
            r.put("banco", "UP");
            r.put("totalChamados", chamados.contar());
        } catch (RuntimeException e) {
            r.put("status", "DEGRADED");
            r.put("banco", "DOWN");
        }
        r.put("horario", Datas.iso(LocalDateTime.now(clock).withNano(0)));
        return r;
    }
}
