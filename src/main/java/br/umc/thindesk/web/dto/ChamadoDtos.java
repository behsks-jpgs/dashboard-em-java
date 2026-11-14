package br.umc.thindesk.web.dto;

import java.time.LocalDateTime;
import java.util.List;

import br.umc.thindesk.domain.Chamado;
import br.umc.thindesk.domain.ConsideracaoTecnica;
import br.umc.thindesk.domain.HistoricoStatus;
import br.umc.thindesk.domain.Status;

/** Requisições e respostas de /api/v1/chamados/** */
public final class ChamadoDtos {
    private ChamadoDtos() { }

    // ---------- requisições ----------

    public record NovoChamadoRequest(String titulo, String descricao, String solicitante, String setor,
                                     String categoriaId, String prioridade) { }

    public record AlterarStatusRequest(String status, String tecnicoId, String observacao) { }

    public record NovaConsideracaoRequest(String tipo, String texto, String tecnicoId) { }

    // ---------- respostas ----------

    public record Paginacao(int pagina, int porPagina, long total, int totalPaginas) { }

    public record PaginaResponse<T>(List<T> dados, Paginacao paginacao) { }

    public record ChamadoResumo(String id, long numero, String titulo, String solicitante, String setor,
                                String categoriaId, String categoria, String tecnicoId, String tecnico,
                                String prioridade, String prioridadeRotulo, String status, String statusRotulo,
                                String dataAbertura, String prazoSla, String dataResolucao, boolean atrasado) {

        public static ChamadoResumo de(Chamado c, LocalDateTime agora) {
            return new ChamadoResumo(c.getId(), c.getNumero(), c.getTitulo(), c.getSolicitante(), c.getSetor(),
                    c.getCategoriaId(), c.getCategoriaNome(), c.getTecnicoId(), c.getTecnicoNome(),
                    c.getPrioridade().codigo(), c.getPrioridade().rotulo(),
                    c.getStatus().codigo(), c.getStatus().rotulo(),
                    Datas.iso(c.getDataAbertura()), Datas.iso(c.getPrazoSla()), Datas.iso(c.getDataResolucao()),
                    c.atrasado(agora));
        }
    }

    public record ConsideracaoDto(String tipo, String tipoRotulo, String texto, String tecnicoId,
                                  String tecnico, String criadoEm) {
        public static ConsideracaoDto de(ConsideracaoTecnica c) {
            return new ConsideracaoDto(c.getTipo().codigo(), c.getTipo().rotulo(), c.getTexto(),
                    c.getTecnicoId(), c.getTecnicoNome(), Datas.iso(c.getCriadoEm()));
        }
    }

    public record HistoricoDto(String de, String para, String alteradoEm, String observacao) {
        public static HistoricoDto de(HistoricoStatus h) {
            return new HistoricoDto(h.getDe() == null ? null : h.getDe().codigo(), h.getPara().codigo(),
                    Datas.iso(h.getAlteradoEm()), h.getObservacao());
        }
    }

    public record ChamadoDetalhe(ChamadoResumo resumo, String descricao, String dataPrimeiroAtendimento,
                                 Double minutosAtePrimeiroAtendimento, Double horasAteResolucao,
                                 Integer avaliacao, List<String> transicoesPermitidas,
                                 List<ConsideracaoDto> consideracoes, List<HistoricoDto> historico) {

        public static ChamadoDetalhe de(Chamado c, LocalDateTime agora) {
            return new ChamadoDetalhe(ChamadoResumo.de(c, agora), c.getDescricao(),
                    Datas.iso(c.getDataPrimeiroAtendimento()),
                    arred(c.minutosAtePrimeiroAtendimento()), arred(c.horasAteResolucao()), c.getAvaliacao(),
                    c.getStatus().transicoesPermitidas().stream().sorted().map(Status::codigo).toList(),
                    c.getConsideracoes().stream().map(ConsideracaoDto::de).toList(),
                    c.getHistorico().stream().map(HistoricoDto::de).toList());
        }

        private static Double arred(Double v) {
            return v == null ? null : Math.round(v * 10) / 10.0;
        }
    }
}
