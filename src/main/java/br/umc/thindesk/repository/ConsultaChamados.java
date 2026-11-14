package br.umc.thindesk.repository;

import java.time.LocalDateTime;
import java.util.List;

import br.umc.thindesk.service.Filtros;

/** Parâmetros da listagem paginada de chamados. */
public record ConsultaChamados(Filtros filtros,
                               String busca,
                               boolean somenteAtrasados,
                               LocalDateTime agora,
                               int pagina,
                               int porPagina,
                               String ordenarPor,
                               boolean ascendente) {

    /** Campos aceitos no parâmetro ordenarPor. */
    public static final List<String> CAMPOS_ORDENACAO =
            List.of("dataAbertura", "numero", "prazoSla");
}
