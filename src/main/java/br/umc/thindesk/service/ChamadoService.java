package br.umc.thindesk.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import br.umc.thindesk.domain.Categoria;
import br.umc.thindesk.domain.Chamado;
import br.umc.thindesk.domain.ConsideracaoTecnica;
import br.umc.thindesk.domain.HistoricoStatus;
import br.umc.thindesk.domain.Prioridade;
import br.umc.thindesk.domain.Status;
import br.umc.thindesk.domain.Tecnico;
import br.umc.thindesk.domain.TipoConsideracao;
import br.umc.thindesk.exception.DetalheErro;
import br.umc.thindesk.exception.NaoEncontradoException;
import br.umc.thindesk.exception.RegraNegocioException;
import br.umc.thindesk.exception.ValidacaoException;
import br.umc.thindesk.repository.CatalogoGateway;
import br.umc.thindesk.repository.ChamadoGateway;
import br.umc.thindesk.repository.ConsultaChamados;
import br.umc.thindesk.web.dto.ChamadoDtos.AlterarStatusRequest;
import br.umc.thindesk.web.dto.ChamadoDtos.ChamadoDetalhe;
import br.umc.thindesk.web.dto.ChamadoDtos.ChamadoResumo;
import br.umc.thindesk.web.dto.ChamadoDtos.NovaConsideracaoRequest;
import br.umc.thindesk.web.dto.ChamadoDtos.NovoChamadoRequest;
import br.umc.thindesk.web.dto.ChamadoDtos.PaginaResponse;
import br.umc.thindesk.web.dto.ChamadoDtos.Paginacao;

/** Consulta e atualização de chamados (RF05 listagem filtrada, RF07 atualização de dados). */
@Service
public class ChamadoService {

    private final ChamadoGateway chamados;
    private final CatalogoGateway catalogo;
    private final Clock clock;

    public ChamadoService(ChamadoGateway chamados, CatalogoGateway catalogo, Clock clock) {
        this.chamados = chamados;
        this.catalogo = catalogo;
        this.clock = clock;
    }

    private LocalDateTime agora() {
        return LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
    }

    // ---------------- consultas ----------------

    public PaginaResponse<ChamadoResumo> listar(Map<String, String> params) {
        Filtros filtros = FiltrosParser.parse(params);
        int pagina = FiltrosParser.inteiro(params, "pagina", 1, 1, 100_000);
        int porPagina = FiltrosParser.inteiro(params, "porPagina", 20, 1, 100);
        String ordenarPor = params.getOrDefault("ordenarPor", "dataAbertura");
        if (!ConsultaChamados.CAMPOS_ORDENACAO.contains(ordenarPor)) {
            throw new ValidacaoException("ordenarPor", "Use um destes valores: "
                    + String.join(", ", ConsultaChamados.CAMPOS_ORDENACAO) + ".");
        }
        String ordem = params.getOrDefault("ordem", "desc");
        if (!ordem.equals("asc") && !ordem.equals("desc")) {
            throw new ValidacaoException("ordem", "Use 'asc' ou 'desc'.");
        }
        boolean atrasados = "true".equalsIgnoreCase(params.get("atrasados"));
        LocalDateTime agora = agora();

        var pagina0 = chamados.listar(new ConsultaChamados(filtros, params.get("busca"), atrasados, agora,
                pagina, porPagina, ordenarPor, ordem.equals("asc")));
        var dados = pagina0.map(c -> ChamadoResumo.de(c, agora));
        return new PaginaResponse<>(dados.itens(),
                new Paginacao(dados.pagina(), dados.porPagina(), dados.total(), dados.totalPaginas()));
    }

    public ChamadoDetalhe detalhar(String id) {
        return ChamadoDetalhe.de(buscar(id), agora());
    }

    private Chamado buscar(String id) {
        return chamados.porId(id).orElseThrow(() -> new NaoEncontradoException("Chamado '" + id + "' não encontrado."));
    }

    // ---------------- comandos ----------------

    public ChamadoDetalhe criar(NovoChamadoRequest req) {
        if (req == null) throw new ValidacaoException("corpo", "Corpo da requisição é obrigatório.");
        List<DetalheErro> erros = new ArrayList<>();
        obrigatorio(req.titulo(), "titulo", 120, erros);
        obrigatorio(req.descricao(), "descricao", 2000, erros);
        obrigatorio(req.solicitante(), "solicitante", 120, erros);
        obrigatorio(req.setor(), "setor", 60, erros);
        Prioridade prioridade = null;
        if (req.prioridade() == null) {
            erros.add(new DetalheErro("prioridade", "Campo obrigatório."));
        } else {
            prioridade = Prioridade.deCodigo(req.prioridade()).orElse(null);
            if (prioridade == null) erros.add(new DetalheErro("prioridade", "Use: baixa, media, alta ou critica."));
        }
        Categoria categoria = null;
        if (req.categoriaId() == null || req.categoriaId().isBlank()) {
            erros.add(new DetalheErro("categoriaId", "Campo obrigatório."));
        } else {
            categoria = catalogo.categoria(req.categoriaId()).orElse(null);
            if (categoria == null) erros.add(new DetalheErro("categoriaId", "Categoria inexistente."));
        }
        if (!erros.isEmpty()) throw new ValidacaoException(erros);

        LocalDateTime agora = agora();
        Chamado c = new Chamado();
        c.setNumero(chamados.proximoNumero());
        c.setTitulo(req.titulo().trim());
        c.setDescricao(req.descricao().trim());
        c.setSolicitante(req.solicitante().trim());
        c.setSetor(req.setor().trim());
        c.setCategoriaId(categoria.getId());
        c.setCategoriaNome(categoria.getNome());
        c.setPrioridade(prioridade);
        c.setStatus(Status.ABERTO);
        c.setDataAbertura(agora);
        c.setPrazoSla(agora.plusHours(prioridade.slaHoras()));
        c.getHistorico().add(new HistoricoStatus(null, Status.ABERTO, agora, "Chamado aberto"));
        return ChamadoDetalhe.de(chamados.salvar(c), agora);
    }

    /**
     * Regras:
     *  - só são aceitas as transições da máquina de estados ({@link Status#transicoesPermitidas()});
     *  - "em_atendimento" exige um técnico (o já atribuído ou o informado);
     *  - "resolvido" exige a descrição da solução (vira uma consideração técnica do tipo "solucao");
     *  - "cancelado" exige o motivo.
     */
    public ChamadoDetalhe alterarStatus(String id, AlterarStatusRequest req) {
        if (req == null || req.status() == null) throw new ValidacaoException("status", "Campo obrigatório.");
        Status destino = Status.deCodigo(req.status())
                .orElseThrow(() -> new ValidacaoException("status",
                        "Use: aberto, em_atendimento, aguardando, resolvido ou cancelado."));
        Chamado c = buscar(id);
        Status origem = c.getStatus();
        if (!origem.podeIrPara(destino)) {
            throw new RegraNegocioException("TRANSICAO_INVALIDA", "Não é permitido mudar de '"
                    + origem.codigo() + "' para '" + destino.codigo() + "'. Permitido: "
                    + String.join(", ", origem.transicoesPermitidas().stream().map(Status::codigo).toList()) + ".");
        }
        if (req.tecnicoId() != null && !req.tecnicoId().isBlank()) {
            Tecnico t = catalogo.tecnico(req.tecnicoId())
                    .orElseThrow(() -> new ValidacaoException("tecnicoId", "Técnico inexistente."));
            c.setTecnicoId(t.getId());
            c.setTecnicoNome(t.getNome());
        }
        String obs = req.observacao() == null ? null : req.observacao().trim();
        LocalDateTime agora = agora();

        switch (destino) {
            case EM_ATENDIMENTO -> {
                if (c.getTecnicoId() == null) {
                    throw new ValidacaoException("tecnicoId", "Informe o técnico que vai atender o chamado.");
                }
                if (c.getDataPrimeiroAtendimento() == null) c.setDataPrimeiroAtendimento(agora);
            }
            case RESOLVIDO -> {
                if (obs == null || obs.isEmpty()) {
                    throw new ValidacaoException("observacao", "Descreva a solução aplicada.");
                }
                c.setDataResolucao(agora);
                c.getConsideracoes().add(new ConsideracaoTecnica(TipoConsideracao.SOLUCAO, obs,
                        c.getTecnicoId(), c.getTecnicoNome(), agora));
            }
            case CANCELADO -> {
                if (obs == null || obs.isEmpty()) {
                    throw new ValidacaoException("observacao", "Informe o motivo do cancelamento.");
                }
            }
            case ABERTO -> c.setDataResolucao(null); // reabertura
            default -> { }
        }
        c.setStatus(destino);
        c.getHistorico().add(new HistoricoStatus(origem, destino, agora, obs));
        return ChamadoDetalhe.de(chamados.salvar(c), agora);
    }

    public ChamadoDetalhe adicionarConsideracao(String id, NovaConsideracaoRequest req) {
        if (req == null) throw new ValidacaoException("corpo", "Corpo da requisição é obrigatório.");
        List<DetalheErro> erros = new ArrayList<>();
        TipoConsideracao tipo = req.tipo() == null ? null : TipoConsideracao.deCodigo(req.tipo()).orElse(null);
        if (tipo == null) erros.add(new DetalheErro("tipo", "Use: diagnostico, solucao ou observacao."));
        obrigatorio(req.texto(), "texto", 2000, erros);
        if (!erros.isEmpty()) throw new ValidacaoException(erros);

        Chamado c = buscar(id);
        if (c.getStatus() == Status.CANCELADO) {
            throw new RegraNegocioException("CHAMADO_CANCELADO", "Chamado cancelado não recebe novas considerações.");
        }
        String tecnicoId = req.tecnicoId() != null && !req.tecnicoId().isBlank() ? req.tecnicoId() : c.getTecnicoId();
        String tecnicoNome = null;
        if (tecnicoId != null) {
            tecnicoNome = catalogo.tecnico(tecnicoId)
                    .orElseThrow(() -> new ValidacaoException("tecnicoId", "Técnico inexistente.")).getNome();
        }
        LocalDateTime agora = agora();
        c.getConsideracoes().add(new ConsideracaoTecnica(tipo, req.texto().trim(), tecnicoId, tecnicoNome, agora));
        return ChamadoDetalhe.de(chamados.salvar(c), agora);
    }

    private static void obrigatorio(String valor, String campo, int max, List<DetalheErro> erros) {
        if (valor == null || valor.isBlank()) {
            erros.add(new DetalheErro(campo, "Campo obrigatório."));
        } else if (valor.trim().length() > max) {
            erros.add(new DetalheErro(campo, "Máximo de " + max + " caracteres."));
        }
    }
}
