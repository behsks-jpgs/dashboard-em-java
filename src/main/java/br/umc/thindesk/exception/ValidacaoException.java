package br.umc.thindesk.exception;

import java.util.List;

/** HTTP 400 — parâmetros ou corpo inválidos. */
public class ValidacaoException extends ApiException {
    public ValidacaoException(List<DetalheErro> detalhes) {
        super(400, "VALIDACAO", "Parâmetros inválidos.", detalhes);
    }

    public ValidacaoException(String campo, String problema) {
        this(List.of(new DetalheErro(campo, problema)));
    }
}
