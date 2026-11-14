package br.umc.thindesk.domain;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

/** Situação do chamado e máquina de estados (transições permitidas). */
public enum Status {
    ABERTO("aberto", "Aberto"),
    EM_ATENDIMENTO("em_atendimento", "Em atendimento"),
    AGUARDANDO("aguardando", "Aguardando usuário"),
    RESOLVIDO("resolvido", "Resolvido"),
    CANCELADO("cancelado", "Cancelado");

    private final String codigo;
    private final String rotulo;

    Status(String codigo, String rotulo) {
        this.codigo = codigo;
        this.rotulo = rotulo;
    }

    public String codigo() { return codigo; }

    public String rotulo() { return rotulo; }

    /** Pendente = ainda exige ação da equipe de suporte. */
    public boolean pendente() {
        return this == ABERTO || this == EM_ATENDIMENTO || this == AGUARDANDO;
    }

    public Set<Status> transicoesPermitidas() {
        return switch (this) {
            case ABERTO -> EnumSet.of(EM_ATENDIMENTO, CANCELADO);
            case EM_ATENDIMENTO -> EnumSet.of(AGUARDANDO, RESOLVIDO, CANCELADO);
            case AGUARDANDO -> EnumSet.of(EM_ATENDIMENTO, RESOLVIDO, CANCELADO);
            case RESOLVIDO -> EnumSet.of(ABERTO); // reabertura
            case CANCELADO -> EnumSet.noneOf(Status.class);
        };
    }

    public boolean podeIrPara(Status destino) {
        return transicoesPermitidas().contains(destino);
    }

    public static Optional<Status> deCodigo(String codigo) {
        return Arrays.stream(values()).filter(s -> s.codigo.equals(codigo)).findFirst();
    }
}
