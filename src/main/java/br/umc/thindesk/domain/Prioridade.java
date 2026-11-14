package br.umc.thindesk.domain;

import java.util.Arrays;
import java.util.Optional;

/** Prioridade do chamado e o prazo de SLA (em horas) para resolução. */
public enum Prioridade {
    BAIXA("baixa", "Baixa", 48),
    MEDIA("media", "Média", 24),
    ALTA("alta", "Alta", 8),
    CRITICA("critica", "Crítica", 4);

    private final String codigo;
    private final String rotulo;
    private final int slaHoras;

    Prioridade(String codigo, String rotulo, int slaHoras) {
        this.codigo = codigo;
        this.rotulo = rotulo;
        this.slaHoras = slaHoras;
    }

    public String codigo() { return codigo; }

    public String rotulo() { return rotulo; }

    public int slaHoras() { return slaHoras; }

    public static Optional<Prioridade> deCodigo(String codigo) {
        return Arrays.stream(values()).filter(p -> p.codigo.equals(codigo)).findFirst();
    }
}
