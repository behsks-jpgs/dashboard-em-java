package br.umc.thindesk.domain;

import java.time.LocalDateTime;

/** Registro de auditoria de cada mudança de status (documento embutido no chamado). */
public class HistoricoStatus {
    private Status de;
    private Status para;
    private LocalDateTime alteradoEm;
    private String observacao;

    public HistoricoStatus() { }

    public HistoricoStatus(Status de, Status para, LocalDateTime alteradoEm, String observacao) {
        this.de = de;
        this.para = para;
        this.alteradoEm = alteradoEm;
        this.observacao = observacao;
    }

    public Status getDe() { return de; }
    public void setDe(Status de) { this.de = de; }
    public Status getPara() { return para; }
    public void setPara(Status para) { this.para = para; }
    public LocalDateTime getAlteradoEm() { return alteradoEm; }
    public void setAlteradoEm(LocalDateTime alteradoEm) { this.alteradoEm = alteradoEm; }
    public String getObservacao() { return observacao; }
    public void setObservacao(String observacao) { this.observacao = observacao; }
}
