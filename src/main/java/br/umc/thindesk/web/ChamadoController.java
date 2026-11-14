package br.umc.thindesk.web;

import java.net.URI;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.umc.thindesk.service.ChamadoService;
import br.umc.thindesk.web.dto.ChamadoDtos.AlterarStatusRequest;
import br.umc.thindesk.web.dto.ChamadoDtos.ChamadoDetalhe;
import br.umc.thindesk.web.dto.ChamadoDtos.ChamadoResumo;
import br.umc.thindesk.web.dto.ChamadoDtos.NovaConsideracaoRequest;
import br.umc.thindesk.web.dto.ChamadoDtos.NovoChamadoRequest;
import br.umc.thindesk.web.dto.ChamadoDtos.PaginaResponse;

@RestController
@RequestMapping("/api/v1/chamados")
public class ChamadoController {

    private final ChamadoService service;

    public ChamadoController(ChamadoService service) {
        this.service = service;
    }

    @GetMapping
    public PaginaResponse<ChamadoResumo> listar(@RequestParam Map<String, String> params) {
        return service.listar(params);
    }

    @GetMapping("/{id}")
    public ChamadoDetalhe detalhar(@PathVariable String id) {
        return service.detalhar(id);
    }

    @PostMapping
    public ResponseEntity<ChamadoDetalhe> criar(@RequestBody NovoChamadoRequest req) {
        ChamadoDetalhe criado = service.criar(req);
        return ResponseEntity.created(URI.create("/api/v1/chamados/" + criado.resumo().id())).body(criado);
    }

    @PatchMapping("/{id}/status")
    public ChamadoDetalhe alterarStatus(@PathVariable String id, @RequestBody AlterarStatusRequest req) {
        return service.alterarStatus(id, req);
    }

    @PostMapping("/{id}/consideracoes")
    public ResponseEntity<ChamadoDetalhe> adicionarConsideracao(@PathVariable String id,
                                                                @RequestBody NovaConsideracaoRequest req) {
        return ResponseEntity.status(201).body(service.adicionarConsideracao(id, req));
    }
}
