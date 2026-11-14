package br.umc.thindesk.domain;

import java.time.LocalDateTime;

/** Consideração técnica registrada no atendimento (documento embutido no chamado). */
public class ConsideracaoTecnica {
    private TipoConsideracao tipo;
    private String texto;
    private String tecnicoId;
    private String tecnicoNome;
    private LocalDateTime criadoEm;

    public ConsideracaoTecnica() { }

    public ConsideracaoTecnica(TipoConsideracao tipo, String texto, String tecnicoId, String tecnicoNome,
                               LocalDateTime criadoEm) {
        this.tipo = tipo;
        this.texto = texto;
        this.tecnicoId = tecnicoId;
        this.tecnicoNome = tecnicoNome;
        this.criadoEm = criadoEm;
    }

    public TipoConsideracao getTipo() { return tipo; }
    public void setTipo(TipoConsideracao tipo) { this.tipo = tipo; }
    public String getTexto() { return texto; }
    public void setTexto(String texto) { this.texto = texto; }
    public String getTecnicoId() { return tecnicoId; }
    public void setTecnicoId(String tecnicoId) { this.tecnicoId = tecnicoId; }
    public String getTecnicoNome() { return tecnicoNome; }
    public void setTecnicoNome(String tecnicoNome) { this.tecnicoNome = tecnicoNome; }
    public LocalDateTime getCriadoEm() { return criadoEm; }
    public void setCriadoEm(LocalDateTime criadoEm) { this.criadoEm = criadoEm; }
}
