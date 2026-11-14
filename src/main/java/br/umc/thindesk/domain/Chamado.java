package br.umc.thindesk.domain;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Chamado técnico. Coleção "chamados".
 *
 * Decisão de modelagem (MongoDB): considerações técnicas e histórico de status são
 * EMBUTIDOS no documento do chamado (relação 1:poucos, sempre lidos juntos). Categoria e
 * técnico são REFERENCIADOS por id, com o nome copiado (desnormalizado) para que o
 * dashboard monte rankings sem precisar de joins ($lookup).
 */
@Document("chamados")
public class Chamado {
    @Id
    private String id;
    @Indexed(unique = true)
    private long numero;
    private String titulo;
    private String descricao;
    private String solicitante;
    private String setor;
    @Indexed
    private String categoriaId;
    private String categoriaNome;
    @Indexed
    private String tecnicoId;
    private String tecnicoNome;
    private Prioridade prioridade;
    @Indexed
    private Status status = Status.ABERTO;
    @Indexed
    private LocalDateTime dataAbertura;
    private LocalDateTime dataPrimeiroAtendimento;
    @Indexed
    private LocalDateTime dataResolucao;
    private LocalDateTime prazoSla;
    private Integer avaliacao;
    private List<ConsideracaoTecnica> consideracoes = new ArrayList<>();
    private List<HistoricoStatus> historico = new ArrayList<>();

    // ---------- regras de negócio derivadas ----------

    /** Pendente e com o prazo de SLA vencido no momento informado. */
    public boolean atrasado(LocalDateTime agora) {
        return status.pendente() && prazoSla != null && prazoSla.isBefore(agora);
    }

    public boolean resolvidoNoPrazo() {
        return status == Status.RESOLVIDO && dataResolucao != null && !dataResolucao.isAfter(prazoSla);
    }

    public Double horasAteResolucao() {
        if (status != Status.RESOLVIDO || dataResolucao == null) return null;
        return Duration.between(dataAbertura, dataResolucao).toSeconds() / 3600.0;
    }

    public Double minutosAtePrimeiroAtendimento() {
        if (dataPrimeiroAtendimento == null) return null;
        return Duration.between(dataAbertura, dataPrimeiroAtendimento).toSeconds() / 60.0;
    }

    public boolean foiAtendido() {
        return dataPrimeiroAtendimento != null;
    }

    // ---------- getters / setters ----------

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public long getNumero() { return numero; }
    public void setNumero(long numero) { this.numero = numero; }
    public String getTitulo() { return titulo; }
    public void setTitulo(String titulo) { this.titulo = titulo; }
    public String getDescricao() { return descricao; }
    public void setDescricao(String descricao) { this.descricao = descricao; }
    public String getSolicitante() { return solicitante; }
    public void setSolicitante(String solicitante) { this.solicitante = solicitante; }
    public String getSetor() { return setor; }
    public void setSetor(String setor) { this.setor = setor; }
    public String getCategoriaId() { return categoriaId; }
    public void setCategoriaId(String categoriaId) { this.categoriaId = categoriaId; }
    public String getCategoriaNome() { return categoriaNome; }
    public void setCategoriaNome(String categoriaNome) { this.categoriaNome = categoriaNome; }
    public String getTecnicoId() { return tecnicoId; }
    public void setTecnicoId(String tecnicoId) { this.tecnicoId = tecnicoId; }
    public String getTecnicoNome() { return tecnicoNome; }
    public void setTecnicoNome(String tecnicoNome) { this.tecnicoNome = tecnicoNome; }
    public Prioridade getPrioridade() { return prioridade; }
    public void setPrioridade(Prioridade prioridade) { this.prioridade = prioridade; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public LocalDateTime getDataAbertura() { return dataAbertura; }
    public void setDataAbertura(LocalDateTime dataAbertura) { this.dataAbertura = dataAbertura; }
    public LocalDateTime getDataPrimeiroAtendimento() { return dataPrimeiroAtendimento; }
    public void setDataPrimeiroAtendimento(LocalDateTime d) { this.dataPrimeiroAtendimento = d; }
    public LocalDateTime getDataResolucao() { return dataResolucao; }
    public void setDataResolucao(LocalDateTime dataResolucao) { this.dataResolucao = dataResolucao; }
    public LocalDateTime getPrazoSla() { return prazoSla; }
    public void setPrazoSla(LocalDateTime prazoSla) { this.prazoSla = prazoSla; }
    public Integer getAvaliacao() { return avaliacao; }
    public void setAvaliacao(Integer avaliacao) { this.avaliacao = avaliacao; }
    public List<ConsideracaoTecnica> getConsideracoes() { return consideracoes; }
    public void setConsideracoes(List<ConsideracaoTecnica> c) { this.consideracoes = c; }
    public List<HistoricoStatus> getHistorico() { return historico; }
    public void setHistorico(List<HistoricoStatus> historico) { this.historico = historico; }
}
