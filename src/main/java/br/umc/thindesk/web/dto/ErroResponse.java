package br.umc.thindesk.web.dto;

import java.util.List;

import br.umc.thindesk.exception.DetalheErro;

/** Formato padrão de erro: {"erro": {"codigo", "mensagem", "detalhes": [...]}} */
public record ErroResponse(Erro erro) {
    public record Erro(String codigo, String mensagem, List<DetalheErro> detalhes) { }

    public static ErroResponse de(String codigo, String mensagem, List<DetalheErro> detalhes) {
        return new ErroResponse(new Erro(codigo, mensagem, detalhes == null ? List.of() : detalhes));
    }
}
