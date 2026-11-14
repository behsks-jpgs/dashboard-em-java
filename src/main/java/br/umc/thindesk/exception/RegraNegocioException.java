package br.umc.thindesk.exception;

/** HTTP 409 — a operação viola uma regra de negócio (ex.: transição de status inválida). */
public class RegraNegocioException extends ApiException {
    public RegraNegocioException(String codigo, String mensagem) {
        super(409, codigo, mensagem, null);
    }
}
