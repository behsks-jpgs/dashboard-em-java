package br.umc.thindesk.domain;

import java.util.Arrays;
import java.util.Optional;

public enum TipoConsideracao {
    DIAGNOSTICO("diagnostico", "Diagnóstico"),
    SOLUCAO("solucao", "Solução"),
    OBSERVACAO("observacao", "Observação");

    private final String codigo;
    private final String rotulo;

    TipoConsideracao(String codigo, String rotulo) {
        this.codigo = codigo;
        this.rotulo = rotulo;
    }

    public String codigo() { return codigo; }

    public String rotulo() { return rotulo; }

    public static Optional<TipoConsideracao> deCodigo(String codigo) {
        return Arrays.stream(values()).filter(t -> t.codigo.equals(codigo)).findFirst();
    }
}
