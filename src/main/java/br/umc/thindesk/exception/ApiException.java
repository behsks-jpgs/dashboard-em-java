package br.umc.thindesk.exception;

import java.util.List;

/** Exceção base: carrega o status HTTP, o código de erro e os detalhes para o corpo da resposta. */
public class ApiException extends RuntimeException {
    private final int status;
    private final String codigo;
    private final List<DetalheErro> detalhes;

    public ApiException(int status, String codigo, String mensagem, List<DetalheErro> detalhes) {
        super(mensagem);
        this.status = status;
        this.codigo = codigo;
        this.detalhes = detalhes == null ? List.of() : List.copyOf(detalhes);
    }

    public int getStatus() { return status; }
    public String getCodigo() { return codigo; }
    public List<DetalheErro> getDetalhes() { return detalhes; }
}
