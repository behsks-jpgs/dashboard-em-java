package br.umc.thindesk.exception;

/** HTTP 404 — recurso inexistente. */
public class NaoEncontradoException extends ApiException {
    public NaoEncontradoException(String mensagem) {
        super(404, "NAO_ENCONTRADO", mensagem, null);
    }
}
