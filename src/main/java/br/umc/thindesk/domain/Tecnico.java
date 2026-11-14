package br.umc.thindesk.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/** Técnico responsável pelo atendimento. Coleção "tecnicos". */
@Document("tecnicos")
public class Tecnico {
    @Id
    private String id;
    private String nome;
    private String email;
    private String nivel; // N1, N2, N3
    private boolean ativo = true;

    public Tecnico() { }

    public Tecnico(String id, String nome, String email, String nivel) {
        this.id = id;
        this.nome = nome;
        this.email = email;
        this.nivel = nivel;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getNivel() { return nivel; }
    public void setNivel(String nivel) { this.nivel = nivel; }
    public boolean isAtivo() { return ativo; }
    public void setAtivo(boolean ativo) { this.ativo = ativo; }
}
